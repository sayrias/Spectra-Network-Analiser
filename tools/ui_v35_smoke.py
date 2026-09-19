#!/usr/bin/env python3
"""Dedicated DEMO only: category isolation and live settings on one connection."""
from ui_smoke import adb, nav, tap, find, nodes, screenshot, ROOT
import time

adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(4)
nav("Radar ve cihazlar")
for category,expected in (("AirTag / Find My","DEMO-FIND-MY"),("iBeacon","DEMO-IBEACON"),("BLE sensörler","DEMO-SENSOR")):
    tap(find("Radar kategorisi seç","content-desc"));tap(find(category))
    labels=[n.get("text","") for n in nodes()]
    assert any(expected in x for x in labels),labels
    assert not any("DEMO-APPLE-OTHER" in x for x in labels),"Generic Apple leaked into filter"
    screenshot("spectra24-"+expected.lower()+"-category-v35.png")
tap(find("Radar işlemleri","content-desc"));find("CSV dışa aktar")
tap(find("Etiket algılama hakkında"));find("Etiket ve beacon gözlemi")
screenshot("spectra24-tracker-info-v35.png")
tap(find("Pencereyi kapat","content-desc"))
nav("Ayarlar");find("SPECTRA 3.5.0 · özel arayüz")
log=ROOT/".run/simulator.log"
before=log.read_text().count("[BAĞLANDI]")
for label,count in (("Ayrıntılı · 12 örnek/kanal",12),("Dengeli · 8 örnek/kanal",8),("Hızlı · 4 örnek/kanal",4)):
    tap(find("Tarama ayrıntısı seç","content-desc",scroll=True));tap(find(label))
    time.sleep(1)
    find(f"Cihaz onayladı: {count} örnek/kanal")
    assert f"[SETTINGS] samples={count}" in log.read_text()
    assert log.read_text().count("[BAĞLANDI]")==before,"Changing scan speed reconnected socket"
screenshot("spectra24-live-scan-settings-v35.png")
print("[OK] 3.5 category isolation, actions sheet, 12/8/4 immediate ACKs without reconnect")
