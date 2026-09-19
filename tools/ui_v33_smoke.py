#!/usr/bin/env python3
"""3.3 visual/interaction regressions; dedicated DEMO emulator only."""
from ui_smoke import adb, nav, tap, find, nodes, screenshot
import time

adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(4)
nav("Ayarlar")
for label in ("HASSAS","SAKİN","DENGELİ"):
    tap(find(label))
    presets=[n for n in nodes() if n.get("text") in ("HASSAS","SAKİN","DENGELİ")]
    assert [n.get("text") for n in presets if n.get("selected")=="true"]==[label]
screenshot("spectra24-profiles-v33.png")
nav("Bağlantı")
for label in ("Otomatik bağlan","Hızlı Wi-Fi","Bluetooth"): find(label)
screenshot("spectra24-connection-v33.png")
nav("Radar ve cihazlar")
tap(find("1. DEMO-AĞ-01 — Wi-Fi erişim noktası"))
find("Pencereyi kapat","content-desc")
find("Takma ad")
assert not any(n.get("resource-id","").startswith("android:id/alertTitle") for n in nodes())
screenshot("spectra24-device-sheet-v33.png")
tap(find("Pencereyi kapat","content-desc"))
nav("Genel")
find("Olay günlüğü",scroll=True)
all_events=None
for _ in range(8):
    all_events=next((n for n in nodes() if n.get("text","").startswith("Tüm ") and n.get("text","").endswith(" olayı göster")),None)
    if all_events is not None: break
    adb("shell","input","swipe","55","1700","55","1200","500")
assert all_events is not None,"Need more than six recorded DEMO events"
tap(all_events)
find("Olay geçmişi")
find("Tüm olaylar")
tap(find("Uyarılar"))
tap(find("Tüm olaylar"))
screenshot("spectra24-journal-sheet-v33.png")
tap(find("Pencereyi kapat","content-desc"))
nav("Spektrum")
screenshot("spectra24-spectrum-v33.png")
print("[OK] 3.3 profile selection, connection labels, custom device/journal sheets and filters")
