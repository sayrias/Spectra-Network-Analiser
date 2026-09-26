#include "local_panel.h"

#include <TFT_eSPI.h>
#include <algorithm>
#include <cstring>

namespace LocalPanel {
namespace {
constexpr uint8_t MODE_BUTTON = 32;
constexpr uint8_t BUZZER = 25;
constexpr uint8_t BUZZER_CHANNEL = 6;
constexpr uint16_t WIDTH = 240;
constexpr uint16_t HEIGHT = 240;
constexpr uint32_t RENDER_INTERVAL_MS = 250;
constexpr uint8_t MODE_COUNT = 6;
constexpr uint16_t WATERFALL_X = 6;
constexpr uint16_t WATERFALL_Y = 39;
constexpr uint16_t WATERFALL_WIDTH = 228;
constexpr uint16_t WATERFALL_HEIGHT = 145;
constexpr uint8_t SPECTRUM_CHANNELS = 126;

constexpr uint16_t COLOR_BG = 0x0000;
constexpr uint16_t COLOR_PANEL = 0x1082;
constexpr uint16_t COLOR_GRID = 0x2104;
constexpr uint16_t COLOR_TEXT = 0xFF9C;
constexpr uint16_t COLOR_MUTED = 0x8410;
constexpr uint16_t COLOR_ORANGE = 0xFC20;
constexpr uint16_t COLOR_AMBER = 0xFD20;
constexpr uint16_t COLOR_RED = 0xF904;
constexpr uint16_t COLOR_CYAN = 0x07FF;
constexpr uint16_t COLOR_GREEN = 0x37EC;

TFT_eSPI panel = TFT_eSPI(WIDTH, HEIGHT);
bool panelReady = false;
uint8_t mode = 0;
bool buttonStable = HIGH;
bool buttonSample = HIGH;
uint32_t buttonChangedMs = 0;
uint32_t buttonPressedMs = 0;
uint32_t lastRenderMs = 0;
bool forceRedraw = true;
uint8_t waterfallHistory[WATERFALL_HEIGHT][SPECTRUM_CHANNELS] = {};
uint16_t waterfallRows = 0;
uint32_t lastWaterfallSequence = UINT32_MAX;
uint8_t lastDrawnSpectrum[SPECTRUM_CHANNELS] = {};
uint8_t lastDrawnPeakChannel = 0;
uint8_t lastDrawnPeak = 0;
bool spectrumDrawn = false;
bool phoneRadioToggleRequested = false;

bool buzzerEnabled = true;
uint8_t buzzerProfile = 1;
uint8_t buzzerLevel = 65;
uint32_t muteUntilMs = 0;
uint32_t testUntilMs = 0;
uint32_t finderResetUntilMs = 0;
float finderBaseline = -1;
float finderPeak = 0;

void toneOff() {
  ledcWrite(BUZZER_CHANNEL, 0);
  ledcWriteTone(BUZZER_CHANNEL, 0);
}

void toneOn(uint16_t frequency) {
  if (buzzerLevel == 0) {
    toneOff();
    return;
  }
  ledcWriteTone(BUZZER_CHANNEL, frequency);
  // 8-bit LEDC'de 128 tam %50 duty'dir ve pasif piezo icin en yuksek
  // AC salinimini verir. Onceki 8..120 eslemesi orta seviyeleri gereksiz
  // kisiyordu; yeni egri 70 ve uzerinde guvenli tepe surusune ulasir.
  const uint8_t duty = static_cast<uint8_t>(constrain(
      70 + static_cast<int>(buzzerLevel) * 58 / 70, 70, 128));
  ledcWrite(BUZZER_CHANNEL, duty);
}

uint16_t profileFrequency(AlertLevel level, uint8_t step) {
  // Bu cihazdaki piezo derin profilde daha yuksek ses verdigi icin tum
  // profiller rezonansa yakin tutulur; karakteri ritim ve kucuk gecisler ayirir.
  static constexpr uint16_t frequencies[3][3] = {
      {950, 1350, 1800}, {1400, 1700, 1950}, {1650, 1900, 2150}};
  static constexpr uint16_t stepOffsets[3] = {0, 120, 240};
  uint8_t row = constrain(buzzerProfile, 0, 2);
  uint8_t column = constrain(static_cast<int>(level) - 1, 0, 2);
  return frequencies[row][column] + stepOffsets[std::min<uint8_t>(step, 2)];
}

void serviceBuzzer(AlertLevel level) {
  const uint32_t now = millis();
  if (!buzzerEnabled || (muteUntilMs && static_cast<int32_t>(muteUntilMs - now) > 0)) {
    toneOff();
    return;
  }
  if (static_cast<int32_t>(testUntilMs - now) > 0) level = AlertLevel::Extreme;
  if (level == AlertLevel::Normal) {
    toneOff();
    return;
  }
  const uint32_t phase = level == AlertLevel::Medium ? now % 1450
      : level == AlertLevel::High ? now % 760 : now % 500;
  bool on = false;
  uint8_t toneStep = 0;
  if (level == AlertLevel::Medium) {
    on = phase < 250;
  } else if (level == AlertLevel::High) {
    on = phase < 190 || (phase >= 245 && phase < 445);
    toneStep = phase >= 245 ? 1 : 0;
  } else {
    on = phase < 125 || (phase >= 160 && phase < 290) ||
         (phase >= 325 && phase < 470);
    toneStep = phase >= 325 ? 2 : phase >= 160 ? 1 : 0;
  }
  if (on) toneOn(profileFrequency(level, toneStep)); else toneOff();
}

void printAt(int16_t x, int16_t y, const char* value, uint8_t size = 1,
             uint16_t color = COLOR_TEXT) {
  panel.setCursor(x, y);
  panel.setTextSize(size);
  panel.setTextColor(color);
  panel.print(value);
}

void printNumber(int16_t x, int16_t y, uint32_t value, uint8_t size = 1,
                 uint16_t color = COLOR_TEXT) {
  panel.setCursor(x, y);
  panel.setTextSize(size);
  panel.setTextColor(color);
  panel.print(value);
}

void printAtOpaque(int16_t x, int16_t y, const char* value, uint8_t size,
                   uint16_t color, uint16_t background) {
  panel.setCursor(x, y);
  panel.setTextSize(size);
  panel.setTextColor(color, background);
  panel.print(value);
}

void drawHeader(const char* title, uint8_t page, const Snapshot& snapshot, bool full) {
  if (!full) return;
  panel.fillRect(0, 0, WIDTH, 35, COLOR_PANEL);
  panel.fillRoundRect(6, 4, 4, 18, 2, COLOR_ORANGE);
  printAtOpaque(16, 5, title, 2, COLOR_TEXT, COLOR_PANEL);
  printAtOpaque(198, 4, "RF", 1, COLOR_MUTED, COLOR_PANEL);
  panel.fillCircle(228, 9, 5, snapshot.radioReady ? COLOR_GREEN : COLOR_RED);
  printNumber(207, 17, page + 1, 1, COLOR_ORANGE);
  printAtOpaque(214, 17, "/6", 1, COLOR_MUTED, COLOR_PANEL);
  for (uint8_t i = 0; i < MODE_COUNT; ++i) {
    const int16_t tabX = 8 + i * 38;
    panel.drawRoundRect(tabX, 26, 30, 7, 2, COLOR_GRID);
    if (i == page) panel.fillRoundRect(tabX + 1, 27, 28, 5, 1, COLOR_ORANGE);
  }
}

void drawFooter(const Snapshot& snapshot, bool full) {
  if (!full) return;
  panel.fillRect(0, 218, WIDTH, 22, COLOR_PANEL);
  printAt(7, 224, snapshot.phoneConnected ? "APP BAGLI" : "BAGIMSIZ MOD", 1,
          snapshot.phoneConnected ? COLOR_GREEN : COLOR_MUTED);
  printAt(174, 224, "BTN: MOD", 1, COLOR_MUTED);
}

uint16_t heatColor(uint8_t level) {
  if (level == 0) return 0x0001;
  if (level < 18) return static_cast<uint16_t>(0x0004 + (level * 0x001B / 18));
  if (level < 42) {
    const uint8_t step = level - 18;
    const uint16_t green = static_cast<uint16_t>(step * 50 / 24);
    return static_cast<uint16_t>((green << 5) | 0x001F);
  }
  if (level < 68) {
    const uint8_t step = level - 42;
    const uint16_t red = static_cast<uint16_t>(step * 31 / 26);
    return static_cast<uint16_t>((red << 11) | 0x07E0 | (31 - step * 18 / 26));
  }
  const uint8_t step = level - 68;
  const uint16_t green = static_cast<uint16_t>(63 - step * 58 / 32);
  return static_cast<uint16_t>(0xF800 | (green << 5));
}

void captureWaterfall(const Snapshot& snapshot) {
  if (!snapshot.spectrum || snapshot.sweepSequence == lastWaterfallSequence) return;
  lastWaterfallSequence = snapshot.sweepSequence;
  std::memmove(waterfallHistory[1], waterfallHistory[0],
               sizeof(waterfallHistory[0]) * (WATERFALL_HEIGHT - 1));
  std::memcpy(waterfallHistory[0], snapshot.spectrum, SPECTRUM_CHANNELS);
  if (waterfallRows < WATERFALL_HEIGHT) ++waterfallRows;
}

void drawWaterfall(const Snapshot& snapshot, bool full) {
  drawHeader("WATERFALL", 0, snapshot, full);
  if (full) panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
  uint16_t rowPixels[WATERFALL_WIDTH];
  for (uint16_t row = 0; row < WATERFALL_HEIGHT; ++row) {
    for (uint16_t x = 0; x < WATERFALL_WIDTH; ++x) {
      const uint8_t channel = static_cast<uint8_t>(x * 125 / (WATERFALL_WIDTH - 1));
      rowPixels[x] = heatColor(waterfallHistory[row][channel]);
    }
    panel.pushImage(WATERFALL_X, WATERFALL_Y + row, WATERFALL_WIDTH, 1, rowPixels);
  }
  if (full) {
    panel.drawRect(WATERFALL_X - 1, WATERFALL_Y - 1, WATERFALL_WIDTH + 2,
                   WATERFALL_HEIGHT + 2, COLOR_GRID);
    printAt(6, 188, "2400", 1, COLOR_MUTED);
    printAt(104, 188, "2463", 1, COLOR_MUTED);
    printAt(208, 188, "2525", 1, COLOR_MUTED);
    panel.fillTriangle(224, 40, 231, 40, 228, 46, COLOR_GREEN);
  }
  panel.fillRect(6, 198, 198, 17, COLOR_BG);
  printAt(7, 203, "ORT", 1, COLOR_MUTED);
  printNumber(31, 199, snapshot.average, 2, COLOR_CYAN);
  printAt(69, 203, "TEPE", 1, COLOR_MUTED);
  printNumber(104, 199, 2400 + snapshot.peakChannel, 2, COLOR_ORANGE);
  printAt(169, 203, "MHz", 1, COLOR_MUTED);
  drawFooter(snapshot, full);
}

void drawSpectrum(const Snapshot& snapshot, bool full) {
  drawHeader("RF SPEKTRUM", 1, snapshot, full);
  if (full) panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
  const int16_t left = 7, top = 48, graphWidth = 226, graphHeight = 116;
  if (full || !spectrumDrawn) {
    panel.fillRect(left, top, graphWidth, graphHeight, COLOR_BG);
  } else {
    int16_t oldX = left;
    int16_t oldY = top + graphHeight - lastDrawnSpectrum[0] * graphHeight / 100;
    for (uint8_t channel = 1; channel < SPECTRUM_CHANNELS; ++channel) {
      const int16_t x = left + channel * (graphWidth - 1) / 125;
      const int16_t y = top + graphHeight - lastDrawnSpectrum[channel] * graphHeight / 100;
      panel.drawLine(oldX, oldY, x, y, COLOR_BG);
      oldX = x;
      oldY = y;
    }
    const int16_t oldPeakX = left + lastDrawnPeakChannel * (graphWidth - 1) / 125;
    const int16_t oldPeakY = top + graphHeight - lastDrawnPeak * graphHeight / 100;
    panel.fillCircle(oldPeakX, oldPeakY, 6, COLOR_BG);
  }
  for (uint8_t i = 0; i <= 4; ++i)
    panel.drawFastHLine(left, top + i * graphHeight / 4, graphWidth, COLOR_GRID);
  for (uint8_t i = 0; i <= 5; ++i)
    panel.drawFastVLine(left + i * graphWidth / 5, top, graphHeight, COLOR_GRID);
  if (snapshot.spectrum) {
    int16_t previousX = left;
    int16_t previousY = top + graphHeight - snapshot.spectrum[0] * graphHeight / 100;
    for (uint8_t channel = 1; channel < 126; ++channel) {
      const int16_t x = left + channel * (graphWidth - 1) / 125;
      const int16_t y = top + graphHeight - snapshot.spectrum[channel] * graphHeight / 100;
      const uint16_t color = snapshot.spectrum[channel] > 70 ? COLOR_RED
          : snapshot.spectrum[channel] > 35 ? COLOR_AMBER : COLOR_CYAN;
      panel.drawLine(previousX, previousY, x, y, color);
      previousX = x; previousY = y;
    }
    const int16_t peakX = left + snapshot.peakChannel * (graphWidth - 1) / 125;
    const int16_t peakY = top + graphHeight - snapshot.peak * graphHeight / 100;
    panel.drawFastVLine(peakX, top, graphHeight, 0x4208);
    panel.fillCircle(peakX, peakY, 4, COLOR_RED);
    panel.drawCircle(peakX, peakY, 5, COLOR_TEXT);
    std::memcpy(lastDrawnSpectrum, snapshot.spectrum, SPECTRUM_CHANNELS);
    lastDrawnPeakChannel = snapshot.peakChannel;
    lastDrawnPeak = snapshot.peak;
    spectrumDrawn = true;
  }
  if (full) {
    printAt(7, 169, "2400", 1, COLOR_MUTED);
    printAt(103, 169, "2463", 1, COLOR_MUTED);
    printAt(207, 169, "2525", 1, COLOR_MUTED);
  }
  panel.fillRect(7, 181, 226, 35, COLOR_BG);
  printAt(8, 188, "ORT", 1, COLOR_MUTED); printNumber(38, 184, snapshot.average, 2, COLOR_TEXT);
  printAt(84, 188, "TEPE", 1, COLOR_MUTED); printNumber(119, 184, 2400 + snapshot.peakChannel, 2, COLOR_ORANGE);
  printAt(184, 188, "MHz", 1, COLOR_MUTED);
  if (snapshot.calibrating) {
    printAt(8, 206, "KALIBRASYON %", 1, COLOR_AMBER); printNumber(94, 206, snapshot.calibration, 1, COLOR_AMBER);
  } else {
    printAt(8, 206, "ETKILENEN", 1, COLOR_MUTED); printNumber(73, 206, snapshot.affectedChannels, 1, COLOR_TEXT);
  }
  drawFooter(snapshot, full);
}

void drawTraffic(const Snapshot& snapshot, bool full) {
  drawHeader("WiFi TRAFIK", 2, snapshot, full);
  if (full) {
    panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
    printAt(10, 42, "GERCEK 802.11 CERCEVELERI", 1, COLOR_MUTED);
  }
  const uint32_t values[] = {snapshot.managementFrames, snapshot.dataFrames, snapshot.controlFrames};
  const char* labels[] = {"YONETIM", "VERI", "KONTROL"};
  const char* symbols[] = {"M", "D", "C"};
  const uint16_t colors[] = {COLOR_ORANGE, COLOR_CYAN, COLOR_AMBER};
  const uint32_t scaleMax = std::max<uint32_t>(1, std::max(values[0], std::max(values[1], values[2])));
  for (uint8_t i = 0; i < 3; ++i) {
    const int16_t y = 64 + i * 38;
    if (full) {
      panel.fillCircle(17, y + 4, 10, colors[i]);
      printAt(14, y, symbols[i], 1, COLOR_BG);
      printAt(32, y, labels[i], 1, COLOR_TEXT);
      panel.drawRoundRect(84, y - 2, 146, 14, 4, COLOR_GRID);
    }
    const uint16_t width = values[i] ? static_cast<uint16_t>(std::max<uint32_t>(3, values[i] * 142 / scaleMax)) : 0;
    panel.fillRect(86, y, 142, 10, COLOR_BG);
    if (width) panel.fillRoundRect(86, y, width, 10, 3, colors[i]);
    panel.fillRect(178, y + 13, 51, 10, COLOR_BG);
    printNumber(181, y + 15, values[i], 1, colors[i]);
  }
  panel.fillRect(8, 174, 224, 39, COLOR_BG);
  printAt(10, 178, "WiFi", 1, COLOR_MUTED); printNumber(45, 174, snapshot.wifiDevices, 2, COLOR_TEXT);
  printAt(88, 178, "BLE", 1, COLOR_MUTED); printNumber(116, 174, snapshot.bleDevices, 2, COLOR_TEXT);
  printAt(156, 178, "HAVA", 1, COLOR_MUTED); printNumber(194, 174, snapshot.airDevices, 2, COLOR_TEXT);
  printAt(10, 202, "0 = BU ARALIKTA GORULMEDI", 1, COLOR_MUTED);
  drawFooter(snapshot, full);
}

void drawFinder(const Snapshot& snapshot, bool full) {
  drawHeader("RF BULUCU", 3, snapshot, full);
  if (full) panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
  const float raw = snapshot.peak * 0.72f + snapshot.average * 0.28f;
  if (finderBaseline < 0 || static_cast<int32_t>(finderResetUntilMs - millis()) > 0) {
    finderBaseline = raw;
    finderPeak = raw;
  } else {
    if (raw < finderBaseline) finderBaseline = finderBaseline * .96f + raw * .04f;
    finderPeak = std::max(raw, finderPeak * .995f);
  }
  const int score = constrain(static_cast<int>((raw - finderBaseline) * 4.0f + 8), 0, 100);
  const int delta = std::max(0, static_cast<int>(raw - finderBaseline));
  if (full) printAt(10, 42, "YAGI / GORELI RF YAKINLIK", 1, COLOR_MUTED);
  panel.fillRect(8, 57, 224, 60, COLOR_BG);
  printNumber(12, 61, score, 5, score > 75 ? COLOR_RED : score > 40 ? COLOR_AMBER : COLOR_GREEN);
  printAt(76, 94, "/100", 1, COLOR_MUTED);
  printAt(145, 64, "TABAN FARKI", 1, COLOR_MUTED);
  printAt(145, 82, "+", 2, COLOR_ORANGE);
  printNumber(160, 82, delta, 2, COLOR_ORANGE);
  printAt(145, 104, "GORELI PUAN", 1, COLOR_MUTED);
  for (uint8_t i = 0; i < 20; ++i) {
    const uint16_t color = i < score / 5 ? (i > 14 ? COLOR_RED : i > 8 ? COLOR_AMBER : COLOR_GREEN) : COLOR_GRID;
    panel.fillRoundRect(8 + i * 11, 124, 8, 21, 2, color);
  }
  if (full) {
    printAt(8, 150, "ZAYIF", 1, COLOR_GREEN);
    printAt(100, 150, "ORTA", 1, COLOR_AMBER);
    printAt(198, 150, "GUCLU", 1, COLOR_RED);
  }
  panel.fillRect(76, 162, 154, 20, COLOR_BG);
  printAt(10, 167, "TEPE MHz", 1, COLOR_MUTED); printNumber(78, 163, 2400 + snapshot.peakChannel, 2, COLOR_ORANGE);
  if (full) {
    printAt(10, 190, "ANTENI YAVASCA CEVIR", 1, COLOR_TEXT);
    printAt(10, 203, "UZUN BAS: ORTAMI REFERANS AL", 1, COLOR_MUTED);
  }
  drawFooter(snapshot, full);
}

const char* alertName(AlertLevel level) {
  switch (level) {
    case AlertLevel::Medium: return "ORTA DOLULUK";
    case AlertLevel::High: return "YUKSEK DOLULUK";
    case AlertLevel::Extreme: return "ASIRI DOLULUK";
    default: return "RF TABANI NORMAL";
  }
}

uint16_t alertColor(AlertLevel level) {
  return level == AlertLevel::Extreme ? COLOR_RED
      : level == AlertLevel::High ? COLOR_ORANGE
      : level == AlertLevel::Medium ? COLOR_AMBER : COLOR_GREEN;
}

void drawAlert(const Snapshot& snapshot, bool full) {
  drawHeader("RF UYARI", 4, snapshot, full);
  if (full) panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
  const uint16_t color = alertColor(snapshot.alert);
  panel.fillRoundRect(8, 43, 224, 83, 10, COLOR_PANEL);
  panel.drawRoundRect(8, 43, 224, 83, 10, color);
  panel.fillRoundRect(15, 51, 7, 66, 3, color);
  printAtOpaque(31, 51, "UYARI SEVIYESI", 1, COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(31, 67, alertName(snapshot.alert), 2, color, COLOR_PANEL);
  printAtOpaque(31, 96, "GUVEN", 1, COLOR_MUTED, COLOR_PANEL);
  printNumber(70, 92, snapshot.confidence, 2, COLOR_TEXT);
  printAtOpaque(104, 96, "ETKIN CH", 1, COLOR_MUTED, COLOR_PANEL);
  printNumber(164, 92, snapshot.affectedChannels, 2, COLOR_TEXT);
  panel.fillRoundRect(8, 135, 224, 48, 9, COLOR_PANEL);
  panel.drawRoundRect(8, 135, 224, 48, 9, buzzerEnabled ? COLOR_ORANGE : COLOR_GRID);
  printAtOpaque(20, 144, buzzerEnabled ? "BUZZER ACIK" : "BUZZER KAPALI", 2,
          buzzerEnabled ? COLOR_ORANGE : COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(20, 167, "TON", 1, COLOR_MUTED, COLOR_PANEL);
  printNumber(45, 163, buzzerProfile + 1, 2, COLOR_TEXT);
  printAtOpaque(87, 167, "SES", 1, COLOR_MUTED, COLOR_PANEL);
  printNumber(112, 163, buzzerLevel, 2, COLOR_TEXT);
  printAtOpaque(145, 167, "%", 1, COLOR_MUTED, COLOR_PANEL);
  panel.fillRect(10, 190, 222, 23, COLOR_BG);
  if (muteUntilMs && static_cast<int32_t>(muteUntilMs - millis()) > 0)
    printAt(12, 193, "SESSIZ MOD: 5 DAKIKA", 1, COLOR_AMBER);
  else printAt(12, 193, "UZUN BAS: BUZZERI SUSTUR", 1, COLOR_MUTED);
  printAt(12, 205, "OLCUM: ADAPTIF RF DOLULUGU", 1, COLOR_MUTED);
  drawFooter(snapshot, full);
}

void drawConnection(const Snapshot& snapshot, bool full) {
  drawHeader("BAGLANTI", 5, snapshot, full);
  if (full) panel.fillRect(0, 35, WIDTH, 183, COLOR_BG);
  const uint16_t stateColor = snapshot.phoneRadiosEnabled ? COLOR_CYAN : COLOR_GREEN;
  panel.fillRoundRect(8, 43, 224, 70, 10, COLOR_PANEL);
  panel.drawRoundRect(8, 43, 224, 70, 10, stateColor);
  printAtOpaque(20, 52, "TELEFON RADYOLARI", 1, COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(20, 70, snapshot.phoneRadiosEnabled ? "ACIK" : "TAMAMEN KAPALI",
                2, stateColor, COLOR_PANEL);
  printAtOpaque(20, 94, snapshot.phoneConnected ? "UYGULAMA BAGLI" : "UYGULAMA YOK",
                1, snapshot.phoneConnected ? COLOR_GREEN : COLOR_MUTED, COLOR_PANEL);

  panel.fillRoundRect(8, 121, 108, 55, 9, COLOR_PANEL);
  panel.fillRoundRect(124, 121, 108, 55, 9, COLOR_PANEL);
  panel.drawRoundRect(8, 121, 108, 55, 9,
                      snapshot.phoneRadiosEnabled ? COLOR_ORANGE : COLOR_GRID);
  panel.drawRoundRect(124, 121, 108, 55, 9,
                      snapshot.phoneRadiosEnabled ? COLOR_CYAN : COLOR_GRID);
  printAtOpaque(20, 132, "Wi-Fi", 2,
                snapshot.phoneRadiosEnabled ? COLOR_ORANGE : COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(20, 157, snapshot.phoneRadiosEnabled ? "YAYINDA" : "KAPALI", 1,
                snapshot.phoneRadiosEnabled ? COLOR_TEXT : COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(138, 132, "BLE", 2,
                snapshot.phoneRadiosEnabled ? COLOR_CYAN : COLOR_MUTED, COLOR_PANEL);
  printAtOpaque(138, 157, snapshot.phoneRadiosEnabled ? "YAYINDA" : "KAPALI", 1,
                snapshot.phoneRadiosEnabled ? COLOR_TEXT : COLOR_MUTED, COLOR_PANEL);

  panel.fillRect(8, 184, 224, 31, COLOR_BG);
  printAt(10, 185, "nRF24 TARAYICI: ACIK", 1, COLOR_GREEN);
  printAt(10, 199, snapshot.phoneRadiosEnabled
      ? "UZUN BAS: Wi-Fi + BLE KAPAT"
      : "UZUN BAS: Wi-Fi + BLE AC", 1, COLOR_TEXT);
  printAt(10, 210, "DEGISIMDE CIHAZ YENIDEN BASLAR", 1, COLOR_MUTED);
  drawFooter(snapshot, full);
}

void render(const Snapshot& snapshot, bool full) {
  if (!panelReady) return;
  switch (mode) {
    case 1: drawSpectrum(snapshot, full); break;
    case 2: drawTraffic(snapshot, full); break;
    case 3: drawFinder(snapshot, full); break;
    case 4: drawAlert(snapshot, full); break;
    case 5: drawConnection(snapshot, full); break;
    default: drawWaterfall(snapshot, full); break;
  }
}

void serviceButton() {
  const uint32_t now = millis();
  const bool sample = digitalRead(MODE_BUTTON);
  if (sample != buttonSample) { buttonSample = sample; buttonChangedMs = now; }
  if (now - buttonChangedMs < 30 || sample == buttonStable) return;
  buttonStable = sample;
  if (buttonStable == LOW) {
    buttonPressedMs = now;
    return;
  }
  const uint32_t held = now - buttonPressedMs;
  if (held >= 700) {
    if (mode == 3) {
      finderBaseline = -1;
      finderResetUntilMs = now + 1000;
    } else if (mode == 5) {
      phoneRadioToggleRequested = true;
    } else {
      muteUntilMs = now + 300000;
      toneOff();
    }
  } else if (held >= 35) {
    mode = (mode + 1) % MODE_COUNT;
  }
  forceRedraw = true;
}
}  // namespace

void begin() {
  pinMode(MODE_BUTTON, INPUT_PULLUP);
  ledcSetup(BUZZER_CHANNEL, 1000, 8);
  ledcAttachPin(BUZZER, BUZZER_CHANNEL);
  toneOff();
  // Some ST7789 clones need their supply to settle before the hardware reset.
  delay(500);
  panel.init();
  panel.setRotation(0);
  panel.invertDisplay(true);
  // pushImage() uses raw RGB565 words; this panel expects the opposite byte order.
  panel.setSwapBytes(true);
  panel.setTextWrap(false);
  panel.fillScreen(TFT_RED);
  delay(300);
  panel.fillScreen(TFT_GREEN);
  delay(300);
  panel.fillScreen(TFT_BLUE);
  delay(300);
  panel.fillScreen(COLOR_BG);
  printAt(28, 92, "SPECTRA 24", 3, COLOR_ORANGE);
  printAt(53, 126, "EKRAN HAZIR", 2, COLOR_CYAN);
  delay(450);
  panelReady = true;
  forceRedraw = true;
  Serial.println("[DISPLAY] ST7789 ready: TFT_eSPI HSPI 20 MHz, CS-less panel");
}

void service(const Snapshot& snapshot) {
  serviceButton();
  serviceBuzzer(snapshot.alert);
  captureWaterfall(snapshot);
  const uint32_t now = millis();
  if (forceRedraw || now - lastRenderMs >= RENDER_INTERVAL_MS) {
    lastRenderMs = now;
    const bool full = forceRedraw;
    forceRedraw = false;
    render(snapshot, full);
  }
}

void configureBuzzer(bool enabled, uint8_t toneProfile, uint8_t levelPercent) {
  buzzerEnabled = enabled;
  buzzerProfile = constrain(toneProfile, 0, 2);
  buzzerLevel = constrain(levelPercent, 0, 100);
  if (!buzzerEnabled) toneOff();
  forceRedraw = true;
}

void testBuzzer() {
  muteUntilMs = 0;
  testUntilMs = millis() + 2200;
  forceRedraw = true;
}

bool takePhoneRadioToggleRequest() {
  const bool requested = phoneRadioToggleRequested;
  phoneRadioToggleRequested = false;
  return requested;
}

bool ready() { return panelReady; }

}  // namespace LocalPanel
