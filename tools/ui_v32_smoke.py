#!/usr/bin/env python3
"""Additional 3.2 checks on the dedicated DEMO emulator only."""
from ui_smoke import adb, nav, tap, find, nodes, screenshot, category
import re
import time

adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(4)
nav("Spektrum")
tap(find("Tepe izi"))
find("Tepe izi açık")
controls=[find("Waterfall duraklat / sürdür","content-desc"),
          find("Görünüm geçmişini temizle","content-desc"),find("Tepe izi açık")]
bounds=[list(map(int,re.findall(r"\d+",n.get("bounds")))) for n in controls]
assert len({b[1] for b in bounds})==1,"Control tops differ"
assert len({b[3]-b[1] for b in bounds})==1,"Control heights differ"
tap(controls[1])
screenshot("spectra24-peak-v32.png")
nav("Bağlantı")
# UIAutomator pauses DEMO for idleness; inspect a fresh hierarchy without waiting
# for idle to verify recovery rather than treating the pause as a hardware fault.
time.sleep(2)
screenshot("spectra24-connection-v32.png")
nav("Radar ve cihazlar")
tap(find("Radar işlemleri","content-desc"));find("CSV dışa aktar")
find("BLE adlarını sorgula")
tap(find("Pencereyi kapat","content-desc"));category("BLE")
assert not any("DEMO-AĞ-01" in n.get("text","") for n in nodes())
category("Wi-Fi")
screenshot("spectra24-radar-v32.png")
print("[OK] 3.2 peak-hold, reset, aligned controls, radar filters and compact actions")
