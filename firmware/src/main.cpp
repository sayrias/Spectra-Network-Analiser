#include <Arduino.h>
#include <ArduinoJson.h>
#include <NimBLEDevice.h>
#include <Preferences.h>
#include <RF24.h>
#include <SPI.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <algorithm>
#include <deque>
#include <esp_wifi.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

namespace Pins {
constexpr uint8_t NRF_CE = 4;
constexpr uint8_t NRF_CSN = 5;
constexpr uint8_t SPI_SCK = 18;
constexpr uint8_t SPI_MISO = 19;
constexpr uint8_t SPI_MOSI = 23;
constexpr uint8_t STATUS_LED = 2;
}  // namespace Pins

namespace Config {
constexpr char AP_SSID[] = "SPECTRA-24";
constexpr char AP_PASSWORD[] = "spectrum24";
constexpr uint8_t AP_CHANNEL = 1;
constexpr uint16_t CONTROL_PORT = 4211;
constexpr uint16_t SPECTRUM_PORT = 4210;
constexpr uint16_t DISCOVERY_PORT = 4212;
constexpr uint8_t NRF_CHANNELS = 126;
constexpr uint8_t SAMPLES_PER_CHANNEL = 4;
constexpr uint16_t SAMPLE_DWELL_US = 180;
constexpr uint16_t CALIBRATION_SWEEPS = 20;
constexpr uint32_t WIFI_SCAN_INTERVAL_MS = 22000;
constexpr uint32_t SPECTRUM_PERIOD_MS = 170; // ~5.9 real sweeps/s maximum.
constexpr uint32_t WIFI_CHANNEL_REST_MS = 550;
constexpr uint32_t CONNECTION_GRACE_MS = 4000;
constexpr uint32_t BLE_SCAN_INTERVAL_MS = 18000;
constexpr uint32_t BLE_DEVICE_TTL_MS = 60000;
constexpr uint8_t MAX_WIFI_RESULTS = 30;
constexpr uint8_t MAX_BLE_RESULTS = 40;
constexpr size_t SPECTRUM_PACKET_SIZE = 144;
constexpr char BLE_SERVICE_UUID[] = "7a240001-8e7c-4f31-9a62-6d4f53503234";
constexpr char BLE_STATUS_UUID[] = "7a240002-8e7c-4f31-9a62-6d4f53503234";
constexpr char BLE_CONTROL_UUID[] = "7a240003-8e7c-4f31-9a62-6d4f53503234";
constexpr char BLE_SPECTRUM_UUID[] = "7a240004-8e7c-4f31-9a62-6d4f53503234";
constexpr char BLE_EVENT_UUID[] = "7a240005-8e7c-4f31-9a62-6d4f53503234";
}  // namespace Config

struct AnalyzerSettings {
  uint8_t excessThreshold = 30;
  uint8_t minimumAffectedChannels = 28;
  uint32_t holdTimeMs = 4000;
  uint32_t cooldownMs = 30000;
  bool alertsEnabled = true;
};

struct WifiNetwork {
  String ssid;
  String bssid;
  String security;
  int32_t rssi;
  int32_t channel;
};

struct BleDeviceRecord {
  char mac[18] = {};
  char name[64] = {};
  char services[160] = {};
  uint16_t appearance = 0;
  uint16_t manufacturer = 0;
  char mfgPrefix[9] = {};
  uint8_t mfgLength = 0;
  uint32_t signatureSeenMs = 0;
  int16_t rssi = -127;
  uint32_t lastSeenMs = 0;
  uint32_t advertisements = 0;
};

struct RawSweep { uint8_t values[126]; uint32_t durationUs; uint32_t number; };
QueueHandle_t sweepQueue = nullptr;
volatile uint8_t scanSamples = Config::SAMPLES_PER_CHANNEL;
uint32_t skippedSweeps = 0, udpSendErrors = 0;
bool wifiPowerSave = true;
uint32_t sweepDurationUs = 0;
struct AirDevice {
  uint8_t mac[6] = {};
  char ssid[33] = {};
  uint64_t txBytes = 0, rxBytes = 0;
  uint32_t txPackets = 0, rxPackets = 0, firstMs = 0, lastMs = 0;
  int8_t rssi = -127;
  uint8_t channel = 0;
  bool used = false, rssiKnown = false;
};
constexpr uint8_t MAX_AIR_DEVICES = 48;
AirDevice airDevices[MAX_AIR_DEVICES];
uint64_t airBytes = 0;
uint32_t airTableEvictions = 0;

struct BleCommand { char text[384] = {}; };

RF24 radio(Pins::NRF_CE, Pins::NRF_CSN);
WiFiServer controlServer(Config::CONTROL_PORT);
WiFiClient phoneClient;
WiFiUDP spectrumUdp;
WiFiUDP discoveryUdp;
Preferences preferences;

AnalyzerSettings settings;
String stationSsid;
String stationPassword;
std::vector<WifiNetwork> wifiNetworks;
BleDeviceRecord bleDevices[Config::MAX_BLE_RESULTS];
uint8_t bleDeviceCount = 0;
portMUX_TYPE bleMux = portMUX_INITIALIZER_UNLOCKED;

uint8_t spectrum[Config::NRF_CHANNELS] = {};
float smoothedSpectrum[Config::NRF_CHANNELS] = {};
float baseline[Config::NRF_CHANNELS] = {};
bool radioReady = false;
uint16_t calibrationSweeps = 0;
uint32_t sweepSequence = 0;
uint8_t sweepAverage = 0;
uint8_t sweepPeak = 0;
uint8_t sweepPeakChannel = 0;
uint8_t affectedChannels = 0;
uint8_t alarmConfidence = 0;
bool interferenceCandidate = false;
bool interferenceAlarm = false;
uint32_t candidateSinceMs = 0;
uint32_t clearSinceMs = 0;
uint32_t lastAlarmMs = 0;
uint32_t alertSequence = 0;

volatile uint32_t packetMgmt = 0;
volatile uint32_t packetData = 0;
volatile uint32_t packetCtrl = 0;
volatile int8_t lastPacketRssi = -127;
portMUX_TYPE packetMux = portMUX_INITIALIZER_UNLOCKED;

bool wifiScanRunning = false;
uint32_t lastWifiScanMs = 0;
uint8_t wifiScanChannel = 0, wifiScanLastChannel = 11;
uint32_t wifiChannelDoneMs = 0, connectionActivityMs = 0;
std::vector<WifiNetwork> wifiScanResults;
uint32_t lastStationAttemptMs = 0;
wl_status_t previousStationStatus = WL_NO_SHIELD;
NimBLEScan* bleScanner = nullptr;
NimBLEServer* bleServer = nullptr;
NimBLECharacteristic* bleStatusCharacteristic = nullptr;
NimBLECharacteristic* bleSpectrumCharacteristic = nullptr;
NimBLECharacteristic* bleEventCharacteristic = nullptr;
volatile bool bleClientConnected = false;
volatile bool bleScanRunning = false;
uint32_t lastBleScanMs = 0;
bool nameScanPending = false;
uint8_t bleMessageId = 0;
QueueHandle_t bleCommandQueue = nullptr;
String controlInput;
struct BlePending { String value; uint8_t id; size_t offset = 0; size_t payloadSize = 0; uint8_t part = 0; };
std::deque<BlePending> bleOutgoing;
uint32_t bleListBatch = 0;

