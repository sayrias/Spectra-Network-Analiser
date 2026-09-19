#!/usr/bin/env python3
"""Checks Android accepts the request; does NOT claim an RF Wi-Fi connection."""
from ui_smoke import adb, nav, tap, find, screenshot
import time

adb("shell","pm","grant","com.spectra.analyzer","android.permission.NEARBY_WIFI_DEVICES")
adb("shell","am","force-stop","com.spectra.analyzer")
adb("shell","am","start","-n","com.spectra.analyzer/.MainActivity","--ez","simulator","true")
time.sleep(3)
nav("Bağlantı")
adb("logcat","-c")
tap(find("SPECTRA WI-FI"))
time.sleep(3)
logs=adb("logcat","-d","-s","SpectraWifi:I").decode()
assert "WIFI_REQUEST_REGISTERED" in logs, logs
assert "permission rejected" not in logs, logs
screenshot("spectra24-wifi-request.png")
adb("shell","input","keyevent","4")
print("[OK] Android accepted WifiNetworkSpecifier request; no missing network permission")
print("[NOT TESTED] Connecting a physical phone to the physical ESP32 AP")
