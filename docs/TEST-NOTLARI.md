# Test ve sürüm notları

> Bu dosya tarihsel test kaydıdır. Eski komut ve çıktı isimleri o günkü düzeni
> anlatır; güncel komutlar [BUILD.md](BUILD.md) içindedir. APK/BIN ve test
> görselleri kaynak arşivine dahil edilmez; gerektiğinde yeniden üretilir.

## GitHub hazırlığı — 19 Eylül 2026

- Yardımcı komutlar `scripts/` altına taşındı. Kökteki `build.sh`;
  menü, debug/release APK, firmware, test, SDK, emülatör, yükleme,
  güvenli temizlik ve kaynak paketleme komutlarını birleştirir.
- Temiz önbellekten `./build.sh all` geçti: ESP32 firmware ve Android debug
  APK derlendi. `test-full` de başarılı. Bu işlem ESP32'ye yükleme yapmadı;
  fiziksel seri port bağlı değildi. Uygulama/firmware davranış kodu değiştirilmedi.
- 442 Java kontrolü, Python simülatör/ortak-radyo kontrolleri ve 8 yeni
  paketleme/temizlik testi geçti. Yeni testler symlink, yol dışına çıkma,
  kaynak dışlama, özel anahtar başlığı, checksum ve temizlik hedeflerini kapsar.
- Kaynak paketi ayrı geçici klasöre açıldı. Kişisel local.properties olmadan
  kaynak testleri ve `android release` derlemesi geçti; imzasız release APK üretildi.
- Menü açılışı/çıkışı, yardım, hatalı seçeneklerin reddi ve temizlik önizlemesi
  denendi. `doctor`, mevcut JDK 21 / SDK 35 / PlatformIO kurulumunu buldu.
- `emulator → test-models → stop` zinciri aynı shell oturumunda geçti:
  etiketli DEMO TCP bağlantısı ve waterfall doğrulandı, 63 Android kontrolü
  geçti, yalnız SPECTRA simülatörü/AVD kapatıldı. Ayrı araç oturumundaki ilk
  model testi, emülatör artık çalışmadığı için başlayamadı; aynı oturumda yeniden denendi.
- Gradle'da SDK XML sürüm uyarısı ve mevcut deprecated API notları var;
  debug/release derlemeleri başarılı. SDK sürümleri bu düzenlemede değiştirilmedi.
- Eski ve doğrulamada yeniden üretilen build/cache, APK/BIN, log ve görseller
  çöp kutusuna taşındı; kurtarılabilir. SDK ve kullanıcı genel önbellekleri korunur.
  `release/` altında yalnız README ve temiz kaynak arşivi/özeti bırakılır.
- README, derleme rehberi, üçüncü taraf bildirimleri ve Git dışlama kuralları
  yenilendi. GitHub'a gönderim yapılmadı; kaynak kod lisansı sahibi tarafından seçilmeli.

Yeni dosya düzeninde bu belge `docs/` altında, ekran görüntüleri
`release/screenshots/` altındadır. Aşağıdaki geçmiş sürüm notlarının eski
görselleri temizlikte çöp kutusuna taşındı; test kaynakları korunmuştur.

## v3.5.0 — 18 Eylül 2026

- Android versionCode 9 ve firmware 3.5.0 derlendi. IDE/masaüstü firmware
  kaynakları aynı. İlk USB yüklemesi seri akış hatası verdi; çip kontrolünden
  sonra ikinci yükleme başarılı ve flash hash doğrulaması geçti.
- Gerçek cihaz seri günlüğünde nRF24 READY, kalibrasyon tamamlandı,
  4 örnek/kanalda 5,9 tarama/sn ve 16 Wi-Fi erişim noktası gözlendi.
  Bunlar telefona ulaşan hız veya AirTag saha testi değildir.
- Tarama seçimi artık sadece samples alanını anında gönderir. Wi-Fi komut
  yazımı ana thread'de NetworkOnMainThreadException üretiyordu; sıralı, 64
  elemanla sınırlı arka plan kuyruğuna taşındı. Eski oturumun komutları yeni
  sokete yazılmaz. Bu hata uçtan uca testte yakalandı ve düzeltildi.
- Aynı TCP bağlantısında 12/8/4 seçimlerinin her biri kaydedildi/onaylandı;
  `tools/ui_v35_smoke.py` yeniden bağlantı oluşmadığını doğruladı. Wi-Fi ve
  BLE ortak ayar komutunu kullanır; fiziksel telefonda BLE anlık ayar testi yapılmadı.
