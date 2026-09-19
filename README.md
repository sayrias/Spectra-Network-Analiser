# SPECTRA 24

**2,4 GHz radyo ortamını telefondan gözlemleyin.**

ESP32-WROOM-32D ve nRF24 tabanlı ölçüm cihazı; yerel Android uygulamasında
canlı waterfall, Wi-Fi/BLE envanteri ve gözlenen kablosuz trafik istatistikleri.
Turuncu-siyah arayüz, uygulamaya özel ayrıntı pencereleri ve ayarlanabilir RF uyarıları.

**Android 8.0+ · Wi-Fi + Bluetooth LE · PlatformIO · Sürüm 3.5.0**

[Kurulum ve derleme](docs/BUILD.md) · [Donanım](firmware/README.md) ·
[Protokol](firmware/docs/PROTOCOL.md) · [Test/sürüm geçmişi](docs/TEST-NOTLARI.md)

## Neler yapar?

- **Canlı RF waterfall:** 2400–2525 MHz aralığında 126 frekans adımı,
  anlık eğri, tepe izi ve 512 ölçümlük geçmiş. Tam ekran, noktaları büyütmek
  yerine daha fazla geçmiş gösterir. Sürükleyerek geçmişi inceleyebilirsiniz.
- **Çevre radarı:** Wi-Fi erişim noktaları, BLE reklamları ve gözlenen trafik;
  sinyal gücü, ad kaynağı, tür beyanı, takma ad ve cihaz ayrıntıları.
- **Yayın kategorileri:** Find My uyumlu yayınlar, iBeacon ve desteklenen BLE
  sensör servisleri için ayrı filtreler. Bunlar pasif imza eşleşmeleridir.
- **İki bağlantı yolu:** yüksek kapasiteli Wi-Fi TCP/UDP aktarımı ve BLE;
  doğrudan ESP32 ağı, telefon hotspot'u veya yerel ağ üzerinden kullanım.
- **Ayarlanabilir ölçüm:** 4/8/12 örnek/kanal, canlı tarama ayarı,
  adaptif taban kalibrasyonu, hassasiyet ve doğrulama süresi.
- **Telefon uyarıları:** özel ses dosyaları, titreşim, tekrar aralığı ve
  açıkça işaretli test olayı. Olay geçmişi ve CSV/JSON dışa aktarımı.
- **Donanımsız arayüz testi:** emülatörde belirgin `DEMO` etiketi taşıyan
  ayrı simülatör; gerçek kullanımda kendiliğinden demo verisine geçilmez.

ESP32 üzerinde **web sitesi/HTTP sunucusu yoktur**. Android uygulaması WebView
kullanmaz; gerçek moddaki ölçümler ESP32'den gelir.

## Ölçümün sınırları

> nRF24 bir SDR değildir. Waterfall, IQ örnekleri veya kesin güç/dBm ölçümü
> değil, RPD eşiğinin aşılma oranıdır. Satırlar RF taramalarıdır, paket değildir.

RF uyarısı, kalibrasyon tabanına göre geniş ve sürekli enerji artışını bildirir;
**tek başına jammer kanıtı değildir**. Noktalı/boş görünüm; aralıklı yayınlar,
sıralı kanal örneklemesi veya alınamamış ölçümlerden kaynaklanabilir.

Radar açısı yönü, yarıçapı gerçek mesafeyi göstermez. Gizli SSID veya
yayınlanmayan BLE adı uydurulmaz. Find My eşleşmesi kesin AirTag/model/sahip
tanımlaması ya da takip edilme tespiti değildir; eşleşme olmaması da güvence değildir.
Trafik sayaçları yalnız dinlenen kanalda gözlenen çerçeveleri içerir; dosya
boyutu veya cihazın toplam internet kullanımı değildir. Bu donanım 5/6 GHz,
hücresel, LoRa veya çevredeki her cihazı algılayamaz.

## Donanım bağlantısı

| ESP32-WROOM-32D | EBYTE E01-ML01DP5 |
| --- | --- |
| GPIO 18 | SCK |
| GPIO 19 | MISO |
| GPIO 23 | MOSI |
| GPIO 4 | CE |
| GPIO 5 | CSN |
| 3.3 V | VCC |
| GND | GND |

Modülü **5 V ile beslemeyin**. Ortak toprak ve kararlı 3,3 V besleme kullanın;
modül yanında 100 nF + 10–47 µF kondansatör önerilir.
[Donanım ve firmware ayrıntıları](firmware/README.md).

## Hızlı başlangıç

Linux üzerinde Bash, **JDK 17** (önerilen), **Python 3.10+** ve PlatformIO Core
gereklidir. Android için SDK 35 ve Build Tools 35.0.0 kullanılır. Gradle
Wrapper projeye dahildir; ayrıca Gradle kurmanız gerekmez. İlk derleme
bağımlılıkları internetten indirir.

