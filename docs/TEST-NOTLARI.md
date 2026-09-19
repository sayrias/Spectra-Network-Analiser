# SPECTRA 24 v1.0.0 — Test ve sürüm notları

[![Ana rehber](assets/guide.svg)](../README.md)
[![Derleme ve test](assets/tests.svg)](BUILD.md)

## Sürüm kimliği

| Bileşen | Değer |
| --- | --- |
| Android görünen sürüm | 1.0.0 |
| Android dahili güncelleme sayacı | versionCode 10 |
| ESP32 firmware | 1.0.0 |
| İkili haberleşme protokolü | 2 — değişmedi |

v1.0.0, ilk paylaşım sürümünün adlandırmasıdır. Önceki geliştirme sürümlerinin
özellikleri korunur; ürün sürümü yeniden düzenlendi diye protokol sıfırlanmaz.
Android güncelleme sayacı mevcut kurulumların güncellenebilmesi için artar.

## Sürüm kapsamı

- 126 kanallı RPD waterfall, tepe izi, duraklatma ve 512 ölçümlük geçmiş.
- Tam ekranda sabit hücre boyutu: büyüyen alan daha eski ölçümler gösterir.
- Wi-Fi TCP/UDP ve BLE aktarımı; doğrudan AP, hotspot/LAN ve elle IP.
- Wi-Fi/BLE/trafik envanteri; Find My, iBeacon ve BLE sensör filtreleri.
- Bağlantıyı yenilemeden 4/8/12 örnek/kanal seçimi ve cihaz onayı.
- Turuncu-siyah özel pencereler, olay günlüğü, takma ad ve dışa aktarım.
- Ayarlanabilir RF algılama, özel ses, titreşim ve işaretli alarm zinciri testi.

## Doğrulama katmanları

| Kontrol | Komut | Kapsam |
| --- | --- | --- |
| Java kaynak testleri | `./build.sh test` | 172 protokol + 225 akış/radar + 18 geçmiş + 13 metadata + 14 BLE imzası |
| Python kontrolleri | `./build.sh test` | Simülatör, Wi-Fi/BLE kaynak politikası, paketleme/yol güvenliği |
| Derlemeler | `./build.sh test-full` | ESP32 ve Android debug |
| Release derleme | `./build.sh android release` | İmzasız APK; dağıtım imzası değildir |
| İmzalı APK | `./build.sh android signed` | Mevcut yayın anahtarı, APK v2/v3 imza ve ZIP hizalama doğrulaması |
| Android model/izin | `./build.sh test-models` | Açık emülatörde 63 kontrol |
| Arayüz | `python3 tools/ui_v35_smoke.py` | DEMO kategori ayrımı, menüler, aynı bağlantıda 12/8/4 onayı |
| Gerçek veri bağlantısı | `./build.sh test-full --live` | Bağlı fiziksel cihazdan TCP hello + UDP çerçevesi/CRC |

Sürüm adlandırmasından önce aynı işlevler üzerinde 442 Java kontrolü,
63 Android kontrolü, DEMO bağlantısı ve debug/release/firmware derlemeleri
başarılıydı. Bunlar yeni sürümün sahada test edildiği anlamına gelmez.
Güncel v1.0.0 kontrolleri aşağıdaki doğrulama kaydında ayrıca belirtilir.

## v1.0.0 doğrulama kaydı — 19 Eylül 2026

- 442 Java kontrolü, Python simülatör/ortak radyo kontrolleri ve 8 paketleme
  testi geçti. Belge bağlantıları, SVG yapısı, lisans paketleme ve sürüm
  tutarlılığı kontrolü başarılı.
- ESP32 firmware, Android debug APK ve imzasız release APK yeniden derlendi.
  APK metadata kontrolünde versionName 1.0.0 / versionCode 10 doğrulandı.
- Ana proje ile ayrı IDE çalışma kopyasının firmware kaynakları eşit.
  Bu doğrulamada donanıma flash yazılmadı.
- Lisans metni resmî PolyForm Noncommercial 1.0.0 metniyle karşılaştırıldı;
  değiştirilmedi. README görselleri yerel SVG render ile kontrol edildi.
- Bu sürüm adlandırmasından sonra fiziksel telefon/ESP32 saha testi ve
  Android emülatör modeli/UI testi yeniden çalıştırılmadı. Önceki sonuçlar
  yukarıda tarihsel kapsamıyla belirtilmiştir.
- Yayın anahtarıyla imzalanan `SPECTRA24-v1.0.0.apk` için APK v2/v3 imza
  doğrulaması ve ZIP hizalama kontrolü geçti. Anahtar kaynak depoda tutulmaz.
  Bu kontrol, fiziksel telefonda kurulum/çalışma testi yerine geçmez.

## Bilinen sınırlar

- Emülatör verileri sentetiktir; görseller fiziksel RF ölçümü değildir.
- Fiziksel telefonlarda tüm üretici/Android sürümleri ve çoklu ESP32 seçimi
  doğrulanmış değildir.
- Gerçek AirTag saha doğrulaması yoktur. İmza eşleşmesi model/sahip veya
  takip edilme kanıtı değildir.
- RF uyarısı jammer teşhisi değildir; radar yön veya metre ölçmez.
- Yayınlanmayan adlar çözümlenmiş gibi gösterilmez. Bayt sayaçları dosya
  boyutu veya tüm internet kullanımı değildir.
- SDK XML sürüm uyarısı ve bazı mevcut deprecated API derleyici notları
  görülebilir; başarılı derleme ile gerçek cihaz doğrulaması ayrı şeylerdir.

## Yeniden deneme

```bash
./build.sh test-full
./build.sh emulator
./build.sh test-models
python3 tools/ui_v35_smoke.py
./build.sh stop
```

Arayüz testlerini aynı emülatörde paralel çalıştırmayın. `ui_v*` dosya adları
test gruplarının geliştirme geçmişini belirtir; APK sürümünü belirlemez.
Fiziksel test için ayrıca hedef kartı ve uygun ağı hazırlayın.
