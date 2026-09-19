#!/usr/bin/env bash
# Çağıran dosya PROJECT_DIR değişkenini proje köküne ayarlar.
find_pio() {
  if command -v pio >/dev/null 2>&1; then
    PIO="$(command -v pio)"
  elif [[ -x "$HOME/.platformio/penv/bin/pio" ]]; then
    PIO="$HOME/.platformio/penv/bin/pio"
  else
    echo 'HATA: PlatformIO Core gerekli (pio komutu veya VS Code PlatformIO kurulumu).' >&2
    return 1
  fi
}
find_sdk() {
  SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/android-app/local.properties" ]]; then
    SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/android-app/local.properties" | sed 's/\\ / /g' | head -n 1)"
  fi
  SDK_DIR="${SDK_DIR:-$HOME/Android/Sdk}"
  export ANDROID_SDK_ROOT="$SDK_DIR" ANDROID_HOME="$SDK_DIR"
}
check_java() {
  local java_major
  command -v java >/dev/null && command -v javac >/dev/null || {
    echo 'HATA: JDK gerekli; JDK 17 önerilir.' >&2; return 1;
  }
  java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
  if [[ ! "$java_major" =~ ^[0-9]+$ || "$java_major" -lt 17 ]]; then
    echo 'HATA: En az Java 17 gerekli; JDK 17 önerilir.' >&2; return 1
  fi
}

# Never select or close unrelated AVDs.
find_test_emulator() {
  local serial state avd
  while read -r serial state; do
    [[ "$serial" == emulator-* && "$state" == device ]] || continue
    avd="$("$ADB" -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true)"
    if [[ "$avd" == Spectra24_API35 ]]; then printf '%s\n' "$serial"; return; fi
  done < <("$ADB" devices | tail -n +2)
}

stop_simulator() {
  local pid command_line
  [[ -f "$PROJECT_DIR/.run/simulator.pid" ]] || return 0
  pid="$(<"$PROJECT_DIR/.run/simulator.pid")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    command_line="$(tr '\0' '\n' < "/proc/$pid/cmdline" 2>/dev/null || true)"
    if [[ "$command_line" != *"$PROJECT_DIR/tools/device_simulator.py"* ]]; then
      echo 'HATA: PID başka bir sürece ait; süreç durdurulmadı.' >&2; return 1
    fi
    kill "$pid"
    echo 'Bu projenin DEMO simülatörü durduruldu.'
  fi
  rm -f -- "$PROJECT_DIR/.run/simulator.pid"
}
