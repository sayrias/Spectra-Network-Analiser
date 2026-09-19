#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/common.sh"
VARIANT="${1:-debug}"
[[ $# -le 1 && "$VARIANT" =~ ^(debug|release|signed)$ ]] || {
  echo 'Kullanım: ./build.sh android [debug|release|signed]' >&2; exit 2;
}
if [[ "$VARIANT" == signed ]]; then
  exec bash "$PROJECT_DIR/scripts/sign_android.sh"
fi
check_java
find_sdk
[[ -f "$SDK_DIR/platforms/android-35/android.jar" ]] || {
  echo 'HATA: Android SDK 35 bulunamadı. ./build.sh setup çalıştırın.' >&2; exit 1;
}
APP_DIR="$PROJECT_DIR/android-app"
OUTPUT_DIR="$PROJECT_DIR/dist"
if [[ "$VARIANT" == debug ]]; then
  TASK=assembleDebug
  APK_SOURCE="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
  APK_NAME=SPECTRA24-debug.apk
else
  TASK=assembleRelease
  APK_SOURCE="$APP_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
  APK_NAME=SPECTRA24-release-unsigned.apk
fi
(cd "$APP_DIR" && ./gradlew --no-daemon --console=plain "$TASK")
[[ -s "$APK_SOURCE" ]] || { echo 'HATA: APK bulunamadı.' >&2; exit 1; }
mkdir -p "$OUTPUT_DIR"
install -m 0644 "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
echo "APK hazır: $OUTPUT_DIR/$APK_NAME"
if [[ "$VARIANT" == release ]]; then
  echo 'Bu APK imzasızdır; telefona kurulmadan/yayımlanmadan önce kendi anahtarınızla imzalanmalıdır.'
else
  echo 'Debug APK test içindir; bu bilgisayarın Android debug anahtarıyla imzalanır.'
fi