Proje kökünde:

```bash
chmod +x build.sh android-app/gradlew scripts/*.sh
./build.sh doctor
./build.sh setup                   # SDK yoksa; lisansları siz onaylarsınız
./build.sh android                 # release/SPECTRA24-debug.apk
./build.sh flash /dev/ttyUSB0       # ESP32'yi derler ve yükler
```

Debug APK'yı telefonunuza kurun. USB hata ayıklama açıksa ve tek telefon bağlıysa:

```bash
adb install -r release/SPECTRA24-debug.apk
```

Uygulamada **Bağlantı → Hızlı Wi-Fi** veya **Otomatik bağlan** seçin.
Android'in bağlantı/izin pencerelerini onaylayın. Elle bağlantı bilgileri:

| Ayar | Varsayılan değer |
| --- | --- |
| Wi-Fi adı | `SPECTRA-24` |
| Wi-Fi parolası | `spectrum24` |
| ESP32 IP | `192.168.4.1` |
| Spektrum / kontrol / keşif | UDP `4210` / TCP `4211` / UDP `4212` |

Doğrudan ESP32 ağı internet sunmaz. İlk kalibrasyon bitene kadar cihazı sabit
tutun. Hotspot ve BLE kullanımını [Android rehberinde](android-app/README.md) bulabilirsiniz.

## Tek komutla geliştirme

```bash
./build.sh                        # numaralı etkileşimli menü
./build.sh android                # kurulabilir debug APK
./build.sh android release        # imzasız release APK
./build.sh firmware               # yalnız derle; ESP32'ye yazmaz
./build.sh all                    # debug APK + firmware
./build.sh test                   # donanımsız kaynak testleri
./build.sh test-full              # testler + her iki derleme
./build.sh setup --with-emulator  # isteğe bağlı emülatör araçlarını da kur
./build.sh emulator               # Android emülatörde etiketli DEMO
./build.sh stop                   # SPECTRA test ortamını durdur
./build.sh clean --dry-run        # temizlik listesini gör
./build.sh clean                  # onay sonrası çöp kutusuna taşı
./build.sh package                # kaynak arşivi + SHA-256
./build.sh help                   # tüm seçenekler
```

`release` APK **imzasızdır**; yayımlamadan önce kendi anahtarınızla imzalayın.
Debug APK test içindir. Anahtarlar ve parolalar kaynak depoya eklenmemelidir.
[Derleme, çıktı dosyaları ve sorun giderme](docs/BUILD.md).

## Proje düzeni

```text
SPECTRA24/
├── build.sh                 # menü ve komut yönlendirici
├── android-app/             # Android uygulaması + Gradle Wrapper
├── firmware/                # bağımsız PlatformIO projesi
├── scripts/                 # derleme, SDK, yükleme ve test komutları
├── tools/                   # simülatör ve regresyon testleri
├── docs/                    # kurulum, mimari düzen, test geçmişi
└── release/                 # yerel çıktılar; Git'e dahil edilmez
```

[Ayrıntılı dosya düzeni](docs/DOSYA-DUZENI.md).

## GitHub'da paylaşma

`./build.sh package`, `release/SPECTRA24-source.tar.gz` oluşturur. Paket;
kaynakları, testleri, belgeleri, font lisansını ve gerekli Gradle Wrapper JAR'ını
içerir. APK/BIN, kişisel SDK yolu, önbellekler, test kayıtları ve ekran görüntüleri
dahil edilmez. APK dağıtımını kaynak deposundan ayrı, GitHub Releases üzerinden
yapabilirsiniz. Paketleyici otomatik olarak GitHub'a hiçbir şey yüklemez.

Yayımlamadan önce [paylaşım kontrol listesini](docs/BUILD.md#paylaşım-kontrol-listesi)
uygulayın. Bu depoda proje sahibinin seçtiği bir **kaynak kod lisansı henüz yok**;
lisans kararı proje sahibine bırakılmıştır. Üçüncü taraf bildirimleri
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) içindedir.

## Güvenli kullanım

Yalnız izinli ortamlarda ölçüm yapın. SSID, MAC adresi ve gözlem kayıtlarını
paylaşmadan önce kişisel verileri gözden geçirin. Varsayılan Wi-Fi parolası
ortaktır. Kontrol protokolünde uygulama katmanı kimlik doğrulaması/TLS ve BLE
eşleştirme zorunluluğu yoktur; güvenilmeyen ağlarda kullanmayın, portları
internete açmayın. Hotspot bilgilerinin BLE üzerinden gönderildiğini unutmayın.