void broadcastWifiNetworks();
void broadcastBleDevices();
void sendHello();
size_t bleFragmentPayload();
void serviceBleOutput() {
  if (!bleClientConnected) { bleOutgoing.clear(); return; }
  if (bleOutgoing.empty()) return;
  static uint32_t last = 0;
  if (millis() - last < 5) return;
  last = millis();
  auto& pending = bleOutgoing.front();
  if (!pending.payloadSize) pending.payloadSize = bleFragmentPayload();
  if (!pending.payloadSize) return;
  size_t count = std::min(pending.payloadSize, pending.value.length() - pending.offset);
  uint8_t frame[184];
  frame[0] = 'J'; frame[1] = pending.id; frame[2] = pending.part;
  frame[3] = (pending.value.length() + pending.payloadSize - 1) / pending.payloadSize;
  memcpy(frame + 4, pending.value.c_str() + pending.offset, count);
  bleEventCharacteristic->setValue(frame, count + 4);
  bleEventCharacteristic->notify(true);
  pending.offset += count; ++pending.part;
  if (pending.offset >= pending.value.length()) bleOutgoing.pop_front();
}

void sendStatus(bool notify);
void resetCalibration();

uint16_t crc16Ccitt(const uint8_t* data, size_t length) {
  uint16_t crc = 0xFFFF;
  for (size_t i = 0; i < length; ++i) {
    crc ^= static_cast<uint16_t>(data[i]) << 8;
    for (uint8_t bit = 0; bit < 8; ++bit) {
      crc = (crc & 0x8000) ? static_cast<uint16_t>((crc << 1) ^ 0x1021)
                           : static_cast<uint16_t>(crc << 1);
    }
  }
  return crc;
}

String securityName(wifi_auth_mode_t mode) {
  switch (mode) {
    case WIFI_AUTH_OPEN: return "OPEN";
    case WIFI_AUTH_WEP: return "WEP";
    case WIFI_AUTH_WPA_PSK: return "WPA";
    case WIFI_AUTH_WPA2_PSK: return "WPA2";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2";
#ifdef WIFI_AUTH_WPA3_PSK
    case WIFI_AUTH_WPA3_PSK: return "WPA3";
#endif
    default: return "SECURED";
  }
}

uint8_t calibrationPercent() {
  return static_cast<uint8_t>(std::min<uint32_t>(
      100, calibrationSweeps * 100UL / Config::CALIBRATION_SWEEPS));
}

void addSettings(JsonObject target) {
  target["threshold"] = settings.excessThreshold;
  target["channels"] = settings.minimumAffectedChannels;
  target["hold"] = settings.holdTimeMs;
  target["cooldown"] = settings.cooldownMs;
  target["alerts"] = settings.alertsEnabled;
  target["samples"] = scanSamples;
}

String jsonString(const JsonDocument& document) {
  String value;
  serializeJson(document, value);
  return value;
}

size_t bleFragmentPayload() {
  if (bleServer == nullptr || bleServer->getConnectedCount() == 0) return 0;
  uint16_t mtu = 23;
  const std::vector<uint16_t> peers = bleServer->getPeerDevices();
  if (!peers.empty()) mtu = bleServer->getPeerMTU(peers.front());
  return static_cast<size_t>(constrain(static_cast<int>(mtu) - 7, 16, 180));
}

void sendBleFragments(NimBLECharacteristic* characteristic, uint8_t marker,
                      const uint8_t* data, size_t length) {
  if (!bleClientConnected || characteristic == nullptr || length == 0) return;
  const size_t payloadSize = bleFragmentPayload();
  if (payloadSize == 0) return;
  const uint8_t parts = static_cast<uint8_t>((length + payloadSize - 1) / payloadSize);
  const uint8_t messageId = ++bleMessageId;
  uint8_t frame[184];
  for (uint8_t part = 0; part < parts; ++part) {
    const size_t offset = part * payloadSize;
    const size_t count = std::min(payloadSize, length - offset);
    frame[0] = marker;
    frame[1] = messageId;
    frame[2] = part;
    frame[3] = parts;
    memcpy(frame + 4, data + offset, count);
    characteristic->setValue(frame, count + 4);
    characteristic->notify(true);
    if (parts > 1) delay(3);
  }
}

void sendClientJson(const JsonDocument& document) {
  const String value = jsonString(document);
  if (phoneClient && phoneClient.connected()) {
    phoneClient.print(value);
    phoneClient.print('\n');
  }
  if (phoneClient.connected()) { bleOutgoing.clear(); return; }
  if (!bleClientConnected) { bleOutgoing.clear(); return; }
  auto enqueue = [](const String& json) {
    if (bleOutgoing.size() < 24) {
      BlePending pending; pending.value = json; pending.id = ++bleMessageId;
      bleOutgoing.push_back(std::move(pending));
    }
  };
  // Bound a JSON message to fit the 8-bit fragment counter even at MTU 23.
  const char* type = document["type"] | "";
  const char* field = strcmp(type, "wifi") == 0 ? "networks" : "devices";
  JsonArrayConst items = document[field].as<JsonArrayConst>();
  if (!items.isNull() && items.size() > 6) {
    const uint32_t batch = ++bleListBatch;
    const unsigned pages = (items.size() + 5) / 6;
    if (bleOutgoing.size() + pages > 24) return;
    for (unsigned page = 0; page < pages; ++page) {
      JsonDocument chunk;
      chunk["type"] = type; chunk["batch"] = batch; chunk["page"] = page; chunk["pages"] = pages;
      chunk["updated"] = document["updated"]; chunk["live"] = true;
      JsonArray out = chunk[field].to<JsonArray>();
      for (unsigned i = page * 6; i < std::min<unsigned>(items.size(), (page + 1) * 6); ++i) out.add(items[i]);
      enqueue(jsonString(chunk));
    }
  } else enqueue(value);
}