- AirTag / Find My, iBeacon ve BLE sensör kategori izolasyonu test edildi.
  Yalnız Apple şirket kodu taşıyan örnek, Find My filtresine alınmadı.
  Ham anahtarlar değil, en fazla dört bayt format başlığı aktarılır.
  Eşleşme model/sahip/takip edilme kanıtı değildir; gerçek AirTag saha testi yok.
- 172 protokol + 225 akış/radar + 18 geçmiş + 13 metadata + 14 imza kontrolü,
  63 Android model/izin/UI kontrolü ve `./test.sh --android` geçti.
- CSV işlemi Android dosya seçiciyi açtı ve iptal dönüşü çalıştı. Son düzeltmeden
  sonraki testlerde AndroidRuntime hata kaydı yok. Görseller
  `release/screenshots/` altında; hepsi açıkça DEMO etiketlidir.
- Belgeler docs/, görseller release/screenshots/ altına ayrıldı. Eski PNG'ler,
  iki geçici test PNG'si, Python bytecode önbelleği, eski firmware çıktı kopyası,
  iki projedeki altı boş örnek README çöp kutusuna taşındı; geri alınabilir.
  Boş include/lib/test dizinleri kaldırıldı. Kaynaklar, testler, SDK ve derleme
  bağımlılıkları korundu. Güncel firmware çıktısı artık kök release/ altındadır.
- APK SHA-256: `8197e11fddc7917d0e8c2d7d31ddbb0d4a64dfcd1f194857e3bd99eb33ab3773`
- Yüklenen IDE firmware / release kopyası SHA-256: `70c99484decfb5ec16f35ae2a6018eec7358bc0af1133855718b6cb4f1cc8f71`
  Masaüstünde bağımsız derlemenin hash'i farklıdır (derleme konumu/zamanı);
  kaynaklar byte-byte karşılaştırılarak eşit doğrulandı.

## v3.4.1 — 18 Eylül 2026

- `release/SPECTRA24-3.4.1.apk`, versionCode 8. ESP32 firmware değişmedi;
  bu düzeltme için yalnızca Android uygulamasını güncelleyin.
- Tam ekran, küçük karttaki 96 satırı ölçekleyip büyütmek yerine hücre
  yüksekliğini ve frekans sütunu genişliğini korur. Ek yükseklik eski gerçek
  ölçümleri gösterir. Alttaki eğri yüksekliği de kartla aynı kalır.
- Görünür satır sayısı ekran yüksekliğine göre hesaplanır; üstte görünen
  satır / kapasite ve toplam kayıt sayısı bulunur. Ring tamponu 512 ölçümdür;
  henüz birikmemiş geçmiş boş kalır. Bunlar RF taramalarıdır, paket sayısı değildir.
- Tam ekran sürükleme sınırı yeni satır sayısına uyar. Küçültünce 96 satıra
  ve önceki hücre boyutuna dönülür; tampon silinmez. İki sayfa da ortak kodu kullanır.
- 172 protokol + 225 akış/radar + 18 geçmiş + 13 metadata kontrolü geçti.
  Geçmiş testleri değişken görünüm kapasitesini, sınırları ve boş geçmişi kapsar.
- 53 Android model/izin/UI kontrolü geçti. Hücre yüksekliği ve genişliği,
  96. satırdan sonraki gerçek ölçümün bitmap'e yazılması, tam ekran sürükleme,
  96 satıra dönüş, 512 kayıt sınırı ve çok uzun ekranda eğrinin büyümemesi denetlendi.
- Son APK üzerinde `tools/ui_v341_smoke.py` geçti: iki sayfada büyüt/küçült,
  geçmiş sürükleme, duraklat/canlı ve geri. Görseller `release/*v341.png`;
  DEMO test kaynağıdır, fiziksel radyo ölçümü değildir. Emülatörde Genel
  kartta 96, tam ekranda 403 satırlık alan görüldü; telefon ekranına göre değişir.
  Fiziksel telefon testi yapılmadı. AndroidRuntime hata kaydı görülmedi.
- APK SHA-256: `adb0f2b723bf9994cf994f2ea81ae590bac987435eeeec31a074a444a5ede8b1`

## Önceki v3.4.0 — 18 Eylül 2026

