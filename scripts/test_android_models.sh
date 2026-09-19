#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/common.sh"
find_sdk
TEST_SERIAL="${SPECTRA_TEST_SERIAL:-emulator-5554}"
[[ "$TEST_SERIAL" == emulator-* ]] || { echo "Yalnızca test emülatörü desteklenir."; exit 1; }
"$SDK_DIR/platform-tools/adb" -s "$TEST_SERIAL" get-state >/dev/null
"$PROJECT_DIR/scripts/build_android.sh"
cd "$PROJECT_DIR/android-app"
./gradlew --no-daemon --console=plain assembleDebugAndroidTest
"$SDK_DIR/platform-tools/adb" -s "$TEST_SERIAL" install -r "$PROJECT_DIR/dist/SPECTRA24-debug.apk"
"$SDK_DIR/platform-tools/adb" -s "$TEST_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RESULT="$("$SDK_DIR/platform-tools/adb" -s "$TEST_SERIAL" shell am instrument -w com.spectra.analyzer.test/com.spectra.analyzer.ModelTests)"
printf '%s\n' "$RESULT"
[[ "$RESULT" == *"result=PASS:"* ]] || exit 1