void sendStatus(bool notify) {
  JsonDocument doc;
  doc["type"] = "status";
  doc["device"] = "SPECTRA-24";
  doc["firmware"] = "3.5.0";
  doc["radio"] = radioReady;
  doc["calibrating"] = calibrationSweeps < Config::CALIBRATION_SWEEPS;
  doc["calibration"] = calibrationPercent();
  doc["apIp"] = WiFi.softAPIP().toString();
  doc["apSsid"] = Config::AP_SSID;
  doc["staConnected"] = WiFi.status() == WL_CONNECTED;
  doc["staIp"] = WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString() : "";
  doc["staSsid"] = WiFi.status() == WL_CONNECTED ? WiFi.SSID() : stationSsid;
  doc["wifiChannel"] = WiFi.channel();
  doc["sweepUs"] = sweepDurationUs;
  doc["scanHz"] = sweepDurationUs ? 1000000.0 / sweepDurationUs : 0;
  doc["samples"] = scanSamples;
  doc["sweeps"] = sweepSequence;
  doc["skippedSweeps"] = skippedSweeps;
  doc["udpSendErrors"] = udpSendErrors;
  doc["wifiPowerSave"] = wifiPowerSave;
  doc["flowIntervalMs"] = 500;
  doc["bleConnected"] = bleClientConnected;
  doc["heapFree"] = ESP.getFreeHeap();
  doc["heapMin"] = ESP.getMinFreeHeap();
  doc["uptime"] = millis();
  doc["trafficCapacity"] = MAX_AIR_DEVICES;
  const String value = jsonString(doc);
  if (bleStatusCharacteristic != nullptr) {
    // GATT attributes are limited to 512 bytes; full JSON uses the event channel.
    if (value.length() <= 512) bleStatusCharacteristic->setValue(value.c_str());
    // Full status travels on the fragmented event channel. Never send truncated JSON.
    if (notify && bleClientConnected && value.length() <= bleFragmentPayload() + 4)
      bleStatusCharacteristic->notify(true);
  }
  if (notify) sendClientJson(doc);
}

void sendHello() {
  JsonDocument doc;
  doc["type"] = "hello";
  doc["device"] = "SPECTRA-24";
  doc["firmware"] = "3.5.0";
  doc["radio"] = radioReady;
  doc["protocol"] = 2;
  doc["spectrumPort"] = Config::SPECTRUM_PORT;
  doc["discoveryPort"] = Config::DISCOVERY_PORT;
  doc["calibrating"] = calibrationSweeps < Config::CALIBRATION_SWEEPS;
  doc["calibration"] = calibrationPercent();
  doc["staConnected"] = WiFi.status() == WL_CONNECTED;
  doc["staIp"] = WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString() : "";
  doc["staSsid"] = WiFi.status() == WL_CONNECTED ? WiFi.SSID() : stationSsid;
  addSettings(doc["settings"].to<JsonObject>());
  sendClientJson(doc);
}

void saveSettings() {
  preferences.putUChar("excess", settings.excessThreshold);
  preferences.putUChar("channels2", settings.minimumAffectedChannels);
  preferences.putUInt("hold2", settings.holdTimeMs);
  preferences.putUInt("cooldown", settings.cooldownMs);
  preferences.putBool("alerts2", settings.alertsEnabled);
  preferences.putUChar("samples3", scanSamples);
}

void loadSettings() {
  preferences.begin("spectra24", false);
  settings.excessThreshold = constrain(preferences.getUChar("excess", 30), 10, 80);
  settings.minimumAffectedChannels = constrain(preferences.getUChar("channels2", 28), 8, 100);
  settings.holdTimeMs = constrain(preferences.getUInt("hold2", 4000), 1500UL, 20000UL);
  settings.cooldownMs = constrain(preferences.getUInt("cooldown", 30000), 10000UL, 300000UL);
  settings.alertsEnabled = preferences.getBool("alerts2", true);
  scanSamples = constrain(preferences.getUChar("samples3", 4), 4, 12);
  stationSsid = preferences.isKey("staSsid") ? preferences.getString("staSsid", "") : "";
  stationPassword = preferences.isKey("staPass") ? preferences.getString("staPass", "") : "";
}

void resetCalibration() {
  calibrationSweeps = 0;
  memset(baseline, 0, sizeof(baseline));
  memset(smoothedSpectrum, 0, sizeof(smoothedSpectrum));
  interferenceCandidate = false;
  interferenceAlarm = false;
  candidateSinceMs = clearSinceMs = 0;
  Serial.println("[RF] Environment calibration restarted");
  sendStatus(true);
}

void connectStation(const String& ssid, const String& password, bool persist) {
  if (ssid.isEmpty()) return;
  if (wifiScanRunning) {
    esp_wifi_scan_stop();
    WiFi.scanDelete();
    wifiScanRunning = false;
  }
  wifiScanChannel = 0; wifiScanResults.clear();
  stationSsid = ssid;
  stationPassword = password;
  if (persist) {
    preferences.putString("staSsid", stationSsid);
    preferences.putString("staPass", stationPassword);
  }
  WiFi.begin(stationSsid.c_str(), stationPassword.c_str());
  lastStationAttemptMs = millis();
  Serial.printf("[WiFi] Connecting to phone/LAN hotspot: %s\n", stationSsid.c_str());
}

void handleControlLine(const String& line) {
  JsonDocument doc;
  if (deserializeJson(doc, line)) return;
  const char* action = doc["action"] | "";

  if (strcmp(action, "hello") == 0) {
    sendHello();
  } else if (strcmp(action, "ping") == 0) {
    JsonDocument pong;
    pong["type"] = "pong";
    pong["uptime"] = millis();
    sendClientJson(pong);
  } else if (strcmp(action, "settings") == 0) {
    settings.excessThreshold = constrain(doc["threshold"] | settings.excessThreshold, 10, 80);
    settings.minimumAffectedChannels = constrain(doc["channels"] | settings.minimumAffectedChannels, 8, 100);
    settings.holdTimeMs = constrain(doc["hold"] | settings.holdTimeMs, 1500UL, 20000UL);
    settings.cooldownMs = constrain(doc["cooldown"] | settings.cooldownMs, 10000UL, 300000UL);
    settings.alertsEnabled = doc["alerts"] | settings.alertsEnabled;
    scanSamples = constrain(doc["samples"] | static_cast<int>(scanSamples), 4, 12);
    saveSettings();
    JsonDocument reply;
    reply["type"] = "saved";
    addSettings(reply["settings"].to<JsonObject>());
    sendClientJson(reply);
  } else if (strcmp(action, "alertTest") == 0) {
    JsonDocument test;
    test["type"] = "alert"; test["active"] = true; test["test"] = true;
    test["sequence"] = ++alertSequence;
    test["classification"] = "notification_path_test";
    sendClientJson(test);
  } else if (strcmp(action, "scanNames") == 0) {
    nameScanPending = true;
    JsonDocument reply; reply["type"] = "name_scan"; reply["accepted"] = true;
    sendClientJson(reply);
  } else if (strcmp(action, "scan") == 0) {
    lastWifiScanMs = millis() - Config::WIFI_SCAN_INTERVAL_MS;
    lastBleScanMs = millis() - Config::BLE_SCAN_INTERVAL_MS;
    broadcastWifiNetworks();
    broadcastBleDevices();
  } else if (strcmp(action, "calibrate") == 0) {
    resetCalibration();
  } else if (strcmp(action, "wifi") == 0) {
    const String ssid = String(doc["ssid"] | "");
    const String password = String(doc["password"] | "");
    connectStation(ssid, password, true);
    JsonDocument reply;
    reply["type"] = "wifi_config";
    reply["accepted"] = !ssid.isEmpty();
    reply["ssid"] = ssid;
    sendClientJson(reply);
  } else if (strcmp(action, "forgetWifi") == 0) {
    stationSsid = stationPassword = "";
    preferences.remove("staSsid");
    preferences.remove("staPass");
    WiFi.disconnect(false, true);
    sendStatus(true);
  }
}