- Android 3.4.0 / versionCode 7 derlendi. Kurulum dosyası
  `release/SPECTRA24-3.4.0.apk`; `SPECTRA24-debug.apk` aynı dosyanın uyumluluk kopyasıdır.
  Ayarlar ekranında **SPECTRA 3.4.0 · özel arayüz** görünmelidir. Bağlantı
  merkezindeki firmware sürümü, uygulamanın sürümü değildir.
- Firmware değiştirilmedi; mevcut 3.3.0 firmware için yeniden yükleme gerekmez.
- Hızlı profil seçimi hem turuncu vurgu hem seçili profil metniyle gösterilir.
  Yeniden bağlantıda gelen cihaz ayarları kaydedilmemiş seçimi ezmez.
  Seçimi cihaza uygulamak için Kaydet kullanılmalıdır.
- Cihaz ayrıntıları, olay geçmişi, takma ad, onay, ses ve tarama ayrıntısı
  seçimleri uygulamaya özel koyu/turuncu pencerelerdir. Android izin onayı ve
  dosya seçici sistem tarafından çizilir; bunlar değiştirilmedi.
- Genel Waterfall ve Spektrum kartlarında bilgi ve büyütme simgeleri eklendi.
  Tam ekran aynı canlı görünümü kullanır; geri/küçült ile geçmiş korunur.
  Rehber; noktalı görünümü, sıralı kanal örneklemesini, renkleri, veri kesintisini,
  ölçüm sınırlarını ve geçmişi sürüklemeyi açıklar. Boşluklar sahte verilerle doldurulmaz.
- BLE şirket kimliği 76, yerel Bluetooth SIG sözlüğünde Apple, Inc. olarak
  çözümlenir; bu bir iPhone/model/gerçek cihaz adı tespiti değildir. Bazı standart
  servis UUID'leri ve appearance türleri de açıklanır. Yayınlanmayan adlar ve
  belirsiz alanlar gerçek bilgiymiş gibi üretilmez; Wi-Fi yerel MAC sınırı belirtilir.
- 172 protokol + 225 akış/radar + 12 geçmiş + 13 metadata kontrolü geçti.
  40 Android model/izin/UI kontrolü geçti; geç gelen ayarların profili ezmemesi
  ve tam ekrandan dönüşte aynı görünüm/geçmişin korunması dahil.
- `tools/ui_v34_smoke.py` geçti: rehber, iki tam ekran görünümü, geri/küçült,
  duraklat/canlı, Hassas vurgusu, özel ses/tarama seçimleri ve sürüm etiketi.
  `release/*v34.png` görselleri emülatörde DEMO kaynağıyla alınmıştır.
- Önceki `tools/ui_v33_smoke.py` de 3.4 APK üzerinde tekrar geçti:
  üç profil seçimi, bağlantı adları, özel cihaz/olay pencereleri ve filtreler.
  `*device-sheet-v33.png` ve `*journal-sheet-v33.png` bu denemede yenilendi.
  AndroidRuntime hata kaydı görülmedi.
- Fiziksel telefon bağlı olmadığından bu sürüm telefonda doğrulanmadı.
  APK'yı mevcut uygulamanın üzerine kurup sürüm etiketini kontrol edin;
  cihaz/olay pencereleri, profil seçimi ve iki grafiğin tam ekranını deneyin.
- APK SHA-256: `26bc1a201266203480aa7293c7f578465e1e6410db01306c7ad5395469f06f0b`

## Önceki v3.3.0 — 17 Eylül 2026

- APK 3.3.0 / versionCode 6 ve firmware 3.3.0 derlendi. Masaüstündeki
  firmware ile açık IDE projesinin `src/main.cpp` kaynakları aynı.
- Son firmware `/dev/ttyUSB0` üzerinden yüklendi; flash hash doğrulandı.
  Yükleme sonrası kalibrasyon tamamlandı, gerçek cihazda yaklaşık 5,9 tarama/sn
  ve 14 erişim noktası görüldü. Bu, telefona ulaşan kare hızının ölçümü değildir.
- 172 protokol + 225 akış/radar + 12 yeni gerçek-ölçüm geçmişi kontrolü geçti.
  32 Android model/izin/UI kontrolü geçti: profil vurgusu, özel değerlerde
  seçimin kalkması, yatay sürükleme ve geçmiş temizleme dahil.
- `./test.sh --android` başarılı; Wi-Fi/BLE güç politikası ve kısa kanal taraması
  kaynak regresyon koruması geçti. Bu koruma radyo saha testi değildir.
