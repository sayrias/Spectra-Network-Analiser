#!/usr/bin/env python3
from ui_smoke import adb, nav, tap, find, nodes, screenshot, category
import time

adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(4)
nav("Radar ve cihazlar")
tap(find("1. DEMO-AĞ-01 — Wi-Fi erişim noktası"))
find("Takma ad")
tap(find("Takma ad"))
find("Bu adrese takma ad ver")
tap(find("Kapat"))
category("BLE")
assert not any("DEMO-AĞ-01" in n.get("text","") for n in nodes()), "Wi-Fi item leaked into BLE"
screenshot("spectra24-ble-v31.png")
tap(find("Radar işlemleri","content-desc"));tap(find("BLE adlarını sorgula"))
find("Tek seferlik BLE ad sorgulaması")
tap(find("Kapat"))
category("Trafik")
assert not any("DEMO-AĞ-01" in n.get("text","") for n in nodes()), "Wi-Fi item leaked into traffic"
nav("Genel")
find("Olay günlüğü",scroll=True)
tap(find("Tümü",scroll=True))
find("Uyarılar")
tap(find("Uyarılar"))
screenshot("spectra24-journal.png")
tap(find("Olay günlüğünü temizle","content-desc",scroll=True))
find("Kayıtlar silinsin mi?")
tap(find("Kapat"))
print("[OK] Typed device titles, alias dialog, radar/list filters, name-scan consent, journal filter and clear confirmation")