void serviceControlConnection() {
  WiFiClient incoming = controlServer.available();
  if (incoming) {
    if (phoneClient && phoneClient.connected()) phoneClient.stop();
    phoneClient = incoming;
    connectionActivityMs = millis();
    phoneClient.setNoDelay(true);
    phoneClient.setTimeout(20);
    controlInput = "";
    Serial.printf("[TCP] App connected: %s\n", phoneClient.remoteIP().toString().c_str());
    sendHello();
    broadcastWifiNetworks();
    broadcastBleDevices();
  }
  if (!phoneClient || !phoneClient.connected()) return;
  while (phoneClient.available()) {
    const char character = static_cast<char>(phoneClient.read());
    if (character == '\n') {
      if (!controlInput.isEmpty()) handleControlLine(controlInput);
      controlInput = "";
    } else if (character != '\r' && controlInput.length() < 1024) {
      controlInput += character;
    } else if (controlInput.length() >= 1024) {
      controlInput = "";
    }
  }
}

void serviceBleCommands() {
  if (bleCommandQueue == nullptr) return;
  BleCommand command;
  while (xQueueReceive(bleCommandQueue, &command, 0) == pdTRUE) {
    handleControlLine(String(command.text));
  }
}

void serviceDiscovery() {
  const int length = discoveryUdp.parsePacket();
  if (length <= 0) return;
  char request[40] = {};
  const int count = discoveryUdp.read(reinterpret_cast<uint8_t*>(request), sizeof(request) - 1);
  if (count <= 0 || strncmp(request, "SP24_DISCOVER", 13) != 0) return;
  JsonDocument doc;
  doc["type"] = "discovery";
  doc["device"] = "SPECTRA-24";
  doc["firmware"] = "3.5.0";
  doc["ip"] = WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
  doc["apIp"] = WiFi.softAPIP().toString();
  doc["sta"] = WiFi.status() == WL_CONNECTED;
  doc["radio"] = radioReady;
  const String response = jsonString(doc);
  discoveryUdp.beginPacket(discoveryUdp.remoteIP(), discoveryUdp.remotePort());
  discoveryUdp.write(reinterpret_cast<const uint8_t*>(response.c_str()), response.length());
  discoveryUdp.endPacket();
}

// No allocation or packet content retention in the Wi-Fi callback.
int airIndex(const uint8_t* mac, uint32_t now, int protectedIndex = -1) {
  if (mac[0] & 1) return -1;
  int freeIndex = -1, oldest = -1;
  for (int i = 0; i < MAX_AIR_DEVICES; ++i) {
    if (airDevices[i].used && memcmp(airDevices[i].mac, mac, 6) == 0) return i;
    if (!airDevices[i].used) freeIndex = i;
    if (i != protectedIndex && (oldest < 0 || now - airDevices[i].lastMs > now - airDevices[oldest].lastMs)) oldest = i;
  }
  int index = freeIndex >= 0 ? freeIndex : oldest;
  if (freeIndex < 0) ++airTableEvictions;
  airDevices[index] = AirDevice{};
  memcpy(airDevices[index].mac, mac, 6);
  airDevices[index].used = true;
  airDevices[index].firstMs = now;
  airDevices[index].lastMs = now;
  return index;
}

void captureWifiPacket(void* buffer, wifi_promiscuous_pkt_type_t type) {
  if (!buffer || type == WIFI_PKT_MISC) return;
  const auto* packet = static_cast<wifi_promiscuous_pkt_t*>(buffer);
  const uint8_t* p = packet->payload;
  const uint16_t length = packet->rx_ctrl.sig_len;
  const uint32_t now = millis();
  portENTER_CRITICAL(&packetMux);
  if (type == WIFI_PKT_MGMT) ++packetMgmt;
  else if (type == WIFI_PKT_DATA) ++packetData;
  else if (type == WIFI_PKT_CTRL) ++packetCtrl;
  airBytes += length;
  lastPacketRssi = packet->rx_ctrl.rssi;
  // addr1 receiver / addr2 transmitter, not IP endpoints or application bytes.
  if ((type == WIFI_PKT_DATA || type == WIFI_PKT_MGMT) && length >= 24) {
    int tx = airIndex(p + 10, now), rx = airIndex(p + 4, now, tx);
    if (tx >= 0) {
      auto& d = airDevices[tx];
      d.txBytes += length; ++d.txPackets; d.lastMs = now;
      d.rssi = packet->rx_ctrl.rssi; d.rssiKnown = true; d.channel = packet->rx_ctrl.channel;
      // Only learn a real advertised SSID from a beacon/probe response.
      const uint8_t subtype = p[0] & 0xF0;
      if (type == WIFI_PKT_MGMT && (subtype == 0x80 || subtype == 0x50) && length >= 38) {
        for (size_t pos = 36; pos + 2 <= length - 4;) {
          uint8_t tag = p[pos], size = p[pos + 1]; pos += 2;
          if (pos + size > length - 4) break;
          if (tag == 0 && size > 0 && size <= 32 && p[pos] != 0) {
            memcpy(d.ssid, p + pos, size); d.ssid[size] = 0; break;
          }
          pos += size;
        }
      }
    }
    if (rx >= 0) {
      auto& d = airDevices[rx];
      d.rxBytes += length; ++d.rxPackets; d.lastMs = now; d.channel = packet->rx_ctrl.channel;
    }
  }
  portEXIT_CRITICAL(&packetMux);
}

void broadcastAirDevices() {
  static uint32_t last = 0;
  if (millis() - last < (phoneClient.connected() ? 4000 : 8000)) return;
  last = millis();
  JsonDocument doc; doc["type"] = "traffic"; doc["updated"] = last;
  doc["channel"] = WiFi.channel(); doc["scope"] = "observed_80211_frames";
  doc["tableCapacity"] = MAX_AIR_DEVICES;
  JsonArray devices = doc["devices"].to<JsonArray>();
  for (uint8_t i = 0; i < MAX_AIR_DEVICES; ++i) {
    AirDevice d;
    portENTER_CRITICAL(&packetMux); d = airDevices[i]; portEXIT_CRITICAL(&packetMux);
    if (!d.used || last - d.lastMs > 60000) continue;
    char mac[18];
    snprintf(mac, sizeof(mac), "%02X:%02X:%02X:%02X:%02X:%02X",
        d.mac[0], d.mac[1], d.mac[2], d.mac[3], d.mac[4], d.mac[5]);
    JsonObject item = devices.add<JsonObject>();
    item["mac"] = mac; item["name"] = d.ssid;
    item["txBytes"] = d.txBytes; item["rxBytes"] = d.rxBytes;
    item["txPackets"] = d.txPackets; item["rxPackets"] = d.rxPackets;
    item["firstMs"] = d.firstMs; item["lastMs"] = d.lastMs;
    item["ageMs"] = last - d.lastMs; item["ch"] = d.channel;
    item["rssi"] = d.rssi; item["rssiKnown"] = d.rssiKnown;
  }
  doc["tableEvictions"] = airTableEvictions;
  sendClientJson(doc);
}

