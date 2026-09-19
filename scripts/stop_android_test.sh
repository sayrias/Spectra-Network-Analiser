#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_DIR/scripts/common.sh"
RUN_DIR="$PROJECT_DIR/.run"
find_sdk
ADB="$SDK_DIR/platform-tools/adb"

stop_simulator

if [[ -x "$ADB" ]]; then
  serial="$(find_test_emulator)"
  if [[ -n "$serial" ]]; then
    "$ADB" -s "$serial" emu kill >/dev/null
    echo "SPECTRA test emülatörü durduruldu: $serial"
  fi
  rm -f -- "$RUN_DIR/emulator.pid"
fi
