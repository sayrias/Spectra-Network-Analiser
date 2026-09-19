# Yerel çıktılar

Bu klasördeki dosyalar yeniden üretilir; kaynak deposuna eklenmez.

- `./build.sh android` → `SPECTRA24-debug.apk` (test için imzalı).
- `./build.sh android release` → `SPECTRA24-release-unsigned.apk` (imzasız).
- `./build.sh firmware` → `spectra24-esp32.bin`.
- `./build.sh package` → `SPECTRA24-source.tar.gz` ve SHA-256 özeti.
- Emülatör testleri → `screenshots/` altındaki etiketli DEMO görselleri.

Firmware yüklemek için `./build.sh flash /dev/ttyUSB0` kullanın.
Klasör geçmiş sürümlerin kalıcı arşivi değildir; `clean` çıktıları çöp kutusuna taşır.
[Derleme rehberi](../docs/BUILD.md) · [Test geçmişi](../docs/TEST-NOTLARI.md)