void enableWifiMetadataCapture() {
  wifi_promiscuous_filter_t filter = {};
  filter.filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT | WIFI_PROMIS_FILTER_MASK_DATA |
                       WIFI_PROMIS_FILTER_MASK_CTRL;
  esp_wifi_set_promiscuous_filter(&filter);
  esp_wifi_set_promiscuous_rx_cb(captureWifiPacket);
  esp_wifi_set_promiscuous(true);
}

void sendFlowCounters() {
  static uint32_t lastSendMs = 0;
  const uint32_t now = millis();
  const uint32_t windowMs = now - lastSendMs;
  if (windowMs < 500) return;
  lastSendMs = now;
  uint32_t mgmt, data, ctrl;
  uint64_t observedBytes;
  int8_t rssi;
  portENTER_CRITICAL(&packetMux);
  mgmt = packetMgmt; data = packetData; ctrl = packetCtrl; rssi = lastPacketRssi;
  observedBytes = airBytes; airBytes = 0;
  packetMgmt = packetData = packetCtrl = 0;
  portEXIT_CRITICAL(&packetMux);
  JsonDocument doc;
  doc["type"] = "flow";
  doc["mgmt"] = mgmt;
  doc["data"] = data;
  doc["ctrl"] = ctrl;
  doc["total"] = mgmt + data + ctrl;
  doc["bytes"] = observedBytes;
  doc["windowMs"] = windowMs;
  doc["rssi"] = rssi;
  doc["channel"] = WiFi.channel();
  doc["live"] = true;
  sendClientJson(doc);
}

bool initializeRadio() {
  SPI.begin(Pins::SPI_SCK, Pins::SPI_MISO, Pins::SPI_MOSI, Pins::NRF_CSN);
  if (!radio.begin()) return false;
  radio.setAutoAck(false);
  radio.setRetries(0, 0);
  radio.setAddressWidth(5);
  radio.setDataRate(RF24_1MBPS);
  radio.setPALevel(RF24_PA_MIN);
  radio.disableCRC();
  radio.setPayloadSize(32);
  radio.openReadingPipe(1, 0xE7E7E7E7E7ULL);
  radio.maskIRQ(true, true, true);
  radio.setChannel(0);
  radio.startListening();
  return true;
}

void sendAlertEvent(bool active) {
  JsonDocument doc;
  doc["type"] = "alert";
  doc["active"] = active;
  doc["sequence"] = alertSequence;
  doc["avg"] = sweepAverage;
  doc["peak"] = sweepPeak;
  doc["peakMHz"] = 2400 + sweepPeakChannel;
  doc["affected"] = affectedChannels;
  doc["confidence"] = alarmConfidence;
  doc["classification"] = "baseline_wideband_anomaly";
  doc["time"] = millis();
  sendClientJson(doc);
}

void evaluateInterference() {
  uint32_t sum = 0, excessSum = 0;
  uint8_t peak = 0, peakChannel = 0, affected = 0, occupiedRegions = 0, regionHits = 0;
  for (uint8_t channel = 0; channel < Config::NRF_CHANNELS; ++channel) {
    smoothedSpectrum[channel] = sweepSequence <= 1
        ? spectrum[channel]
        : smoothedSpectrum[channel] * 0.58f + spectrum[channel] * 0.42f;
    const uint8_t visible = static_cast<uint8_t>(constrain(
        static_cast<int>(smoothedSpectrum[channel] + 0.5f), 0, 100));
    spectrum[channel] = visible;
    sum += visible;
    if (visible > peak) { peak = visible; peakChannel = channel; }
    if (calibrationSweeps < Config::CALIBRATION_SWEEPS) {
      baseline[channel] = (baseline[channel] * calibrationSweeps + visible) /
                          static_cast<float>(calibrationSweeps + 1);
    } else {
      const float excess = std::max(0.0f, smoothedSpectrum[channel] - baseline[channel]);
      excessSum += static_cast<uint32_t>(excess);
      if (excess >= settings.excessThreshold) { ++affected; ++regionHits; }
      if ((channel + 1) % 14 == 0 || channel == Config::NRF_CHANNELS - 1) {
        if (regionHits >= 2) ++occupiedRegions;
        regionHits = 0;
      }
    }
  }
  sweepAverage = static_cast<uint8_t>(sum / Config::NRF_CHANNELS);
  sweepPeak = peak;
  sweepPeakChannel = peakChannel;

  if (calibrationSweeps < Config::CALIBRATION_SWEEPS) {
    ++calibrationSweeps;
    affectedChannels = alarmConfidence = 0;
    interferenceCandidate = false;
    if (calibrationSweeps % 5 == 0) {
      Serial.printf("[RF] Calibration %u%%\n", calibrationPercent());
    }
    if (calibrationSweeps == Config::CALIBRATION_SWEEPS) {
      Serial.println("[RF] Calibration complete; adaptive baseline active");
      sendStatus(true);
    }
    return;
  }

  affectedChannels = affected;
  const uint8_t averageExcess = static_cast<uint8_t>(excessSum / Config::NRF_CHANNELS);
  const bool broadBand = occupiedRegions >= 5;
  interferenceCandidate = settings.alertsEnabled && broadBand &&
      affected >= settings.minimumAffectedChannels && averageExcess >= 10;
  alarmConfidence = static_cast<uint8_t>(constrain(
      35 + affected * 45 / std::max<uint8_t>(1, settings.minimumAffectedChannels) +
      occupiedRegions * 3 + averageExcess, 0, 100));

  const uint32_t now = millis();
  if (interferenceCandidate) {
    clearSinceMs = 0;
    if (candidateSinceMs == 0) candidateSinceMs = now;
    if (!interferenceAlarm && now - candidateSinceMs >= settings.holdTimeMs &&
        (lastAlarmMs == 0 || now - lastAlarmMs >= settings.cooldownMs)) {
      interferenceAlarm = true;
      lastAlarmMs = now;
      ++alertSequence;
      sendAlertEvent(true);
      Serial.printf("[ALARM] Baseline-wide anomaly: %u channels, %u regions, confidence %u%%\n",
                    affected, occupiedRegions, alarmConfidence);
    }
  } else {
    candidateSinceMs = 0;
    for (uint8_t channel = 0; channel < Config::NRF_CHANNELS; ++channel) {
      const float alpha = smoothedSpectrum[channel] < baseline[channel] ? 0.035f : 0.0025f;
      baseline[channel] += (smoothedSpectrum[channel] - baseline[channel]) * alpha;
    }
    if (interferenceAlarm) {
      if (clearSinceMs == 0) clearSinceMs = now;
      if (now - clearSinceMs >= 2500) {
        interferenceAlarm = false;
        clearSinceMs = 0;
        sendAlertEvent(false);
      }
    }
  }
}

