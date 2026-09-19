# Derleme ve geliştirme rehberi

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
| `./build.sh android` | `release/SPECTRA24-debug.apk` |
| `./build.sh android release` | `release/SPECTRA24-release-unsigned.apk` |
| `./build.sh firmware` | `release/spectra24-esp32.bin` |
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

## Paylaşım kontrol listesi

- `./build.sh test` ve mümkünse `./build.sh test-full` çalıştırın.
- Kaynak kod için proje sahibinin seçtiği `LICENSE` / `LICENSE.md` dosyasını ekleyin.
  Font Awesome'un mevcut lisansını ve üçüncü taraf bildirimlerini koruyun.
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
