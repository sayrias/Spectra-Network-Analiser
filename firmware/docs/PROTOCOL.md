# SPECTRA 24 uygulama protokolü v2

## Firmware 3.5 ek alanları (geriye uyumlu)

BLE envanterindeki `mfgPrefix` üretici alanının en fazla ilk 4 baytının küçük
harfli hex karşılığıdır (şirket kimliği dahil). `mfgLength` orijinal bayt sayısı,
`signatureAgeMs` bu alanın son gözleminden beri geçen süredir. Tam üretici
verisi ve Find My anahtarları aktarılmaz. Şirket kodu tek başına etiket kanıtı değildir.

`{"action":"settings","samples":12}` bağlıyken uygulanır; diğer ayarları
değiştirmez. RF görevi sonraki taramanın başında yeni sayıyı alır. Yanıt
`{"type":"saved","settings":{...}}` mevcut örnek sayısını içerir.
Yeni tarama süresi sonraki ölçüm/status mesajlarına yansır; yeniden bağlantı gerekmez.

ESP32 aynı veri modelini Wi-Fi ve BLE üzerinden sunar. Doğrudan AP adresi
`192.168.4.1`'dir. Hotspot/LAN kullanılıyorsa cihazın istasyon IP'si BLE durum
karakteristiği veya UDP keşfiyle öğrenilir.

## Wi-Fi aktarımı

- `4211/TCP`: satır sonlu UTF-8 JSON kontrol ve olay akışı
- `4210/UDP`: 144 baytlık ikili waterfall paketleri
- `4212/UDP`: cihaz keşfi

Keşif için `SP24_DISCOVER` ASCII verisi UDP 4212'ye yayınlanır. Cihaz
`type=discovery`, cihaz/firmware, `ip`, `apIp`, `sta` ve `radio` alanlarını
içeren JSON ile gönderene cevap verir.

### UDP spectrum paketi

Tüm çok baytlı sayılar little-endian'dır.

| Offset | Boyut | Alan |
|---:|---:|---|
| 0 | 4 | ASCII `SP24` |
| 4 | 1 | Protokol sürümü (`2`) |
| 5 | 1 | bit0 nRF hazır, bit1 aday, bit2 alarm, bit3 kalibrasyon, bit4 STA bağlı, bit5 BLE bağlı |
| 6 | 2 | Sweep sıra numarası |
| 8 | 4 | ESP32 uptime (ms) |
| 12 | 1 | Ortalama doluluk (%) |
| 13 | 1 | Tepe doluluk (%) |
| 14 | 1 | Tepe kanal (MHz = 2400 + kanal) |
| 15 | 1 | Taban üstü eşiği aşan kanal sayısı |
| 16 | 126 | Kanal doluluk değerleri (0–100) |
| 142 | 2 | CRC-16/CCITT-FALSE; ilk 142 bayt |

## JSON kontrolü

Komut örnekleri:

```json
{"action":"hello"}
{"action":"ping"}
{"action":"settings","threshold":30,"channels":28,"hold":4000,"cooldown":30000,"alerts":true}
{"action":"calibrate"}
{"action":"wifi","ssid":"TelefonHotspot","password":"parola"}
{"action":"forgetWifi"}
{"action":"settings","samples":4}
{"action":"scan"}
{"action":"alertTest"}
{"action":"scanNames"}
```

Sınırlar: `threshold` 10–80 taban üstü puan, `channels` 8–100, `hold`
1500–20000 ms ve `cooldown` 10000–300000 ms. Donanım ayarları ile hotspot
bilgisi ESP32 NVS'sinde; ton, ses ve titreşim telefonda saklanır.

Mesaj türleri: `hello`, `status`, `pong`, `saved`, `wifi_config`, `flow`, `wifi`,
`ble` ve `alert`. Envanter mesajlarındaki `live=true`, listenin ESP32 taramasından
geldiğini belirtir. Alarm sınıfı `baseline_wideband_anomaly`'dir.

## Bluetooth LE aktarımı