void sendSpectrumPacket() {
  uint8_t packet[Config::SPECTRUM_PACKET_SIZE] = {};
  packet[0] = 'S'; packet[1] = 'P'; packet[2] = '2'; packet[3] = '4'; packet[4] = 2;
  packet[5] = (radioReady ? 0x01 : 0x00) |
      (interferenceCandidate ? 0x02 : 0x00) |
      (interferenceAlarm ? 0x04 : 0x00) |
      (calibrationSweeps < Config::CALIBRATION_SWEEPS ? 0x08 : 0x00) |
      (WiFi.status() == WL_CONNECTED ? 0x10 : 0x00) |
      (bleClientConnected ? 0x20 : 0x00);
  packet[6] = sweepSequence & 0xFF; packet[7] = (sweepSequence >> 8) & 0xFF;
  const uint32_t uptime = millis();
  packet[8] = uptime & 0xFF; packet[9] = (uptime >> 8) & 0xFF;
  packet[10] = (uptime >> 16) & 0xFF; packet[11] = (uptime >> 24) & 0xFF;
  packet[12] = sweepAverage; packet[13] = sweepPeak;
  packet[14] = sweepPeakChannel; packet[15] = affectedChannels;
  memcpy(packet + 16, spectrum, Config::NRF_CHANNELS);
  const uint16_t checksum = crc16Ccitt(packet, Config::SPECTRUM_PACKET_SIZE - 2);
  packet[142] = checksum & 0xFF; packet[143] = (checksum >> 8) & 0xFF;

  if (phoneClient && phoneClient.connected()) {
    spectrumUdp.beginPacket(phoneClient.remoteIP(), Config::SPECTRUM_PORT);
    spectrumUdp.write(packet, sizeof(packet));
    if (!spectrumUdp.endPacket()) ++udpSendErrors;
  }
  // Keep a low-rate BLE backup when Wi-Fi is active, without duplicating the full stream.
  static uint32_t lastBleSpectrum = 0;
  if (!phoneClient.connected() || millis() - lastBleSpectrum >= 1000) {
    sendBleFragments(bleSpectrumCharacteristic, 'S', packet, sizeof(packet));
    lastBleSpectrum = millis();
  }
}

void spectrumTask(void*) {
  RawSweep frame{};
  uint32_t number = 0;
  uint32_t previousStart = 0;
  for (;;) {
    TickType_t cycleStart = xTaskGetTickCount();
    const uint32_t start = micros();
    const uint8_t samples = scanSamples;
    for (uint8_t channel = 0; channel < Config::NRF_CHANNELS; ++channel) {
      digitalWrite(Pins::NRF_CE, LOW);
      radio.setChannel(channel);
      uint8_t hits = 0;
      for (uint8_t sample = 0; sample < samples; ++sample) {
        // Keep PRIM_RX set. CE low/high restarts RX and resets the RPD latch.
        digitalWrite(Pins::NRF_CE, HIGH);
        delayMicroseconds(Config::SAMPLE_DWELL_US); // 130 us RX settling + >=40 us RPD
        digitalWrite(Pins::NRF_CE, LOW);
        if (radio.testRPD()) ++hits;
      }
      frame.values[channel] = (hits * 100U + samples / 2U) / samples;
      // Yield once per 16 channels (~12 ms at four samples); keep RF dwell intact.
      if ((channel & 15) == 15) vTaskDelay(1);
    }
    frame.durationUs = number ? start - previousStart : micros() - start;
    previousStart = start; // Report actual cadence, including scheduling delays.
    frame.number = ++number;
    xQueueOverwrite(sweepQueue, &frame); // Latest complete measurement, no growing backlog.
    vTaskDelayUntil(&cycleStart, pdMS_TO_TICKS(Config::SPECTRUM_PERIOD_MS));
  }
}

void runSpectrumScanner() {
  if (!radioReady || sweepQueue == nullptr) return;
  RawSweep frame;
  if (xQueueReceive(sweepQueue, &frame, 0) != pdTRUE) return;
  memcpy(spectrum, frame.values, sizeof(spectrum));
  if (sweepSequence != 0) skippedSweeps += frame.number - sweepSequence - 1;
  sweepSequence = frame.number; sweepDurationUs = frame.durationUs;
  evaluateInterference();
  sendSpectrumPacket();
  static uint32_t lastReport = 0;
  if (millis() - lastReport > 10000) {
    lastReport = millis();
    Serial.printf("[RF] %.1f sweeps/s | %u samples/channel | frame %lu\n",
        1000000.0 / sweepDurationUs, scanSamples, static_cast<unsigned long>(sweepSequence));
  }
}

void broadcastWifiNetworks() {
  JsonDocument doc;
  doc["type"] = "wifi";
  doc["live"] = true;
  doc["updated"] = millis();
  JsonArray networks = doc["networks"].to<JsonArray>();
  for (const auto& item : wifiNetworks) {
    JsonObject network = networks.add<JsonObject>();
    String observedName = item.ssid;
    if (observedName.isEmpty()) {
      for (uint8_t i = 0; i < MAX_AIR_DEVICES; ++i) {
        AirDevice d;
        portENTER_CRITICAL(&packetMux); d = airDevices[i]; portEXIT_CRITICAL(&packetMux);
        char mac[18];
        snprintf(mac, sizeof(mac), "%02X:%02X:%02X:%02X:%02X:%02X",
            d.mac[0], d.mac[1], d.mac[2], d.mac[3], d.mac[4], d.mac[5]);
        if (d.used && item.bssid.equalsIgnoreCase(mac) && d.ssid[0]) observedName = d.ssid;
      }
    }
    network["ssid"] = observedName;
    network["nameSource"] = item.ssid.isEmpty() ? (observedName.isEmpty() ? "not_advertised" : "observed_frame") : "scan";
    network["bssid"] = item.bssid;
    network["rssi"] = item.rssi;
    network["ch"] = item.channel;
    network["security"] = item.security;
  }
  sendClientJson(doc);
}

void serviceWifiScanner() {
  // Return to the phone's channel between short passive inventory scans.
  const uint32_t now = millis();
  static uint8_t apClients = 0;
  const uint8_t clients = WiFi.softAPgetStationNum();
  if (clients > apClients) connectionActivityMs = now;
  apClients = clients;
  const int16_t result = WiFi.scanComplete();
  if (wifiScanRunning && result != WIFI_SCAN_RUNNING) {
    if (result >= 0) {
      for (int i = 0; i < result; ++i) {
        WifiNetwork found{WiFi.SSID(i), WiFi.BSSIDstr(i),
            securityName(WiFi.encryptionType(i)), WiFi.RSSI(i), WiFi.channel(i)};
        auto existing = std::find_if(wifiScanResults.begin(), wifiScanResults.end(),
            [&](const WifiNetwork& value) { return value.bssid == found.bssid; });
        if (existing != wifiScanResults.end()) *existing = found;
        else wifiScanResults.push_back(found);
        std::sort(wifiScanResults.begin(), wifiScanResults.end(),
            [](const WifiNetwork& x, const WifiNetwork& y) { return x.rssi > y.rssi; });
        if (wifiScanResults.size() > Config::MAX_WIFI_RESULTS) wifiScanResults.pop_back();
      }
    }
    WiFi.scanDelete(); wifiScanRunning = false; wifiChannelDoneMs = now;
    enableWifiMetadataCapture();
    if (++wifiScanChannel > wifiScanLastChannel) {
      wifiNetworks.swap(wifiScanResults); wifiScanResults.clear();
      wifiScanChannel = 0; lastWifiScanMs = now;
      broadcastWifiNetworks();
      Serial.printf("[WiFi] %u live access points (paced scan)\n", static_cast<unsigned>(wifiNetworks.size()));
    }
  }
  if (wifiScanRunning || bleScanRunning || now - connectionActivityMs < Config::CONNECTION_GRACE_MS) return;
  if (!wifiScanChannel) {
    if (now - lastWifiScanMs < Config::WIFI_SCAN_INTERVAL_MS) return;
    wifi_country_t country{};
    wifiScanChannel = 1; wifiScanLastChannel = 11;
    if (esp_wifi_get_country(&country) == ESP_OK && country.nchan) {
      wifiScanChannel = country.schan;
      wifiScanLastChannel = std::min<uint8_t>(14, country.schan + country.nchan - 1);
    }
    wifiScanResults.clear();
  }
  if (now - wifiChannelDoneMs < Config::WIFI_CHANNEL_REST_MS) return;
  // Covers a normal beacon interval without seconds-long off-channel pauses.
  int16_t state = WiFi.scanNetworks(true, true, true, 120, wifiScanChannel);
  wifiScanRunning = state == WIFI_SCAN_RUNNING || state >= 0;
  if (!wifiScanRunning) {
    wifiScanChannel = 0; lastWifiScanMs = now; wifiScanResults.clear();
  }
}

