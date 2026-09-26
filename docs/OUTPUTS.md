# Yerel çıktılar

[![Derleme rehberi](assets/guide.svg)](BUILD.md)

**SPECTRA 24 v1.1.0** — ürün sürümü APK içinde ve firmware durum mesajında yer alır.

Yerel derleme dosyaları `dist/` altında yeniden üretilir; bu dizin tamamen
Git dışında tutulur. Kullanıcıya sunulan sürümler GitHub Releases'tedir;
kaynak deposunda ayrı bir `release/` klasörü tutulmaz.

- `./build.sh android` → `SPECTRA24-debug.apk` (test için imzalı).
- `./build.sh android release` → `SPECTRA24-release-unsigned.apk` (imzasız).
- `./build.sh android signed` → `SPECTRA24-v1.1.0.apk` (yerel yayın anahtarıyla imzalı).
- `./build.sh firmware` → `spectra24-esp32.bin`.
- `./build.sh package` → `SPECTRA24-source.tar.gz` ve SHA-256 özeti.
- Emülatör testleri → `.run/screenshots/` altındaki etiketli DEMO görselleri.

Firmware yüklemek için `./build.sh flash /dev/ttyUSB0` kullanın.
Klasör geçmiş sürümlerin kalıcı arşivi değildir; `clean` çıktıları çöp kutusuna taşır.
[Derleme rehberi](BUILD.md) · [Test notları](TEST-NOTLARI.md)
