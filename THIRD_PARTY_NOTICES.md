# Üçüncü taraf bileşenler

[![Ana rehber](docs/assets/guide.svg)](README.md)

Projenin özgün kodu ve belgeleri [PolyForm Noncommercial 1.0.0](LICENSE.md)
ile sunulur. Aşağıdaki üçüncü taraf bileşenler kendi lisanslarını korur;
proje lisansı onları yeniden lisanslamaz. Kaynak ve ikili dosya dağıtımında
ilgili bileşenlerin lisans dosyalarını koruyun.

## Depoda bulunan dosyalar

- **Font Awesome Free 6.7.2**, Fonticons, Inc.: `fa-solid-900.ttf`.
  Font için SIL OFL 1.1; tam dağıtım bildirimi
  [fonts/LICENSE.txt](android-app/app/src/main/assets/fonts/LICENSE.txt).
- **Gradle Wrapper**: [Gradle projesi](https://github.com/gradle/gradle),
  [Apache License 2.0](https://github.com/gradle/gradle/blob/v8.9.0/LICENSE).
  Wrapper betikleri lisans başlıklarını, JAR da `META-INF/LICENSE` dosyasını
  içerir; JAR derleme için korunur.

## Derleme sırasında indirilen bağımlılıklar

Sürümler `firmware/platformio.ini` ve Android Gradle dosyalarında sabitlenir.
Bağımlılık kaynakları/önbellekleri paylaşım arşivine kopyalanmaz.

- [Arduino-ESP32](https://github.com/espressif/arduino-esp32)
- [RF24](https://github.com/nRF24/RF24)
- [ArduinoJson](https://github.com/bblanchon/ArduinoJson)
- [NimBLE-Arduino](https://github.com/h2zero/NimBLE-Arduino)
- Android SDK, Android Gradle Plugin ve PlatformIO araçları.

Bu liste tüm transitif bileşenlerin lisans dökümü değildir. İkili release
yayımlarken kullanılan sürümlerin kendi lisans/bildirimlerini de inceleyin.
