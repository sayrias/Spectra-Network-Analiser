#!/usr/bin/env python3
"""3.4 app-owned sheets, guide, fullscreen/restore and visible version."""
from ui_smoke import adb, nav, tap, find, nodes, screenshot
import time

adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(4)
nav("Genel")
tap(find("Waterfall açıklaması","content-desc",scroll=True))
find("Waterfall rehberi")
find("Nokta nokta görünüm neden normal?")
screenshot("spectra24-guide-v34.png")
tap(find("Pencereyi kapat","content-desc"))
tap(find("Waterfall tam ekran","content-desc"))
find("Waterfall · tam ekran")
tap(find("Duraklat"));find("Sürdür")
tap(find("Canlı"));find("Duraklat")
screenshot("spectra24-fullscreen-home-v34.png")
adb("shell","input","keyevent","4")
find("Waterfall tam ekran","content-desc")
nav("Spektrum")
tap(find("Spektrum tam ekran","content-desc"))
find("Spektrum · tam ekran")
screenshot("spectra24-fullscreen-spectrum-v34.png")
tap(find("Tam ekrandan çık","content-desc"))
find("Spektrum tam ekran","content-desc")
nav("Ayarlar")
assert any(n.get("text", "").startswith("SPECTRA 3.") and "özel arayüz" in n.get("text", "") for n in nodes())
tap(find("HASSAS"))
find("Seçili: Hassas · uygulamak için Kaydet")
assert find("HASSAS").get("selected")=="true"
assert find("DENGELİ").get("selected")=="false"
screenshot("spectra24-sensitive-v34.png")
tap(find("Uyarı sesi seç","content-desc",scroll=True))
find("Uyarı sesi");tap(find("Sonar"))
assert find("Uyarı sesi seç","content-desc").get("text")=="Sonar"
tap(find("Uyarı sesi seç","content-desc"))
assert find("Sonar").get("selected")=="true"
screenshot("spectra24-tone-picker-v34.png")
tap(find("Pencereyi kapat","content-desc"))
tap(find("Tarama ayrıntısı seç","content-desc",scroll=True))
find("Tarama ayrıntısı")
tap(find("Ayrıntılı · 12 örnek/kanal"))
assert find("Tarama ayrıntısı seç","content-desc").get("text")=="Ayrıntılı · 12 örnek/kanal"
print("[OK] 3.4 guide, both fullscreen views, back/restore, profile, custom pickers and build label")
