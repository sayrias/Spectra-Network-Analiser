# Geliştirme ve test araçları

[![Ana rehber](../docs/assets/guide.svg)](../README.md)
[![Test kapsamı](../docs/assets/tests.svg)](../docs/TEST-NOTLARI.md)

**v1.0.0** için doğrulama araçları. `ui_v*` dosyalarının adları geliştirme
dönemindeki test gruplarını belirtir; ürünün güncel sürümü değildir.

`test_docs.py`, göreli belge/görsel bağlantılarını, SVG yapısını, lisansın
pakete alınmasını ve Android/firmware/belge sürüm tutarlılığını kontrol eder.

- `device_simulator.py`: yalnız etiketli DEMO; gerçek radyo ölçümü değildir.
- `*SelfTest.java`: protokol, geçmiş, performans, metadata ve BLE imza birim testleri.
  Kökte `./build.sh test` hepsini derleyip çalıştırır.
- `test_wifi_coexistence.py`: ESP32 Wi-Fi/BLE güç politikası kaynak kontrolü.
- `ui_smoke.py`: ortak emülatör araçları ve genel arayüz testi.
- `ui_v31/32/33/34/341_smoke.py`: önceki özelliklerin hâlâ kullanılan regresyon testleri.
- `ui_v35_smoke.py`: kategori ayrımı ve aynı TCP bağlantısında anlık 12/8/4 örnek ayarı.
- `wifi_request_smoke.py`: Android ağ isteği arayüzü denemesi.

Önce `./build.sh emulator`, sonra istenen `python3 tools/ui_…_smoke.py`.
Arayüz testlerini aynı emülatörde paralel çalıştırmayın.
Test görselleri `release/screenshots/`, günlükler `.run/` altında oluşur.
Testler yalnız `emulator-*` ADB hedeflerini kabul eder.

`project_bundle.py`: açık kaynak-dosya listesiyle paylaşım arşivi ve yalnız
bilinen çıktıları çöp kutusuna taşıyan temizlik. `test_project_bundle.py`,
paket içeriği, anahtar başlığı, yol/symlink güvenliği ve temizlik kapsamını sınar.
