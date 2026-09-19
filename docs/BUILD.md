# Derleme ve geliştirme rehberi

[![Ana rehber](assets/guide.svg)](../README.md)
[![Testler](assets/tests.svg)](TEST-NOTLARI.md)

**SPECTRA 24 v1.0.0** — önce araçları kontrol edin, sonra hedefinizi seçin.

Tüm örnekler proje kökünden çalışır. `build.sh`, başka bir çalışma dizininden
tam yoluyla çağrıldığında da doğru proje üzerinde çalışır. Linux/Bash iş akışı
desteklenir; Windows'ta Android Studio/Gradle ve PlatformIO ayrı kullanılabilir,
ancak buradaki shell/USB/emülatör otomasyonu Windows için test edilmemiştir.

## Gereksinimler

| Bileşen | Gereksinim |
| --- | --- |
| Java | JDK 17 önerilir; yerel doğrulama JDK 21 ile de yapılmıştır |
| Python | 3.10+; araçlar standart kütüphaneyi kullanır |
| Android | SDK 35, Build Tools 35.0.0, isteğe bağlı ADB |
| Android derleme | Projedeki Gradle Wrapper 8.9, Android Gradle Plugin 8.7.3 |
| ESP32 | PlatformIO Core; proje espressif32 6.12.0 kullanır |
| SDK indirme | curl, unzip, sha256sum; Google lisanslarını kullanıcı onaylar |
| Emülatör | x86_64 Android 35 imajı, Linux/KVM erişimi önerilir |
| Geri alınabilir temizlik | `gio trash` komutu (GIO araçları) |

`pio` PATH üzerinde değilse VS Code PlatformIO'nun
`$HOME/.platformio/penv/bin/pio` kurulumu da bulunur. Sistem Python'una zorla
paket yüklenmez. Android SDK arama sırası: `ANDROID_SDK_ROOT`, `ANDROID_HOME`,
`android-app/local.properties`, son olarak `$HOME/Android/Sdk`.
Birden fazla SDK varsa değişkenleri ve local.properties dosyasını tutarlı tutun.

```bash
./build.sh doctor
./build.sh setup                   # sadece APK derlemeye gereken SDK + ADB
./build.sh setup --tools-only      # yalnız Google komut satırı araçları
./build.sh setup --with-emulator   # SDK + emülatör + sistem imajı (daha büyük indirme)
```

SDK daha önce kuruluysa `setup` gerekli değildir. SDK kullanıcı alanına kurulur;
root yetkisi istenmez. İlk Gradle/PlatformIO derlemesi internet gerektirir.

## Derleme çıktıları

| Komut | Çıktı / etki |
| --- | --- |
| `./build.sh android` | `dist/SPECTRA24-debug.apk` |
| `./build.sh android release` | `dist/SPECTRA24-release-unsigned.apk` |
| `./build.sh android signed` | `dist/SPECTRA24-v1.0.0.apk` — mevcut özel yayın anahtarıyla imzalanır |
| `./build.sh firmware` | `dist/spectra24-esp32.bin` |
| `./build.sh all` | Debug APK + firmware; donanıma yüklemez |
| `./build.sh flash /dev/ttyUSB0` | Firmware derler ve bağlı ESP32'ye yazar |
| `./build.sh flash /dev/ttyUSB0 --monitor` | Yükleme sonrası seri monitör; Ctrl+C ile çıkış |
| `./build.sh package` | Kaynak `.tar.gz` arşivi ve `.sha256` dosyası |

Tek `.bin` dosyasını rastgele bir flash adresine yazmayın. PlatformIO, bootloader
ve bölüm tablosunu da doğru yerlere yükler. Seri port görünmüyorsa veri kablosu,
port erişimi ve açık seri monitörleri kontrol edin. Yükleme hızı 115200 baud'dur.

Debug APK bu bilgisayarın standart Android debug anahtarıyla imzalanır. Başka
makinede oluşturulmuş APK ile imza uyuşmazlığı yaşanabilir; var olan uygulamayı
kaldırmak verilerini siler. Release çıktısı imzasızdır; imzalama anahtarı/parolası
bu projede oluşturulmaz veya depolanmaz. Store yayını bu komutun kapsamı dışındadır.

`android signed`, proje dışında tutulan mevcut yayın anahtarını kullanır;
eksik anahtar varsa hata verir, yeni kimlik üretmez. Varsayılan konum
`$HOME/.local/share/spectra24/signing/` içindeki `release.p12` ve
`keystore.pass` dosyalarıdır; alias `spectra24` olur. Başka bir konum için
`SPECTRA_SIGNING_DIR`, alias için `SPECTRA_KEY_ALIAS` kullanılabilir.
Bu özel dosyalar dağıtılmaz ve Git'e eklenmez. İmzalama sonrasında APK imzası
ve ZIP hizalaması doğrulanır. Anahtarı ve parolayı güvenli, ayrı bir yedekte
korumak sonraki güncellemeler için gereklidir.

## Test katmanları

