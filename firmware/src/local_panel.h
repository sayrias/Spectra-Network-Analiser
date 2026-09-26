#pragma once

#include <Arduino.h>

namespace LocalPanel {

enum class AlertLevel : uint8_t { Normal = 0, Medium = 1, High = 2, Extreme = 3 };

struct Snapshot {
  const uint8_t* spectrum = nullptr;
  uint8_t average = 0;
  uint8_t peak = 0;
  uint8_t peakChannel = 0;
  uint8_t affectedChannels = 0;
  uint8_t confidence = 0;
  uint8_t calibration = 0;
  uint32_t sweepSequence = 0;
  bool calibrating = true;
  bool radioReady = false;
  bool phoneConnected = false;
  bool phoneRadiosEnabled = true;
  uint32_t managementFrames = 0;
  uint32_t dataFrames = 0;
  uint32_t controlFrames = 0;
  uint8_t wifiDevices = 0;
  uint8_t bleDevices = 0;
  uint8_t airDevices = 0;
  int8_t lastRssi = -127;
  AlertLevel alert = AlertLevel::Normal;
};

void begin();
void service(const Snapshot& snapshot);
void configureBuzzer(bool enabled, uint8_t toneProfile, uint8_t levelPercent);
void testBuzzer();
bool takePhoneRadioToggleRequest();
bool ready();

}  // namespace LocalPanel
