#!/usr/bin/env python3
"""Read/click only on the explicitly selected Spectra DEMO emulator."""
import os
import re
import signal
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Android/Sdk"))) / "platform-tools/adb"
SERIAL = os.environ.get("SPECTRA_TEST_SERIAL", "emulator-5554")
if not SERIAL.startswith("emulator-"):
    raise SystemExit("This UI test only targets an emulator, never a physical phone.")

def adb(*args):
    return subprocess.check_output([str(ADB), "-s", SERIAL, *args])

def nodes():
    # Live metric text prevents UIAutomator's idle detection. Pause only our
    # explicitly identified DEMO source while obtaining the hierarchy.
    pid=int((ROOT / ".run/simulator.pid").read_text())
    command=Path(f"/proc/{pid}/cmdline").read_bytes()
    assert str(ROOT / "tools/device_simulator.py").encode() in command, "Not our simulator"
    os.kill(pid,signal.SIGSTOP)
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/spectra-test-ui.xml")
        return list(ET.fromstring(adb("shell", "cat", "/sdcard/spectra-test-ui.xml")).iter("node"))
    finally:
        os.kill(pid,signal.SIGCONT)

def find(value, attr="text", scroll=False):
    for _ in range(12 if scroll else 1):
        for node in nodes():
            if node.get(attr) == value:
                return node
        if scroll:
            adb("shell", "input", "swipe", "55", "1700", "55", "1200", "600")
            time.sleep(.6)
    raise AssertionError("Missing UI element: " + value)

def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r"\d+",node.get("bounds")))
    assert x2>x1 and y2>y1
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.3)

def screenshot(name):
    time.sleep(2)  # let the DEMO stream resume after UIAutomator's idle snapshot
    output=ROOT / "release" / "screenshots"
    output.mkdir(parents=True,exist_ok=True)
    (output / name).write_bytes(adb("exec-out","screencap","-p"))

def category(label):
    tap(find("Radar kategorisi seç","content-desc"))
    tap(find(label))

def nav(label):
    tap(find(label,"content-desc"))
    item=find(label,"content-desc")
    assert item.get("selected")=="true", label
    assert len(item.get("text")) == 1 and ord(item.get("text")) >= 0xf000, "Font Awesome only"

def main():
    adb("shell","am","force-stop","com.spectra.analyzer")
    adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
    time.sleep(4)
    nav("Genel")
    assert not any(n.get("text")=="Görünmeyeni izle." for n in nodes())
    assert not any(n.get("content-desc")=="Uygulamayı ve ölçüm servisini kapat" for n in nodes())
    find("DEMO VERİSİ")
    screenshot("spectra24-emulator.png")
    nav("Spektrum")
    tap(find("Waterfall duraklat / sürdür","content-desc"))
    screenshot("spectra24-spectrum.png")
    tap(find("Waterfall duraklat / sürdür","content-desc"))
    nav("Bağlantı")
    find("Hızlı Wi-Fi")
    screenshot("spectra24-connection.png")
    nav("Radar ve cihazlar")
    find("1. DEMO-AĞ-01 — Wi-Fi erişim noktası")
    screenshot("spectra24-radar.png")
    category("BLE")
    category("Trafik")
    category("Wi-Fi")
    nav("Ayarlar")
    tap(find("Ses dosyaları ekle",scroll=True))
    assert any("documentsui" in n.get("package","") for n in nodes()), "Android file picker"
    adb("shell","input","keyevent","4")
    tap(find("Uyarıyı dene",scroll=True))
    screenshot("spectra24-settings.png")
    tap(find("Ölçümü durdur ve uygulamadan çık",scroll=True))
    time.sleep(1)
    services=adb("shell","dumpsys","activity","services","com.spectra.analyzer").decode()
    assert "ServiceRecord" not in services, "Measurement service still running"
    activities=adb("shell","dumpsys","activity","activities").decode()
    resumed=[line for line in activities.splitlines() if "mResumedActivity" in line]
    assert not any("com.spectra.analyzer" in line for line in resumed), "App still visible"
    print("[OK] Five icon-only tabs, DEMO label, removed hero/header actions, pause, audio picker, preview and exit")

if __name__ == "__main__":
    main()
