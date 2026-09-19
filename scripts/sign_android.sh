#!/usr/bin/env bash
# Uses an existing private key; never generates or replaces the publishing identity.
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/common.sh"
find_sdk
SIGNING_DIR="${SPECTRA_SIGNING_DIR:-$HOME/.local/share/spectra24/signing}"
KEYSTORE="$SIGNING_DIR/release.p12"
PASSWORD_FILE="$SIGNING_DIR/keystore.pass"
ALIAS="${SPECTRA_KEY_ALIAS:-spectra24}"
TOOLS_DIR="$SDK_DIR/build-tools/35.0.0"
for required_file in "$KEYSTORE" "$PASSWORD_FILE"; do
  [[ -f "$required_file" && ! -L "$required_file" ]] || {
    echo 'HATA: Mevcut yayın anahtarı/parola dosyası bulunamadı. İmzasız APK yayımlanmadı.' >&2
    exit 1
  }
done
for command_path in "$TOOLS_DIR/zipalign" "$TOOLS_DIR/apksigner"; do
  [[ -x "$command_path" ]] || { echo 'HATA: Android Build Tools 35.0.0 gerekli.' >&2; exit 1; }
done
bash "$PROJECT_DIR/scripts/build_android.sh" release
APP_VERSION="$(sed -n "s/^[[:space:]]*versionName '\([^']*\)'.*/\1/p" "$PROJECT_DIR/android-app/app/build.gradle")"
[[ "$APP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Geçersiz sürüm.' >&2; exit 1; }
STAGING_DIR="$(mktemp -d -t spectra-sign.XXXXXX)"
trap 'rm -f -- "$STAGING_DIR/aligned.apk" "$STAGING_DIR/signed.apk" "$STAGING_DIR/signed.apk.idsig"; rmdir -- "$STAGING_DIR"' EXIT
"$TOOLS_DIR/zipalign" -p -f 4 "$PROJECT_DIR/dist/SPECTRA24-release-unsigned.apk" "$STAGING_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
  --ks-pass "file:$PASSWORD_FILE" \
  --v4-signing-enabled false --out "$STAGING_DIR/signed.apk" "$STAGING_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" verify --verbose --print-certs "$STAGING_DIR/signed.apk"
"$TOOLS_DIR/zipalign" -c -v 4 "$STAGING_DIR/signed.apk" >/dev/null
install -m 0644 "$STAGING_DIR/signed.apk" "$PROJECT_DIR/dist/SPECTRA24-v$APP_VERSION.apk"
(cd "$PROJECT_DIR/dist" && sha256sum "SPECTRA24-v$APP_VERSION.apk" > SHA256SUMS)
echo "İmzalı ve doğrulanmış APK: $PROJECT_DIR/dist/SPECTRA24-v$APP_VERSION.apk"
