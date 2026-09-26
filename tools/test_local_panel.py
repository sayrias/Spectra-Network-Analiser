#!/usr/bin/env python3
"""Static regression checks for the optional local display/button/buzzer hardware."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
panel = (root / 'firmware/src/local_panel.cpp').read_text()
header = (root / 'firmware/src/local_panel.h').read_text()
firmware = (root / 'firmware/src/main.cpp').read_text()
android = (root / 'android-app/app/src/main/java/com/spectra/analyzer/MainActivity.java').read_text()
service = (root / 'android-app/app/src/main/java/com/spectra/analyzer/AnalyzerService.java').read_text()
platform = (root / 'firmware/platformio.ini').read_text()

expected_panel_flags = {
    'TFT_SCLK=14', 'TFT_MOSI=13', 'TFT_CS=-1', 'TFT_DC=26',
    'TFT_RST=33', 'USE_HSPI_PORT=1', 'SPI_FREQUENCY=20000000', 'LOAD_GLCD=1',
}
for flag in expected_panel_flags:
    assert flag in platform, flag
for pin in ('MODE_BUTTON = 32', 'BUZZER = 25'):
    assert pin in panel, pin
for level in ('Medium = 1', 'High = 2', 'Extreme = 3'):
    assert level in header, level
for feature in ('drawWaterfall', 'captureWaterfall', 'drawSpectrum', 'drawTraffic',
                'drawFinder', 'drawAlert', 'drawConnection', 'serviceButton'):
    assert feature in panel, feature
assert 'MODE_COUNT = 6' in panel
assert 'panelSnapshot.sweepSequence = sweepSequence' in firmware
assert 'panelSnapshot.phoneRadiosEnabled = phoneRadiosEnabled' in firmware
assert 'panel.setSwapBytes(true)' in panel
assert 'render(snapshot, full)' in panel
for label in ('WiFi TRAFIK', 'GERCEK 802.11 CERCEVELERI',
              'YAGI / GORELI RF YAKINLIK', 'UYARI SEVIYESI',
              'TELEFON RADYOLARI'):
    assert label in panel, label
for offline_feature in ('phoneRfOff', 'WiFi.mode(WIFI_OFF)',
                        'LocalPanel::takePhoneRadioToggleRequest()'):
    assert offline_feature in firmware, offline_feature
assert 'phoneRadiosEnabled' in header
for buzzer_feature in ('buzzerLevel) * 58 / 70', '{1400, 1700, 1950}',
                       'phase >= 325 && phase < 470'):
    assert buzzer_feature in panel, buzzer_feature
for setting in ('buzzerEnabled', 'buzzerTone', 'buzzerLevel'):
    assert setting in firmware, setting
for setting in ('buzzerSwitch', 'buzzerToneSpinner', 'buzzerLevelBar', 'CİHAZDA DENE'):
    assert setting in android, setting
for wire_name in ('"buzzer"', '"buzzerTone"', '"buzzerLevel"'):
    assert wire_name in service, wire_name
assert 'bodmer/TFT_eSPI@2.5.43' in platform
assert '#include <TFT_eSPI.h>' in panel
assert 'TFT_SCLK=18' not in platform and 'TFT_MOSI=23' not in platform
print('[OK] local ST7789/button/buzzer modes and Android controls')
