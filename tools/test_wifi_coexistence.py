#!/usr/bin/env python3
"""Source regression guard for the fixed firmware radio power policy.

Not a hardware connectivity test. Both Wi-Fi and BLE remain enabled.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = (root / 'firmware/src/main.cpp').read_text()
calls = re.findall(r'WiFi\.setSleep\s*\(\s*([^)]*)\)', source)
assert calls == ['WIFI_PS_MIN_MODEM'], f'Unexpected Wi-Fi power policy: {calls}'
assert 'wantPowerSave' not in source, 'TCP must not toggle the shared radio power mode'
assert 'initializeBle' in source, 'Regression guard assumes BLE remains enabled'
assert 'WiFi.scanNetworks(true, true, true, 120, wifiScanChannel)' in source, 'Use paced single-channel inventory'
assert 'WIFI_CHANNEL_REST_MS = 550' in source, 'Give streaming a home-channel interval'
assert 'SPECTRUM_PERIOD_MS = 170' in source, 'Keep measured stream cadence bounded'
assert '!wifiScanRunning && !wifiScanChannel' in source, 'Do not overlap BLE and Wi-Fi inventory scans'
print('[OK] Wi-Fi/BLE coexistence source guard (not a radio field test)')
