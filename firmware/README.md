# SPECTRA 24 firmware v1.0.0

[![Ana rehber](../docs/assets/guide.svg)](../README.md)
[![Protokol](../docs/assets/protocol.svg)](docs/PROTOCOL.md)

ESP32-WROOM-32D ve EBYTE E01-ML01DP5 (nRF24L01P + PA/LNA) için pasif 2,4 GHz
alan gözlem firmware'i. HTTP sunucusu yoktur; Android uygulaması TCP/UDP veya
Bluetooth LE ile bağlanır.

## Donanım

| ESP32 | E01-ML01DP5 |
|---|---|
| GPIO 18 | SCK |
| GPIO 19 | MISO |
| GPIO 23 | MOSI |
| GPIO 4 | CE |
| GPIO 5 | CSN |
| 3.3 V | VCC |
| GND | GND |

E01-ML01DP5'i kesinlikle 5 V ile beslemeyin. Üretici 2,0–3,6 V besleme ve
3,3 V tipik değer belirtir; modül RX'te tipik 20 mA, en yüksek TX ayarında tipik
130 mA isteyebilir. Firmware nRF24'ü sadece RX'te tutsa da temiz bir regülatör
kullanın ve VCC/GND'ye modülün hemen yanında 100 nF + 10–47 µF kondansatör
ekleyin. ESP32 ve modül toprakları ortak olmalıdır. Kararsız besleme, sahte
waterfall çizgileri veya `nRF24 NOT FOUND` sonucu oluşturabilir.

## Gerçek zamanlı ölçüm

- nRF24, 0–125 kanallarını ayrı bir görevde sürekli tarar; her kanal varsayılan
  4 RPD örneğinden 0–100 doluluk değerine dönüşür. Uygulamadan 8/12 seçilebilir.
  Her örnekte RX yerleşmesi ve RPD algılama için 180 µs beklenir.
  Gerçek hız örnek sayısına, envanter taramasına ve bağlantı koşullarına bağlıdır;
  sabit bir telefon kare hızı vaat edilmez. RF bekleme süresi korunur.
- Açılışta 20 tam sweep ile bulunduğu yerin taban profili ölçülür. Bu sırada
  alarm kapalıdır.
- Alarm için taban üstü artışın ayarlanan sayıda kanalda, en az beş ayrı RF
  bölgesinde ve ayarlanan bekleme süresi boyunca sürmesi gerekir.
- Normal ortam yavaşça değiştiğinde taban profili kendini uyarlar. Uygulamadan
  cihaz yeri/anteni değiştirildikten sonra **Yeniden kalibre et** kullanılabilir.
- Wi-Fi listesi ESP32'nin pasif taramasından, BLE listesi pasif reklam taramasından
  gelir. Kullanıcı `scanNames` isterse bir tarama süresince BLE scan-request
  gönderilir; sonraki tarama tekrar pasiftir. Bağlantı/eşleştirme yapılmaz.
  `flow` değerleri ESP32'nin o anda dinlediği Wi-Fi kanalındaki gerçek
  802.11 çerçeve sayaçlarıdır; içerik kaydedilmez.
- Trafik sayaçları nominal 500 ms aralıkla gönderilir; paket/sn ve bayt/sn
  hesapları gerçek `windowMs` süresini kullanır. BLE ile ortak radyo çalışması
  için Wi-Fi modem tasarrufu `WIFI_PS_MIN_MODEM` olarak korunur; TCP bağlantısı
  bu ayarı değiştirmez.
- Durum mesajlarında atlanan ölçüm ve UDP gönderim hata sayaçları bulunur.
  Bunlar kayıp ağ paketlerinin veya aktarılmış dosya boyutlarının ölçümü değildir.

## Bağlantılar

- Doğrudan AP: `SPECTRA-24`, parola `spectrum24`, IP `192.168.4.1`
- TCP kontrol/JSON: `4211`
- UDP waterfall: `4210`
- UDP keşif: `4212`
- BLE cihaz adı: `SPECTRA-24`; servis UUID'si protokol belgesindedir
- Telefon hotspot/LAN bilgisi BLE veya TCP komutuyla NVS'ye kaydedilir. ESP32 AP'yi
  kapatmadan istasyon olarak bu ağa da katılır.

## Derleme ve yükleme

Proje ana klasöründen:

```bash
./build.sh firmware
./build.sh flash /dev/ttyUSB0
./build.sh flash /dev/ttyUSB0 --monitor
```

Ya da bu klasörde:

```bash
~/.platformio/penv/bin/pio run
~/.platformio/penv/bin/pio run --target upload --upload-port /dev/ttyUSB0
```

Seri başlangıçta `nRF24 : READY`, ardından kalibrasyon yüzdeleri ve
`Calibration complete; adaptive baseline active` görülmelidir. Protokol ayrıntısı:
[docs/PROTOCOL.md](docs/PROTOCOL.md).

## Sınır

nRF24 tam SDR değildir; IQ verisi, kesin dBm seviyesi, sinyal türü veya paket
içeriği vermez. `baseline_wideband_anomaly` bir enerji değişimi sınıflandırmasıdır,
kesin jammer teşhisi değildir.
