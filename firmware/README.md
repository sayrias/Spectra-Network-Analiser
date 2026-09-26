# SPECTRA 24 firmware v1.1.0

[![Ana rehber](../docs/assets/guide.svg)](../README.md)
[![Protokol](../docs/assets/protocol.svg)](docs/PROTOCOL.md)

ESP32-WROOM-32D, EBYTE E01-ML01DP5 ve 240×240 ST7789 ekran için pasif 2,4 GHz
alan gözlem firmware'i. Ekran, buton ve buzzer telefondan bağımsız çalışır.
HTTP sunucusu yoktur; Android uygulaması TCP/UDP veya Bluetooth LE ile bağlanır.

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

### ST7789 ekran, buton ve pasif buzzer

Ekran nRF24'ten ayrı HSPI hattındadır; ekran çizimi RF SPI işlemleriyle aynı
veri yolunu paylaşmaz.

| ESP32 | ST7789 1.3" |
|---|---|
| GPIO 14 | SCL / SCK |
| GPIO 13 | SDA / MOSI |
| GPIO 27 | CS — kartta varsa |
| GPIO 26 | DC / A0 |
| GPIO 33 | RES / RST |
| 3.3 V | VCC |
| GND | GND |

Ekran arka ışığı donanımsal potansiyometreye bağlı kalır; firmware parlaklığı
değiştirmez. Kart üzerindeki pin yazıları farklıysa fiziksel modül şeması esas
alınmalıdır. Ekran veri hatlarına 5 V uygulanmamalıdır. Ekran bu HSPI hattının
tek cihazı olduğundan CS sürekli etkin tutulur; CS pini bulunmayan 7 pinli
kartlar da desteklenir. Açılışta kırmızı, yeşil, mavi test ekranları ve ardından
`SPECTRA 24 / EKRAN HAZIR` görünmelidir.

| İşlev | ESP32 bağlantısı |
|---|---|
| Mod butonu | GPIO 32 ile GND arasına |
| Pasif buzzer sürücüsü | GPIO 25 → 1 kΩ → NPN transistor base |

Buzzer doğrudan GPIO'dan beslenmez. 2N2222/BC337 emitter GND'ye, collector
buzzer eksi ucuna; buzzer artı ucu kendi 3,3/5 V beslemesine bağlanır. Tüm
topraklar ortak olmalıdır. Ton üretmek için aktif değil **pasif** buzzer gerekir.

Kısa buton basışı `Waterfall → RF Spektrum → Trafik → RF Bulucu → RF
Uyarı → Bağlantı` ekranlarını gezer. Açılış ekranı waterfall'dır; 145 tam
tarama satırını saklar ve en yeni ölçümü üstte gösterir. RF Bulucu ekranında
uzun basış o yöndeki göreli referansı yeniler. Bağlantı ekranında uzun basış
telefon radyolarını değiştirir; diğer ekranlarda uzun basış buzzerı beş dakika
susturur.

**Bağlantı 6/6** ekranında uzun basıldığında seçim NVS'ye kaydedilir ve ESP32
yeniden başlatılır. Kapalı durumda Wi-Fi AP/STA ve Bluetooth LE başlatılmaz;
nRF24 taraması, ST7789 ekranı, fiziksel buton ve buzzer çalışmaya devam eder.
Bu temiz yerel mod, ESP32'nin kendi 2,4 GHz yayınını ölçüme eklememesini sağlar.
Telefon bağlantısını geri getirmek için aynı ekranda yeniden uzun basın. Radyo
kapalıyken uygulama bağlantısı, Wi-Fi/BLE envanteri ve 802.11 trafik sayaçları
kullanılamaz; waterfall ve RF doluluk uyarısı çalışır.

Yagi kullanmak için cihazın USB ve batarya beslemesini kesin, mevcut 2,4 GHz
anteni E01/nRF24 anten soketinden söküp 50 Ω, 2,4 GHz ve SMA/RP-SMA tipi
fiziksel soketle uyumlu Yagi'yi takın. Cihazı anten takılıyken açın, `RF
Bulucu 4/5` ekranına geçin ve sakin bir yönde uzun basarak referans alın.
Anten yavaşça çevrildiğinde gösterilen değer gerçek nRF24 ölçümünden
türetilen göreli farktır; kesin dBm, metre veya elektronik yön ölçümü değildir.

Buzzer ses seviyesi 0'da tamamen kapalı, 70–100 aralığında pasif piezo için
%50 duty tepe PWM sürüşündedir. Derin, RWR ve keskin profilleri buzzerın
duyulabilir rezonans bandına yakın farklı frekanslar ile orta/yüksek/aşırı
seviyelerde farklı darbe dizileri kullanır.

## Gerçek zamanlı ölçüm

- nRF24, 0–125 kanallarını ayrı bir görevde sürekli tarar; her kanal varsayılan
  4 RPD örneğinden 0–100 doluluk değerine dönüşür. Uygulamadan 8/12 seçilebilir.
  Her örnekte RX yerleşmesi ve RPD algılama için 180 µs beklenir.
  Gerçek hız örnek sayısına, envanter taramasına ve bağlantı koşullarına bağlıdır;
  sabit bir telefon kare hızı vaat edilmez. RF bekleme süresi korunur.
- Açılışta 20 tam sweep ile bulunduğu yerin taban profili ölçülür. Bu sırada
  alarm kapalıdır.
- Adaptif uyarı orta, yüksek ve aşırı doluluk olarak üç seviyedir. Kanal sayısı,
  ayrı RF bölgeleri, ortalama taban farkı ve süre birlikte değerlendirilir.
  Seviyeler yerel ekranda farklı renkte; pasif buzzerda farklı ton ve ritimde
  gösterilir. Bu sınıflar kalibre edilmiş enerji değişimidir, jammer kimliği değildir.
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
- Yerel **Bağlantı 6/6** ekranında uzun basış Wi-Fi ile BLE'yi birlikte tamamen
  kapatır veya açar. Değişiklik yeniden başlatmadan sonra uygulanır ve kalıcıdır.

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
içeriği vermez. Yagi ekranı antenin tutulduğu yöndeki göreli enerji değişimini
gösterir; gerçek radar, açı ya da mesafe ölçümü değildir. RF doluluk sınıfları
kesin jammer teşhisi değildir.
