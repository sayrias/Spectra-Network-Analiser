#!/usr/bin/env bash
# SPECTRA 24: çalışma dizininden bağımsız geliştirme merkezi.
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$PROJECT_DIR/scripts/common.sh"

usage() {
  cat <<'HELP'
SPECTRA 24 · Derleme ve geliştirme merkezi

Kullanım: ./build.sh [komut] [seçenek]
  android [debug|release|signed]  APK derle (release imzasız, signed yayın anahtarı ister)
  firmware                  ESP32 derle, yükleme yapma
  all                       Debug APK + ESP32 derle
  flash [/dev/ttyUSB0] [--monitor]  ESP32'yi derle ve yükle
  test                      Donanımsız Java/Python kaynak testleri
  test-full [--live]         Firmware + APK + testler; --live gerçek cihaza bağlanır
  emulator                  APK + Android emülatörü + etiketli DEMO
  test-models               Açık test emülatöründe Android model testleri
  stop                      Bu projenin test simülatörünü/emülatörünü durdur
  setup [--tools-only|--with-emulator]  Linux SDK kurulumu (lisans onaylı)
  doctor                    Java / SDK / PlatformIO durumunu kontrol et
  package                   Paylaşılabilir kaynak arşivi ve SHA-256 oluştur
  clean [--dry-run|--yes]    Üretilen proje dosyalarını çöp kutusuna taşı
  help                      Bu yardım

Parametresiz terminalde menü açılır. Diğer ortamlarda yardım yazılır.
Derleme otomatik yükleme yapmaz. SDK kurulumu yalnız setup ile başlar.
HELP
}

no_args() { [[ $# -eq 0 ]] || { echo 'Bu komut ek seçenek kabul etmez.' >&2; exit 2; }; }

firmware() {
  find_pio
  "$PIO" run --project-dir "$PROJECT_DIR/firmware"
  mkdir -p "$PROJECT_DIR/dist"
  install -m 0644 "$PROJECT_DIR/firmware/.pio/build/esp32doit-devkit-v1/firmware.bin" \
    "$PROJECT_DIR/dist/spectra24-esp32.bin"
  echo "Hazır: $PROJECT_DIR/dist/spectra24-esp32.bin"
}

source_tests() {
  for script in "$PROJECT_DIR/build.sh" "$PROJECT_DIR/scripts/"*.sh; do bash -n "$script"; done
  echo '[OK] Bash sözdizimi'
  bash "$PROJECT_DIR/scripts/test_protocol.sh"
  python3 -B "$PROJECT_DIR/tools/test_wifi_coexistence.py"
  python3 -B "$PROJECT_DIR/tools/device_simulator.py" --self-test
  python3 -B "$PROJECT_DIR/tools/test_project_bundle.py"
  python3 -B "$PROJECT_DIR/tools/test_docs.py"
}

doctor() {
  local missing=0
  for tool in java javac python3; do
    if command -v "$tool" >/dev/null; then
      printf '[OK] %s: %s\n' "$tool" "$(command -v "$tool")"
    else
      printf '[EKSİK] %s\n' "$tool"; missing=1
    fi
  done
  if command -v java >/dev/null; then java -version 2>&1; fi
  if check_java; then echo '[OK] Java sürümü'; else missing=1; fi
  if (find_pio); then echo '[OK] PlatformIO'; else missing=1; fi
  find_sdk
  if [[ -f "$SDK_DIR/platforms/android-35/android.jar" && -d "$SDK_DIR/build-tools/35.0.0" ]]; then
    printf '[OK] Android SDK 35: %s\n' "$SDK_DIR"
  else
    echo '[EKSİK] Android SDK 35 / Build Tools 35.0.0; ./build.sh setup'; missing=1
  fi
  [[ -x "$SDK_DIR/emulator/emulator" ]] && echo '[OK] Emülatör aracı' || echo '[İSTEĞE BAĞLI] Emülatör aracı yok'
  [[ -w /dev/kvm ]] && echo '[OK] KVM erişimi' || echo '[İSTEĞE BAĞLI] KVM erişimi yok'
  return "$missing"
}

dispatch() {
  local command_name="${1:-help}"
  shift || true
  case "$command_name" in
    android) bash "$PROJECT_DIR/scripts/build_android.sh" "$@" ;;
    firmware) no_args "$@"; firmware ;;
    all) no_args "$@"; firmware; bash "$PROJECT_DIR/scripts/build_android.sh" debug ;;
    flash) bash "$PROJECT_DIR/scripts/flash_esp32.sh" "$@" ;;
    test) no_args "$@"; source_tests ;;
    test-full) bash "$PROJECT_DIR/scripts/test.sh" --android "$@" ;;
    emulator) no_args "$@"; bash "$PROJECT_DIR/scripts/run_android_test.sh" ;;
    test-models) no_args "$@"; bash "$PROJECT_DIR/scripts/test_android_models.sh" ;;
    stop) no_args "$@"; bash "$PROJECT_DIR/scripts/stop_android_test.sh" ;;
    setup) bash "$PROJECT_DIR/scripts/setup_android_sdk.sh" "$@" ;;
    doctor) no_args "$@"; doctor ;;
    clean|package) python3 -B "$PROJECT_DIR/tools/project_bundle.py" "$command_name" "$@" ;;
    help|-h|--help) usage ;;
    *) printf 'Bilinmeyen komut: %s\n' "$command_name" >&2; usage >&2; exit 2 ;;
  esac
}

if [[ $# -gt 0 ]]; then dispatch "$@"; exit; fi
if [[ ! -t 0 || ! -t 1 ]]; then usage; exit; fi
printf '\nSPECTRA 24 · Geliştirme merkezi\n\n'
printf '  1) Android debug APK\n  2) ESP32 firmware\n  3) İkisini derle\n  4) Kaynak testleri\n  5) Emülatörde dene\n  6) Araçları kontrol et\n  7) Paylaşılabilir kaynak paketi\n  8) Derleme çıktılarını temizle\n  9) Android SDK kur\n 10) ESP32 yükle\n 11) Android release APK (imzasız)\n 12) İmzalı yayın APK\n  0) Çıkış\n\n'
read -r -p 'Seçiminiz: ' choice || exit 0
case "$choice" in
  1) dispatch android ;; 2) dispatch firmware ;; 3) dispatch all ;;
  4) dispatch test ;; 5) dispatch emulator ;; 6) dispatch doctor ;;
  7) dispatch package ;; 8) dispatch clean ;; 9) dispatch setup ;;
  10)
    read -r -p 'Seri port (boş: otomatik): ' port || exit 0
    read -r -p 'ESP32 üzerine firmware yazılacak. Devam? [e/H]: ' answer || exit 0
    if [[ "$answer" == e || "$answer" == E ]]; then
      if [[ -n "$port" ]]; then dispatch flash "$port"; else dispatch flash; fi
    fi ;;
  11) dispatch android release ;;
  12) dispatch android signed ;;
  0|'') exit 0 ;;
  *) echo 'Geçersiz seçim.' >&2; exit 2 ;;
esac