| İşlev | UUID | Özellik |
|---|---|---|
| Servis | `7a240001-8e7c-4f31-9a62-6d4f53503234` | — |
| Durum | `7a240002-8e7c-4f31-9a62-6d4f53503234` | read, notify |
| Kontrol | `7a240003-8e7c-4f31-9a62-6d4f53503234` | write, write-no-response |
| Spectrum | `7a240004-8e7c-4f31-9a62-6d4f53503234` | notify |
| JSON olay | `7a240005-8e7c-4f31-9a62-6d4f53503234` | notify |

Kontrol karakteristiğine yukarıdaki JSON komutları tek yazma olarak gönderilir.
Bildirimler, MTU'dan bağımsız yeniden birleştirme için dört baytlık başlık
kullanır:

| Offset | Alan |
|---:|---|
| 0 | `J` = JSON, `S` = 144 baytlık spectrum paketi |
| 1 | Mesaj kimliği |
| 2 | Parça indeksi (0 tabanlı) |
| 3 | Toplam parça sayısı |
| 4… | Parça verisi |

Android istemcisi MTU 247 ister, bildirim parçalarını mesaj kimliği ve indeksle
birleştirir, ardından spectrum CRC'sini doğrular. Wi-Fi ve BLE aynı anda
bağlıysa Android yüksek bant genişliği için Wi-Fi spectrum akışını önceler.

PC simülatörü UDP yerine aynı alanları taşıyan `spectrum` JSON'u gönderebilir.
Bu yalnızca açıkça seçilen sentetik DEMO modudur ve gerçek cihaz protokolüne
kendiliğinden geri dönüş olarak kullanılmaz.

## Firmware v3 ekleri (ikili protokol hâlâ v2)

v3.2: `status.skippedSweeps` son-ölçüm kuyruğundan alınamayan ara taramaları;
`udpSendErrors` başarısız yerel UDP `endPacket` çağrılarını sayar (alıcı teslim
onayı değildir). `wifiPowerSave` modem tasarrufu durumunu belirtir. 3.2.1'de
BLE uyumluluğu için açık tutulur; TCP bunu değiştirmez.
`flowIntervalMs=500` nominal sayaç gönderim aralığıdır;
oran hesabında her zaman gerçek `flow.windowMs` kullanılmalıdır.
İkili spektrum biçimi ve CRC değişmedi. Görev daha seyrek yield eder;
180 µs RF bekleme ve 4/8/12 örnek seçenekleri korunur.

v3.1: `scanNames` tek seferlik aktif BLE taraması kuyruğa alır; `name_scan`
yanıtı isteğin kabulünü belirtir, bir ad bulunduğu anlamına gelmez.
`ble.devices[].appearance` yayınlanan Bluetooth SIG Appearance değeridir;
0 bilinmiyor anlamındadır. Cihaz kimliği doğrulanmış değildir.
`status` ayrıca `heapFree`, `heapMin`, `uptime` ve `trafficCapacity` içerir.

- `settings.samples`: 4–12; Android 4/8/12 profilleri sunar.
- `status`: `scanHz`, `sweepUs`, `samples`, `sweeps`.
- `flow.windowMs`: sayaçların gerçek ölçüm penceresi. Paket/sn =
  `total * 1000 / windowMs`; `bytes` gözlenen çerçeve baytlarıdır.
- `traffic.devices`: `mac`, `ssid`, `txBytes`, `rxBytes`, `txPackets`,
  `rxPackets`, `ageMs`, `rssiKnown`, `rssi`, `ch`. Tablo en fazla 48 adres;
  aktif görünürlük 60 saniye. Baytlar dosya boyutu değildir.
- BLE envanter JSON'u altışar kayıtla `batch`, `page` (0 tabanlı), `pages`
  içeren sayfalara ayrılır. Her sayfa ayrıca J parçalarına bölünür. Android,
  aynı batch'in bütün sayfalarını almadan listeyi değiştirmez.
- Tam durum JSON'u J olay kanalından gider; küçük MTU'da kesilmiş durum
  bildirimi gönderilmez. Wi-Fi varken envanter TCP'dedir.
- `alertTest`: `type=alert,test=true` ile yalnızca ses/bildirim zinciri testi;
  RF alarm durumunu veya ölçümleri değiştirmez.
- `scan`: Wi-Fi/BLE tarama zamanlayıcılarını yeniler. Bağlantı sürerken
  Wi-Fi taraması geçici aktarım gecikmesi oluşturabilir.
