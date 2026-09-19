# İndirme ve kurulum

[![Ana rehber](assets/guide.svg)](../README.md)
[![Derleme](assets/android.svg)](BUILD.md)

**SPECTRA 24 v1.0.0 · Android 8.0+ · ESP32 + nRF24**

SPECTRA 24 iki parçadan oluşur: telefona kurulan Android uygulaması ve
ESP32 üzerinde çalışan firmware. Canlı ölçüm için ikisinin de hazır olması
ve nRF24 modülünün ESP32'ye doğru bağlanması gerekir.

## 1. Uygulamayı edinin

[Sürümler sayfasında](https://github.com/sayrias/Spectra-Network-Analiser/releases)
kurulabilir bir APK sunuluyorsa sürüm açıklamasını okuyup dosyayı indirin.
Hazır APK kullanırken Android SDK veya JDK kurmanız gerekmez.

| Dosya | Kullanım |
| --- | --- |
| İmzalı `.apk` | Android telefona kurulacak uygulama |
| `unsigned.apk` | İmzasız derleme çıktısı; doğrudan kurulamaz |
| Kaynak `.zip` / `.tar.gz` | Projeyi incelemek veya derlemek için; uygulama değildir |
| Firmware `.bin` | ESP32 yazılımı; Android telefona kurulmaz |

Hazır APK bulunmuyorsa [derleme rehberini](BUILD.md) izleyerek
`./build.sh android` komutuyla test amaçlı debug APK oluşturabilirsiniz.

## 2. Donanımı hazırlayın

Gerekli donanım: ESP32-WROOM-32D, EBYTE E01-ML01DP5 nRF24 modülü,
veri aktarabilen USB kablosu ve uygun 3,3 V besleme.

| nRF24 | ESP32 |
| --- | --- |
| SCK | GPIO 18 |
| MISO | GPIO 19 |
| MOSI | GPIO 23 |
| CE | GPIO 4 |
| CSN | GPIO 5 |
| VCC | 3.3 V |
| GND | GND |

Bağlantıları güç kapalıyken yapın. Modülü **5 V ile beslemeyin**.
Besleme ve kondansatör ayrıntıları için [donanım rehberine](../firmware/README.md)
bakın. Farklı GPIO bağlantıları kullanılıyorsa firmware pinleri değiştirilip
yeniden derlenmelidir; Android APK'nın değiştirilmesi gerekmez.

## 3. ESP32 firmware'ini yükleyin

PlatformIO kurulu bir bilgisayarda proje kökünden çalıştırın:

```bash
./build.sh flash /dev/ttyUSB0
```

Port adını kendi sisteminize göre değiştirin. Bu komut firmware'i derler ve
gerekli flash bölümleriyle birlikte yükler. Yalnız uygulama `.bin` dosyasını
rastgele bir adrese yazmayın; yeni kartta bootloader ve bölüm tablosu da gerekir.

Uygulama ile uyumlu firmware sürümünü kullanın. v1.0.0 uygulaması ve
v1.0.0 firmware'i birlikte kullanılmak üzere hazırlanmıştır.

## 4. Telefona kurun ve bağlanın

1. İndirdiğiniz imzalı APK'yı telefonda açın. Android isterse yalnız güvendiğiniz
   indirme/dosya uygulamasına bu kurulum için izin verin.
2. SPECTRA 24'ü açıp bağlantı için istenen yakın cihaz/Bluetooth izinlerini verin.
   Bildirimli uyarılar için bildirim izni de gerekebilir.
3. ESP32'yi açın. Uygulamanın **Bağlantı** ekranından **Hızlı Wi-Fi** veya
   **Bluetooth** seçin; Android'in bağlantı isteğini onaylayın.
4. İlk taban kalibrasyonu tamamlanana kadar cihazı sabit tutun.

### Bağlantı seçenekleri

| Yöntem | Bağlantı bilgisi |
| --- | --- |
| Doğrudan Wi-Fi | Ağ: `SPECTRA-24`, parola: `spectrum24`, IP: `192.168.4.1` |
| Bluetooth LE | SPECTRA servisinin keşfi; telefonun Wi-Fi ağını değiştirmek gerekmez |
| Telefon hotspot'u / LAN | Cihazın ağdan aldığı IP keşfedilir veya elle girilir |

Doğrudan ESP32 ağı internet sağlamaz; Android “bağlı kal” diye sorarsa bu yerel
bağlantıyı koruyun. `192.168.4.1`, ESP32'nin kendi yerel ağ adresidir; belirli
bir kullanıcıya ait internet adresi değildir. Aynı APK, uyumlu firmware yüklü
farklı ESP32'lerle kullanılabilir; tek bir kartın MAC adresine kilitli değildir.

Aynı ortamda birden fazla SPECTRA bulunuyorsa otomatik keşif hedef seçimini
karıştırabilir. İlk kurulumda yalnız bağlanılacak kartı açın. Firmware'deki
Wi-Fi adı/parola veya AP adresi özelleştirilmişse **Hızlı Wi-Fi** varsayılanları
uyuşmayabilir; elle ağ/IP girişi veya BLE bağlantısını kullanın.

## Güncelleme ve sorun giderme

- **APK kurulmuyor:** Android sürümünü ve dosyanın imzalı APK olduğunu kontrol edin.
  Kaynak arşivi veya `unsigned.apk` bir kurulum dosyası yerine kullanılamaz.
- **Güncellemede imza uyuşmazlığı:** Aynı dağıtım kanalından ve aynı yayın
  anahtarıyla imzalanmış sürümü kullanın. Debug ve release imzaları farklı
  olabilir. Uygulamayı kaldırmak ayarları ve yerel günlükleri siler.
- **ESP32 bulunamıyor:** Gücü, firmware yüklemesini, telefon izinlerini ve
  seçili bağlantı yöntemini kontrol edin. Ayrıntılar [Android rehberinde](../android-app/README.md).
- **nRF24 bulunamıyor veya ölçüm kararsız:** Pin bağlantılarını, ortak toprağı
  ve beslemeyi kontrol edin. APK kurulmuş olması donanımın hazır olduğu anlamına gelmez.

## Kullanım sınırları

Sistemi yalnız izinli ve güvenilen yerel ortamlarda kullanın. Varsayılan ağ
parolası ortaktır; kontrol protokolünde TLS/uygulama-katmanı kimlik doğrulaması
yoktur. Servis portlarını internete açmayın.

nRF24 bir SDR değildir; RF uyarısı kesin jammer teşhisi, radar da gerçek yön
veya mesafe ölçümü değildir. [Ölçüm sınırları](../README.md#ölçümün-sınırları)
ve [lisans koşullarını](LISANS.md) inceleyin.