- v3.1 UI regresyon testi ve yeni `tools/ui_v33_smoke.py` başarılı:
  profil seçimi, bağlantı adları, uygulamaya özel cihaz/olay pencereleri,
  takma ad/onay pencereleri ve filtreler. Görseller `release/*v33.png` altında;
  tümü DEMO kaynağıdır, gerçek çevre verisi değildir.
- nRF24 donanım bandı 2400–2525 MHz olarak kaldı. Sürükleme frekansı
  genişletmez; 512 ölçümlük gerçek zaman geçmişini, 96 satırın boyutunu
  değiştirmeden gösterir. Sağa: geçmiş; sola: yeni; çift dokun: canlı.
- Wi-Fi envanteri 120 ms tek-kanal ziyaretleri / en az 550 ms ev-kanalı
  aralarıyla ilerler. BLE taraması aynı anda başlamaz. Bağlantı kurulurken
  4 saniyelik envanter taraması bekleme aralığı vardır.
- Uygulama mevcut doğrudan Wi-Fi bağlantısını düğmeye tekrar basılınca
  kapatmaz; eski ağ callback'leri yeni bağlantıyı kapatamaz. Ağ hazır olunca
  TCP beklemesi uyandırılır. Android onay süresi uygulamanın kontrolünde değildir.
- Titreşim, önizleme ve ESP32 alarm-zinciri testine eklendi. API 33+ için
  VibrationAttributes.USAGE_ALARM, eski sürümler için AudioAttributes.USAGE_ALARM
  kullanılır. Sistem DND/titreşim politikaları aşılmaz.
- Genel UI smoke testi de geçti: beş sekme, duraklatma, ses seçici, önizleme,
  servisle birlikte çıkış. Emülatörün VibratorManager kaydında uygulamadan
  gelen 562 ms ALARM titreşim isteği `finished` görüldü. Bu, telefonun
  motorunun fiziksel olarak hissedildiği anlamına gelmez. AndroidRuntime hata kaydı yoktu.
- Fiziksel telefon ADB ile bağlı değil. Telefonda uzun süreli Wi-Fi/BLE akışı,
  bağlantı süresinin önce/sonra karşılaştırması ve hissedilen titreşim ayrıca
  denenmelidir. Bilgisayarın Wi-Fi arayüzü olmadığından canlı TCP/UDP AP testi yapılmadı.
- APK SHA-256: `67d6932ac3a79bc3bae153c742b2d7362151e5170d82c4fa49bb2db8c9b1011b`
- Yüklenen firmware SHA-256: `b8e8a6d1f5c51a74e5a68bf947c6ce793b6dd7fe24e15241e1fabc79f81f57d2`

## Telefonda hızlı kontrol

1. `release/SPECTRA24-debug.apk` dosyasını mevcut uygulamanın üzerine kurun.
2. Bağlantı > Hızlı Wi-Fi; Android onayını verin. Elle parola: `spectrum24`.
3. 2 dakika akışı izleyin; Wi-Fi envanteri yenilenirken uzun duraksama var mı bakın.
4. Waterfall yaklaşık 20 saniye ölçüm biriktirdikten sonra sağa sürükleyin;
   GEÇMİŞ yazısını ve çift dokunmayla canlıya dönüşü kontrol edin.
5. Ayarlar > Hassas / Dengeli / Sakin seçin; yalnız seçili profil turuncu olmalı.
6. Titreşimi açıp Uyarıyı dene kullanın; kapatıp tekrar deneyin.
   Ardından bağlıyken ESP32 → telefon alarm zincirini test et düğmesini deneyin.

## Önceki v3.2.1 Wi-Fi bağlantı düzeltmesi — 16 Eylül 2026