class NearbyBleCallbacks final : public NimBLEAdvertisedDeviceCallbacks {
 public:
  void onResult(NimBLEAdvertisedDevice* device) override {
    if (device->isAdvertisingService(NimBLEUUID(Config::BLE_SERVICE_UUID))) return;
    const std::string address = device->getAddress().toString();
    const std::string name = device->haveName() ? device->getName() : "";
    std::string serviceText;
    for (uint8_t i = 0; i < device->getServiceUUIDCount() && i < 4; ++i) {
      if (i) serviceText += ",";
      serviceText += device->getServiceUUID(i).toString();
    }
    uint16_t maker = 0;
    char prefix[9] = {};
    uint8_t manufacturerLength = 0;
    if (device->haveManufacturerData()) {
      std::string data = device->getManufacturerData();
      if (data.size() >= 2) maker = static_cast<uint8_t>(data[0]) | (static_cast<uint8_t>(data[1]) << 8);
      manufacturerLength = std::min<size_t>(255, data.size());
      // Only the format header; do not forward Find My keys or beacon identifiers.
      for (size_t i = 0; i < std::min<size_t>(4, data.size()); ++i)
        snprintf(prefix + i * 2, sizeof(prefix) - i * 2, "%02x", static_cast<uint8_t>(data[i]));
    }
    const uint32_t now = millis();
    portENTER_CRITICAL(&bleMux);
    int found = -1;
    for (uint8_t i = 0; i < bleDeviceCount; ++i) {
      if (strncmp(bleDevices[i].mac, address.c_str(), sizeof(bleDevices[i].mac)) == 0) {
        found = i; break;
      }
    }
    if (found < 0 && bleDeviceCount < Config::MAX_BLE_RESULTS) {
      found = bleDeviceCount++;
      bleDevices[found] = BleDeviceRecord{};
      strlcpy(bleDevices[found].mac, address.c_str(), sizeof(bleDevices[found].mac));
    }
    if (found >= 0) {
      auto& record = bleDevices[found];
      if (!name.empty()) strlcpy(record.name, name.c_str(), sizeof(record.name));
      record.rssi = device->getRSSI();
      if (manufacturerLength >= 2) {
        record.manufacturer = maker;
        strlcpy(record.mfgPrefix, prefix, sizeof(record.mfgPrefix));
        record.mfgLength = manufacturerLength;
        record.signatureSeenMs = now;
      }
      if (device->haveAppearance()) record.appearance = device->getAppearance();
      if (!serviceText.empty()) strlcpy(record.services, serviceText.c_str(), sizeof(record.services));
      record.lastSeenMs = now;
      ++record.advertisements;
    }
    portEXIT_CRITICAL(&bleMux);
  }
};

class BleServerCallbacks final : public NimBLEServerCallbacks {
 public:
  void onConnect(NimBLEServer*, ble_gap_conn_desc* desc) override {
    bleClientConnected = true;
    if (bleServer != nullptr) bleServer->updateConnParams(desc->conn_handle, 12, 24, 0, 100);
    Serial.println("[BLE] App connected");
  }
  void onDisconnect(NimBLEServer*, ble_gap_conn_desc*) override {
    bleClientConnected = false;
    Serial.println("[BLE] App disconnected");
  }
};

class BleControlCallbacks final : public NimBLECharacteristicCallbacks {
 public:
  void onWrite(NimBLECharacteristic* characteristic) override {
    if (bleCommandQueue == nullptr) return;
    const std::string value = characteristic->getValue();
    BleCommand command;
    const size_t count = std::min(value.length(), sizeof(command.text) - 1);
    memcpy(command.text, value.data(), count);
    command.text[count] = '\0';
    xQueueSend(bleCommandQueue, &command, 0);
  }
};

NearbyBleCallbacks nearbyBleCallbacks;
BleServerCallbacks bleServerCallbacks;
BleControlCallbacks bleControlCallbacks;

void bleScanFinished(NimBLEScanResults) {
  bleScanRunning = false;
  lastBleScanMs = millis();
}

void initializeBle() {
  NimBLEDevice::init("SPECTRA-24");
  NimBLEDevice::setPower(ESP_PWR_LVL_P3);
  NimBLEDevice::setMTU(247);
  bleCommandQueue = xQueueCreate(5, sizeof(BleCommand));
  bleServer = NimBLEDevice::createServer();
  bleServer->setCallbacks(&bleServerCallbacks, false);
  bleServer->advertiseOnDisconnect(true);
  NimBLEService* service = bleServer->createService(Config::BLE_SERVICE_UUID);
  bleStatusCharacteristic = service->createCharacteristic(
      Config::BLE_STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY, 512);
  NimBLECharacteristic* control = service->createCharacteristic(
      Config::BLE_CONTROL_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR, 384);
  bleSpectrumCharacteristic = service->createCharacteristic(
      Config::BLE_SPECTRUM_UUID, NIMBLE_PROPERTY::NOTIFY, 184);
  bleEventCharacteristic = service->createCharacteristic(
      Config::BLE_EVENT_UUID, NIMBLE_PROPERTY::NOTIFY, 184);
  control->setCallbacks(&bleControlCallbacks);
  service->start();
  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(Config::BLE_SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x12);
  advertising->setMaxPreferred(0x24);
  advertising->start();

  bleScanner = NimBLEDevice::getScan();
  bleScanner->setAdvertisedDeviceCallbacks(&nearbyBleCallbacks, true);
  bleScanner->setActiveScan(false);
  bleScanner->setInterval(96);
  bleScanner->setWindow(32);
}

