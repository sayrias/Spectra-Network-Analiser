#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/common.sh"
APP_ID="com.spectra.analyzer"
AVD_NAME="Spectra24_API35"
IMAGE_PACKAGE="system-images;android-35;google_apis;x86_64"
RUN_DIR="$PROJECT_DIR/.run"
RELEASE_DIR="$PROJECT_DIR/dist"

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/android-app/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/android-app/local.properties" | sed 's/\\ / /g' | head -n 1)"
fi
[[ -n "$SDK_DIR" ]] || SDK_DIR="$HOME/Android/Sdk"

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVDMANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"

for executable in "$ADB" "$EMULATOR" "$AVDMANAGER"; do
  if [[ ! -x "$executable" ]]; then
    echo "HATA: Android emülatör araçları eksik: $executable" >&2
    echo "Önce çalıştırın: ./build.sh setup --with-emulator" >&2
    exit 1
  fi
done

if [[ ! -d "$SDK_DIR/system-images/android-35/google_apis/x86_64" ]]; then
  echo "HATA: Android 15 emülatör imajı eksik." >&2
  echo "Önce çalıştırın: ./build.sh setup --with-emulator" >&2
  exit 1
fi

"$PROJECT_DIR/scripts/build_android.sh"
mkdir -p "$RUN_DIR" "$RUN_DIR/screenshots"

stop_simulator
nohup python3 "$PROJECT_DIR/tools/device_simulator.py" >"$RUN_DIR/simulator.log" 2>&1 &
SIMULATOR_PID=$!
printf '%s\n' "$SIMULATOR_PID" > "$RUN_DIR/simulator.pid"

if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
  echo "Android sanal telefonu ilk kez oluşturuluyor..."
  printf 'no\n' | "$AVDMANAGER" create avd --force --name "$AVD_NAME" \
    --package "$IMAGE_PACKAGE" --device "pixel_6"
fi

"$ADB" start-server >/dev/null
SERIAL="$(find_test_emulator)"
if [[ -z "$SERIAL" ]]; then
  echo "Android emülatörü açılıyor..."
  nohup "$EMULATOR" -avd "$AVD_NAME" -no-boot-anim -no-snapshot-save -gpu auto \
    >"$RUN_DIR/emulator.log" 2>&1 &
  printf '%s\n' "$!" > "$RUN_DIR/emulator.pid"

  for _ in $(seq 1 90); do
    SERIAL="$(find_test_emulator)"
    [[ -n "$SERIAL" ]] && break
    sleep 2
  done
fi

if [[ -z "$SERIAL" ]]; then
  echo "HATA: Emülatör ADB'de görünmedi. Kayıt: $RUN_DIR/emulator.log" >&2
  exit 1
fi

echo "Android'in açılması bekleniyor ($SERIAL)..."
for _ in $(seq 1 90); do
  BOOTED="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$BOOTED" == "1" ]] && break
  sleep 2
done
if [[ "${BOOTED:-}" != "1" ]]; then
  echo "HATA: Android 3 dakika içinde açılmadı. Kayıt: $RUN_DIR/emulator.log" >&2
  exit 1
fi

"$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0
"$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0
"$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0
"$ADB" -s "$SERIAL" install -r "$RELEASE_DIR/SPECTRA24-debug.apk" >/dev/null
"$ADB" -s "$SERIAL" shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
"$ADB" -s "$SERIAL" shell pm grant "$APP_ID" android.permission.BLUETOOTH_SCAN 2>/dev/null || true
"$ADB" -s "$SERIAL" shell pm grant "$APP_ID" android.permission.BLUETOOTH_CONNECT 2>/dev/null || true
"$ADB" -s "$SERIAL" shell am force-stop "$APP_ID"
"$ADB" -s "$SERIAL" shell am start -f 0x10008000 -n "$APP_ID/.MainActivity" --ez simulator true >/dev/null

APP_STARTED=false
for _ in $(seq 1 20); do
  if "$ADB" -s "$SERIAL" shell pidof "$APP_ID" >/dev/null 2>&1; then
    APP_STARTED=true
    break
  fi
  sleep 0.5
done
if ! $APP_STARTED; then
  echo "HATA: Uygulama başlatılamadı." >&2
  "$ADB" -s "$SERIAL" logcat -d -v brief -t 500 | \
    grep -Ei 'spectra|AndroidRuntime|FATAL EXCEPTION|Process: com\.spectra' >&2 || true
  exit 1
fi

echo "Canlı waterfall bağlantısı doğrulanıyor..."
CONNECTED=false
for _ in $(seq 1 20); do
  NOTIFICATION_STATE="$("$ADB" -s "$SERIAL" shell dumpsys notification --noredact 2>/dev/null || true)"
  if [[ "$NOTIFICATION_STATE" == *'DEMO bağlı · sentetik veri'* ]] && \
     grep -Fq '[BAĞLANDI] Android istemcisi:' "$RUN_DIR/simulator.log"; then
    CONNECTED=true
    break
  fi
  sleep 1
done
if ! $CONNECTED; then
  echo "HATA: Uygulama PC veri simülatörüne bağlanamadı." >&2
  echo "Simülatör kaydı:" >&2
  tail -n 30 "$RUN_DIR/simulator.log" >&2 || true
  "$ADB" -s "$SERIAL" logcat -d -v brief -t 300 | \
    grep -Ei 'spectra|AndroidRuntime|FATAL EXCEPTION' >&2 || true
  exit 1
fi

sleep 2
"$ADB" -s "$SERIAL" exec-out screencap -p > "$RUN_DIR/screenshots/spectra24-emulator.png"
echo
echo "[OK] SPECTRA 24 emülatörde çalışıyor: $SERIAL"
echo "[OK] Canlı waterfall ve TCP simülatör bağlantısı doğrulandı"
echo "[OK] PC veri simülatörü: PID $SIMULATOR_PID"
echo "[OK] Ekran görüntüsü: $RUN_DIR/screenshots/spectra24-emulator.png"
echo "Durdurmak için: $PROJECT_DIR/scripts/stop_android_test.sh"