- 3.2.0'da TCP istemcisi bağlanınca `WiFi.setSleep(false)` çağrılmasına
  yol açan döngü kaldırıldı. BLE açıkken sürekli `WIFI_PS_MIN_MODEM` kullanılır.
  Bu son sürümde eklenmiş bir regresyondu ve bildirilen ani kopmayla uyumludur.
  [Espressif ESP32-WROOM-32D hata kaydı](https://github.com/espressif/esp-idf/issues/5759).
- İki firmware projesi derlendi, kaynaklar eşitlendi. `/dev/ttyUSB0` üzerinden
  3.2.1 yüklendi ve flash hash doğrulaması geçti. Android 3.2.0 APK değişmedi;
  yalnızca bu düzeltme için uygulamayı yeniden kurmak gerekmez.
- 172 protokol + 225 radar/akış kontrolü ve yeni Wi-Fi/BLE güç politikası
  kaynak regresyon kontrolü geçti. Kaynak kontrolü canlı bağlantı testi değildir.
- Bu bilgisayarda Wi-Fi arayüzü/ADB bağlı telefon bulunmadığından telefondaki
  uzun süreli AP bağlantısı henüz doğrulanmadı; yeniden bağlantı saha kontrolü gerekir.
- Yüklenen IDE firmware SHA-256:
  `3fe09c103b039237d1facd0206a62098f706b921855a4eb8032383417f012ef2`.
- Güç politikası durum bilgisi sürücüden `esp_wifi_get_ps` ile doğrulanır.
  Arduino `setSleep` zaten seçili modda false döndürdüğünden bu dönüş değeri
  tek başına hata sayılmaz. Son firmware tekrar yüklendi, flash hash doğrulandı.

## Önceki v3.2 doğrulama — 16 Eylül 2026

## 3.2 değişiklikleri ve ölçülen sonuçlar

- Android sürümü 3.2.0 (versionCode 5). Ortak 48 dp dokunma alanı,
  içten boşluklu buton yüzeyleri, hizalı simgeler ve kompakt ikili eylem satırları.
- Radar yerleşimi MAC sırasına göre kararlı; numaralar nokta içinde,
  çakışmaları azaltan açı yerleşimi ve RSSI yumuşatma. Gerçek konum ölçülmez.
- Tepe izi, gerçek pencereye göre paket/sn grafiği, gözlenen bayt/sn,
  kare sıra boşluğu/CRC hatası ve firmware aktarım tanısı eklendi.
- Protokol: 172 kontrol; yeni radar/akış: 225 kontrol geçti.
- Son APK üzerinde 17 Android model/izin kontrolü geçti. Genel UI testi,
  v3.1 envanter/günlük testi ve v3.2 tepe izi/sıfırlama/hizalama/filtre testi
  başarılı. Beş sekme, ses seçici/önizleme ve servisle birlikte çıkış doğrulandı.
  Emülatör testlerinde AndroidRuntime hata kaydı görülmedi.
- `./test.sh --android` başarılı. 3.2 ekran görüntüleri release klasöründe;
  bunlar açıkça DEMO etiketli sentetik kaynaktır, canlı radyo testi değildir.
- Android ve iki PlatformIO projesi derlendi; firmware kaynakları eşit.
- 15 Eylül'deki 3.2 yüklemesi başarılı, flash hash doğrulandı. Ardından gerçek
  cihazda nRF24 READY, kalibrasyon tamamlandı, 15–16 erişim noktası görüldü.
  Seri portta 4 örnek/kanal ile 7,0 / 7,1 / 7,5 tam tarama/sn gözlendi.
  Önceki oturum 5,3–6,2 idi; bu kontrollü A/B veya telefon uçtan uca testi değildir.
- RF bekleme süresi azaltılmadı; görev yield sıklığı optimize edildi.
  Trafik gönderim hedefi 500 ms; oranlar gerçek `windowMs` ile hesaplanır.
- Son APK SHA-256:
  `928e2b45e0e9dfe553f0c3a64bb545c5622b195c593bf6428163da6dceabc378`.
- Yüklenen IDE firmware SHA-256:
  `b505d8fe554191c849ee916b6d3849645758fca50262cae4698a29e5758b0ea2`.
- Telefon üzerinde yeni 3.2 APK ile Wi-Fi/BLE uçtan uca hız, pil tüketimi
  ve yoğun envanter denemesi hâlâ saha kontrolü gerektirir.

## Önceki v3.1 doğrulama — 15 Eylül 2026

## Güncel sürümün durumu

- Wi-Fi düzeltmesi: eksik CHANGE_NETWORK_STATE izni eklendi. Emülatörde
  gerçek WifiNetworkSpecifier isteği Android tarafından kabul edildi
  (WIFI_REQUEST_REGISTERED); bu fiziksel AP bağlantısının testi değildir.
- Android üzerinde 17 model/izin testi geçti: aynı adresin ad önbelleği,
  farklı adreslerin birleştirilmemesi, DEMO/canlı ayrımı, kalıcı takma ad,
  ad kaynağı, BLE tür beyanı, olay tekrarı/100 kayıt sınırı ve OUI alarm koruması.
- 172 Java protokol testi geçti; firmware 3.1 derlendi.
- Son APK: 3.1.0 (versionCode 4). Genel arayüz testi ve v3.1 arayüz testi geçti:
  ad/tür başlığı, takma ad penceresi, BLE/Trafik filtreleri, ad sorgulama onayı,
  olay günlüğü filtresi ve silmeden önce onay. Ekran görüntüleri release dizininde.
- Firmware kaynakları açık IDE PlatformIO projesine eşitlendi; orada da derlendi.
- Firmware 3.1 açık IDE projesinden `/dev/ttyUSB0` üzerinden yüklendi;
  flash hash doğrulaması başarılı. Açılışta nRF24 READY, 20 taramalık
  kalibrasyon tamamlandı, 14 canlı erişim noktası ve 5,3–6,2 tarama/sn gözlendi.
  HTTP devre dışı. Bunlar seri port doğrulamalarıdır; telefon testi değildir.
- `flash_esp32.sh` artık hem masaüstündeki ana proje klasöründe hem açık
  IDE PlatformIO klasöründe bulunur: `./flash_esp32.sh /dev/ttyUSB0`.
  Yüklenen IDE firmware.bin SHA-256:
  `0ce947fb932d16c58b2fceddd81c8cc5452aa88dc513a4f1f83972000d5cee56`.
- Önceki v3.0 donanım testleri aşağıdadır; v3.1'e ait saha testi sayılmaz.

## Önceki v3.0 donanım doğrulaması

- Android debug APK: Gradle derlemesi başarılı.
- ESP32: masaüstü firmware ve açık IDE PlatformIO projesinde derleme başarılı.
- /dev/ttyUSB0 yüklemesi başarılı, flash hash doğrulandı.
- Yükleme sonrası gerçek cihaz: nRF24 READY, 20 sweep kalibrasyon tamamlandı;
  4 örnek/kanal ile 6,4 tam tarama/sn ve gerçek Wi-Fi erişim noktaları gözlendi.
- Üretimde kullanılan Java CRC ve BLE parça birleştiricisi: 172 kontrol geçti.
  Bozuk CRC, yanlış uzunluk, sınır dışı değerler, MTU boyutları, sıra dışı
  parçalar ve yarım mesajın yenisiyle değiştirilmesi kapsandı.
- PC DEMO simülatörünün kendi testi ve Android TCP bağlantı testi başarılı.
- Son APK üzerinde beş sekme, Font Awesome simgeleri, kaldırılan tanıtım/üst
  düğmeler, waterfall duraklatma, Android ses dosyası seçicisi, ses önizleme
  düğmesi ve Ayarlar'dan çıkış testi geçti. Çıkıştan sonra çalışan ölçüm servisi
  ve uygulama bildirimi kalmadı. Gerçek bir özel ses dosyasının oynatılması
  ve telefon hoparlöründen duyulması ayrıca saha kontrolü gerektirir.

## Telefonda kalan saha kontrolleri

1. Yeni APK'yı mevcut uygulamanın üzerine kurun.
2. Bağlantı > Akıllı bul ile BLE akışını, sonra SPECTRA WI-FI ile Android'in
   onay penceresini deneyin. Elle parola: spectrum24.
3. Her iki bağlantıda waterfall kare/sn değerini karşılaştırın.
   6,4 tarama/sn, seri portta ölçülen sampler hızıdır; telefon uçtan uca
   aktarım hızı olarak doğrulanmış bir değer değildir.
4. Wi-Fi kesildiğinde mevcut BLE yedeğinin devam ettiğini kontrol edin.
5. Ses dosyaları ekle ile kendi dosyanızı seçin, Sesi dene ve Kaydet kullanın.
6. ESP32 alarm zinciri testi ile TEST bildirimi/sesini kontrol edin.
   Bu işlem gerçek parazit algılamasını doğrulamaz.
7. Kendi SSID'nizi izleme kuralına ekleyip canlı envanterde eşleşmesini deneyin.

Gerçek jammer, gerçek Flock kamerası veya belirli bir telefon modelinin
Wi-Fi/BLE birlikte çalışma davranışı laboratuvarda doğrulanmadı.
Emülatör görüntülerindeki DEMO verisi canlı RF ölçümü değildir.