```bash
./build.sh test                     # 442 Java kontrolü + Python testleri
./build.sh test-full                # aynı kaynak kontrolleri + firmware ve APK
./build.sh emulator                 # Spectra24_API35 AVD + DEMO simülatör
./build.sh test-models              # açık emülatörde Android model kontrolleri
python3 tools/ui_v35_smoke.py       # açık DEMO emülatöründe arayüz regresyon testi
./build.sh stop                     # yalnız SPECTRA test AVD'sini durdur
./build.sh test-full --live         # SPECTRA-24 ağına bağlı bilgisayardan cihaz testi
```

Emülatör modu gerçek RF ölçmez, veriler etiketli sentetik test verisidir.
Arayüz testlerini paralel çalıştırmayın. Donanım testi `--live` olmadan
başlatılmaz; `test` USB yüklemesi veya SDK indirmesi yapmaz.
`test-models` varsayılan olarak `emulator-5554` kullanır; farklı port için
`SPECTRA_TEST_SERIAL=emulator-5556 ./build.sh test-models` kullanılabilir.

## Temizlik

```bash
./build.sh clean --dry-run
./build.sh clean                    # listeyi gösterir, onay ister
./build.sh clean --yes              # aynı hedefler, etkileşimsiz
```

Yalnız proje içindeki bilinen derleme/cache klasörleri, `.run/`, üretilmiş
APK/BIN, kaynak paketleri ve test ekran görüntüleri çöp kutusuna taşınır.
`gio` yoksa kalıcı silmeye geçilmez. Sembolik bağlantılar reddedilir.
Test PID kaydı varsa önce `./build.sh stop` gerekir. Derleme/emülatör çalışırken
temizlik yapmayın. Kaynak, SDK, kullanıcı genel Gradle/PlatformIO önbelleği ve
local.properties korunur. Sonraki derleme proje bağımlılıklarını yeniden hazırlayabilir.

## Dağıtım kontrol listesi

- `./build.sh test` ve mümkünse `./build.sh test-full` çalıştırın.
- [LICENSE.md](../LICENSE.md) dosyasını, Font Awesome lisansını ve
  [üçüncü taraf bildirimlerini](../THIRD_PARTY_NOTICES.md) koruyun.
  Projenin ticari olmayan kullanım koşullarına uyun; [lisans özetini](LISANS.md) okuyun.
- Kişisel ağ adı, parola, cihaz adresi, kayıt ve ekran görüntülerini gözden geçirin.
  `.gitignore` tek başına sır tarayıcı değildir; önceden Git'e eklenen dosyaları silmez.
- `./build.sh package` ile temiz kaynak arşivini üretip içeriğini inceleyin.
  Paketleyici yalnız tanımlı kaynak yollarını/uzantılarını dahil eder. Yeni bir
  kaynak türü eklediyseniz `tools/project_bundle.py` listesini güncelleyin.
- Arşiv `local.properties`, anahtar dosyaları, SDK ve build çıktıları içermez.
  Gömülü özel anahtar başlığına yönelik temel kontrol vardır; tam güvenlik taraması değildir.
- GitHub'a kaynakları koyun; kurulum dosyalarını ayrı release eki olarak dağıtın.
  Oluşturulan `.sha256` dosyası bütünlüğü kontrol eder, kimlik doğrulayan imza değildir.

Eski kök `build_android.sh`, `test.sh`, `run_android_test.sh` vb. komutların
uygulamaları artık `scripts/` altındadır. Yeni ortak giriş noktası `./build.sh`.

## Sürüm ve güncelleme

Görünen Android sürümü ve firmware bildirimi **1.0.0**'dır. Android'in dahili
`versionCode` değeri **10** tutulur: daha önceki geliştirme APK'larının üzerine
aynı imzayla güncelleme yapılabilmesi için bu sayaç geriye alınmaz. Protokol
sürümü **2** olarak kalır; ürün sürümüyle aynı kavram değildir.

## Sık karşılaşılan durumlar

<details>
<summary><strong>SDK bulunamadı veya Java hatası</strong></summary>

`./build.sh doctor` çalıştırın. JDK 17 önerilir; Java ve javac erişilebilir
olmalıdır. SDK kuruluysa ortam değişkenlerini kontrol edin; yoksa
`./build.sh setup` kullanın.

</details>

<details>
<summary><strong>APK güncellemesinde imza uyuşmazlığı</strong></summary>

Önceki APK ile aynı imzalama anahtarını kullanın. Farklı bilgisayarların debug
anahtarları farklı olabilir. Uygulamayı kaldırmak kayıtlı ayar ve günlükleri
siler; bunu otomatik bir çözüm olarak uygulamayın.

</details>

<details>
<summary><strong>ESP32 Wi-Fi ağı internetsiz görünüyor</strong></summary>

Bu beklenen davranıştır; ESP32 doğrudan bağlantısı yerel ölçüm içindir.
Android'in bağlantıyı koruma isteğini onaylayın.
[Bağlantı rehberine](../android-app/README.md) bakın.

</details>