void broadcastBleDevices() {
  static BleDeviceRecord snapshot[Config::MAX_BLE_RESULTS];
  uint8_t snapshotCount = 0;
  const uint32_t now = millis();
  portENTER_CRITICAL(&bleMux);
  uint8_t writeIndex = 0;
  for (uint8_t i = 0; i < bleDeviceCount; ++i) {
    if (now - bleDevices[i].lastSeenMs <= Config::BLE_DEVICE_TTL_MS) {
      if (writeIndex != i) bleDevices[writeIndex] = bleDevices[i];
      snapshot[snapshotCount++] = bleDevices[writeIndex++];
    }
  }
  bleDeviceCount = writeIndex;
  portEXIT_CRITICAL(&bleMux);
  std::sort(snapshot, snapshot + snapshotCount,
            [](const BleDeviceRecord& a, const BleDeviceRecord& b) { return a.rssi > b.rssi; });
  JsonDocument doc;
  doc["type"] = "ble";
  doc["live"] = true;
  doc["updated"] = millis();
  JsonArray devices = doc["devices"].to<JsonArray>();
  for (uint8_t i = 0; i < snapshotCount; ++i) {
    JsonObject device = devices.add<JsonObject>();
    device["mac"] = snapshot[i].mac;
    device["name"] = snapshot[i].name;
    device["rssi"] = snapshot[i].rssi;
    device["packets"] = snapshot[i].advertisements;
    device["services"] = snapshot[i].services;
    device["manufacturer"] = snapshot[i].manufacturer;
    device["mfgPrefix"] = snapshot[i].mfgPrefix;
    device["mfgLength"] = snapshot[i].mfgLength;
    device["signatureAgeMs"] = now - snapshot[i].signatureSeenMs;
    device["ageMs"] = now - snapshot[i].lastSeenMs;
    device["appearance"] = snapshot[i].appearance;
  }
  sendClientJson(doc);
}

void serviceBleScanner() {
  static uint32_t lastBroadcastMs = 0;
  const uint32_t now = millis();
  if (bleScanner != nullptr && !bleScanRunning && !wifiScanRunning && !wifiScanChannel &&
      now - connectionActivityMs >= Config::CONNECTION_GRACE_MS &&
      (nameScanPending || now - lastBleScanMs >= Config::BLE_SCAN_INTERVAL_MS)) {
    bleScanner->clearResults();
    bleScanner->setActiveScan(nameScanPending);
    nameScanPending = false;
    bleScanRunning = bleScanner->start(4, bleScanFinished, false);
    if (!bleScanRunning) lastBleScanMs = now;
  }
  if (now - lastBroadcastMs >= 4000) {
    lastBroadcastMs = now;
    broadcastBleDevices();
  }
}

void serviceStation() {
  const wl_status_t current = WiFi.status();
  if (current != previousStationStatus) {
    previousStationStatus = current;
    if (current == WL_CONNECTED) {
      Serial.printf("[WiFi] Hotspot/LAN connected: %s @ %s\n", WiFi.SSID().c_str(),
                    WiFi.localIP().toString().c_str());
      resetCalibration();
    }
    sendStatus(true);
  }
  if (current != WL_CONNECTED && !stationSsid.isEmpty() &&
      millis() - lastStationAttemptMs >= 30000) {
    connectStation(stationSsid, stationPassword, false);
  }
}

void printStartupBanner() {
  Serial.println();
  Serial.println("================================================");
  Serial.println(" SPECTRA-24 | Live 2.4 GHz Network Field Tool");
  Serial.println("================================================");
  Serial.printf(" Direct Wi-Fi : %s / %s / %s\n", Config::AP_SSID, Config::AP_PASSWORD,
                WiFi.softAPIP().toString().c_str());
  Serial.printf(" BLE service  : %s\n", Config::BLE_SERVICE_UUID);
  Serial.printf(" TCP/UDP      : %u / %u | discovery %u\n", Config::CONTROL_PORT,
                Config::SPECTRUM_PORT, Config::DISCOVERY_PORT);
  Serial.printf(" nRF24        : %s | calibration: %u sweeps\n",
                radioReady ? "READY" : "NOT FOUND", Config::CALIBRATION_SWEEPS);
  Serial.println(" HTTP          : DISABLED");
  Serial.println("================================================");
}

void setup() {
  pinMode(Pins::STATUS_LED, OUTPUT);
  digitalWrite(Pins::STATUS_LED, LOW);
  Serial.begin(115200);
  delay(250);
  loadSettings();
  radioReady = initializeRadio();
  initializeBle();

  WiFi.persistent(false);
  WiFi.mode(WIFI_AP_STA);
  // Required for Wi-Fi/BLE coexistence on this ESP32 + Arduino 2.0.17 stack.
  // Arduino returns false even when the requested mode is already selected.
  WiFi.setSleep(WIFI_PS_MIN_MODEM);
  wifi_ps_type_t powerPolicy = WIFI_PS_NONE;
  wifiPowerSave = esp_wifi_get_ps(&powerPolicy) == ESP_OK && powerPolicy == WIFI_PS_MIN_MODEM;
  if (!wifiPowerSave) Serial.println("[WiFi] ERROR: coexistence power-save policy not active");
  WiFi.softAPConfig(IPAddress(192, 168, 4, 1), IPAddress(192, 168, 4, 1),
                    IPAddress(255, 255, 255, 0));
  WiFi.softAP(Config::AP_SSID, Config::AP_PASSWORD, Config::AP_CHANNEL, false, 4);
  if (!stationSsid.isEmpty()) connectStation(stationSsid, stationPassword, false);
  controlServer.begin();
  controlServer.setNoDelay(true);
  spectrumUdp.begin(Config::SPECTRUM_PORT);
  discoveryUdp.begin(Config::DISCOVERY_PORT);
  enableWifiMetadataCapture();

  lastWifiScanMs = millis() - Config::WIFI_SCAN_INTERVAL_MS + 3500;
  lastBleScanMs = millis() - Config::BLE_SCAN_INTERVAL_MS + 6000;
  controlInput.reserve(1024);
  sendStatus(false);
  printStartupBanner();
  sweepQueue = xQueueCreate(1, sizeof(RawSweep));
  if (radioReady && sweepQueue) xTaskCreatePinnedToCore(spectrumTask, "rf-sampler", 4096, nullptr, 1, nullptr, 1);
}

void loop() {
  serviceControlConnection();
  // Keep WIFI_PS_MIN_MODEM: BLE and Wi-Fi share the ESP32 radio.
  // Never switch to WIFI_PS_NONE when a TCP client connects.
  serviceBleCommands();
  serviceBleOutput();
  serviceDiscovery();
  serviceStation();
  runSpectrumScanner();
  serviceWifiScanner();
  serviceBleScanner();
  sendFlowCounters();
  broadcastAirDevices();

  static uint32_t lastStatusMs = 0;
  if (millis() - lastStatusMs >= 3000) {
    lastStatusMs = millis();
    sendStatus(true);
  }
  static uint32_t lastLedMs = 0;
  static bool led = false;
  const uint32_t interval = calibrationSweeps < Config::CALIBRATION_SWEEPS
      ? 220 : interferenceAlarm ? 100 : 1000;
  if (millis() - lastLedMs >= interval) {
    lastLedMs = millis();
    led = !led;
    digitalWrite(Pins::STATUS_LED, led);
  }
  delay(1);
}
