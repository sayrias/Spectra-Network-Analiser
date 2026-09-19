#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
TOOLS_ONLY=false
WITH_EMULATOR=false
if [[ $# -gt 1 ]]; then
  echo 'Kullanım: ./build.sh setup [--tools-only|--with-emulator]' >&2
  exit 2
elif [[ "${1:-}" == "--tools-only" ]]; then
  TOOLS_ONLY=true
elif [[ "${1:-}" == "--with-emulator" ]]; then
  WITH_EMULATOR=true
elif [[ $# -gt 0 ]]; then
  echo 'Kullanım: ./build.sh setup [--tools-only|--with-emulator]' >&2
  exit 2
fi
TOOLS_VERSION="15859902"
TOOLS_ARCHIVE="commandlinetools-linux-${TOOLS_VERSION}_latest.zip"
TOOLS_URL="https://dl.google.com/android/repository/${TOOLS_ARCHIVE}"
TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

for command_name in curl unzip sha256sum java; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "HATA: '$command_name' kurulu değil." >&2
    exit 1
  fi
done

mkdir -p "$SDK_DIR/cmdline-tools"

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  TEMP_DIR="$(mktemp -d -t spectra-android-sdk.XXXXXX)"
  trap 'rm -rf -- "$TEMP_DIR"' EXIT

  echo "Android komut satırı araçları Google'dan indiriliyor..."
  curl -fL --retry 3 --output "$TEMP_DIR/$TOOLS_ARCHIVE" "$TOOLS_URL"
  echo "$TOOLS_SHA256  $TEMP_DIR/$TOOLS_ARCHIVE" | sha256sum --check --status || {
    echo "HATA: İndirilen Android araçlarının SHA-256 özeti uyuşmuyor." >&2
    exit 1
  }

  unzip -q "$TEMP_DIR/$TOOLS_ARCHIVE" -d "$TEMP_DIR/unpacked"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  cp -a "$TEMP_DIR/unpacked/cmdline-tools/." "$SDK_DIR/cmdline-tools/latest/"
fi

SDK_MANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if $TOOLS_ONLY; then
  echo "Android komut satırı araçları hazır: $SDK_MANAGER"
  echo "SDK lisanslarını okuyup onaylamak ve kalan paketleri kurmak için: $PROJECT_DIR/scripts/setup_android_sdk.sh"
  exit 0
fi

echo
echo "Google Android SDK lisansları gösterilecek. Metni okuyup seçimleri kendiniz onaylayın."
"$SDK_MANAGER" --sdk_root="$SDK_DIR" --licenses

echo "SDK 35, Build Tools ve ADB kuruluyor..."
"$SDK_MANAGER" --sdk_root="$SDK_DIR" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "platform-tools"
if $WITH_EMULATOR; then
  "$SDK_MANAGER" --sdk_root="$SDK_DIR" "emulator" "system-images;android-35;google_apis;x86_64"
fi

SDK_PROPERTY="${SDK_DIR//\\/\\\\}"
SDK_PROPERTY="${SDK_PROPERTY// /\\ }"
printf 'sdk.dir=%s\n' "$SDK_PROPERTY" > "$PROJECT_DIR/android-app/local.properties"

echo
echo "Android SDK hazır: $SDK_DIR"
echo "Şimdi çalıştırın: $PROJECT_DIR/scripts/build_android.sh"
echo "PC'de uygulamayı açmak için: $PROJECT_DIR/scripts/run_android_test.sh"
