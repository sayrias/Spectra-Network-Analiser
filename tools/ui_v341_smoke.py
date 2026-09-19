#!/usr/bin/env python3
"""Visual compact/fullscreen density comparison; DEMO emulator only.

Exact cell-size/history assertions are in Android ModelTests. These screenshots
exercise real layout in both screens, using only the labelled test stream.
"""
from ui_smoke import adb, nav, tap, find, screenshot
import time

adb("shell", "am", "force-stop", "com.spectra.analyzer")
adb("shell", "am", "start", "-n", "com.spectra.analyzer/.MainActivity", "--ez", "simulator", "true")
time.sleep(45)  # Accumulate enough real test frames to exceed 96 rows.
find("Waterfall tam ekran", "content-desc", scroll=True)
screenshot("spectra24-compact-v341.png")
tap(find("Waterfall tam ekran", "content-desc"))
find("Waterfall · tam ekran")
screenshot("spectra24-fullscreen-home-v341.png")
tap(find("Duraklat"));find("Sürdür")
adb("shell", "input", "swipe", "180", "700", "850", "700", "500")
screenshot("spectra24-fullscreen-history-v341.png")
tap(find("Canlı"));find("Duraklat")
tap(find("Tam ekrandan çık", "content-desc"))
find("Waterfall tam ekran", "content-desc")
nav("Spektrum")
screenshot("spectra24-spectrum-compact-v341.png")
tap(find("Spektrum tam ekran", "content-desc"))
find("Spektrum · tam ekran")
screenshot("spectra24-fullscreen-spectrum-v341.png")
adb("shell", "input", "keyevent", "4")
find("Spektrum tam ekran", "content-desc")
print("[OK] 3.4.1 compact/fullscreen, history gesture, live and restore on both pages")
