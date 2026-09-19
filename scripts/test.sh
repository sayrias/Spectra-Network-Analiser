#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
LIVE_TEST=false
ANDROID_BUILD=false
EMULATOR_TEST=false
for argument in "$@"; do
  case "$argument" in
    --live) LIVE_TEST=true ;;
    --android) ANDROID_BUILD=true ;;
    --emulator) EMULATOR_TEST=true ;;
    *) echo "HATA: Bilinmeyen seçenek: $argument" >&2; exit 2 ;;
  esac
done

pass() { printf '\033[32m[OK]\033[0m %s\n' "$1"; }
fail() { printf '\033[31m[HATA]\033[0m %s\n' "$1" >&2; exit 1; }
info() { printf '\033[36m[BİLGİ]\033[0m %s\n' "$1"; }

for script in "$PROJECT_DIR"/*.sh "$PROJECT_DIR/scripts/"*.sh; do
  bash -n "$script" || fail "Shell sözdizimi: $script"
done
pass "Komut dosyalarının sözdizimi"

if command -v pio >/dev/null 2>&1; then
  PIO="$(command -v pio)"
elif [[ -x "$HOME/.platformio/penv/bin/pio" ]]; then
  PIO="$HOME/.platformio/penv/bin/pio"
else
  fail "PlatformIO bulunamadı"
fi

"$PIO" run --project-dir "$PROJECT_DIR/firmware"
pass "ESP32 firmware derlemesi"

[[ -f "$PROJECT_DIR/android-app/app/src/main/AndroidManifest.xml" ]] || fail "AndroidManifest.xml eksik"
[[ -f "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/AnalyzerService.java" ]] || fail "AnalyzerService.java eksik"
[[ -f "$PROJECT_DIR/android-app/gradle/wrapper/gradle-wrapper.jar" ]] || fail "Gradle wrapper eksik"
pass "Android proje yapısı"
bash "$PROJECT_DIR/build.sh" test || fail "Kaynak ve paketleme testleri"

if $EMULATOR_TEST; then
  exec "$PROJECT_DIR/scripts/run_android_test.sh"
fi

if $ANDROID_BUILD; then
  "$PROJECT_DIR/scripts/build_android.sh" || fail "Android APK derlemesi"
  pass "Android APK derlemesi"
elif [[ -f "$HOME/Android/Sdk/platforms/android-35/android.jar" ]]; then
  "$PROJECT_DIR/scripts/build_android.sh" || fail "Android APK derlemesi"
  pass "Android APK derlemesi"
else
  info "Android SDK henüz kurulu değil; APK derlemesi atlandı. Kurulum: ./build.sh setup"
fi

if compgen -G '/dev/ttyUSB*' >/dev/null || compgen -G '/dev/ttyACM*' >/dev/null; then
  pass "ESP32 seri portu algılandı"
else
  info "ESP32 seri portu bağlı değil; donanım testi atlandı"
fi

if ! $LIVE_TEST; then
  info "PC'de tam arayüz testi: '$0 --emulator'"
  info "Gerçek cihaz protokol testi: bilgisayarı SPECTRA-24 ağına bağlayıp '$0 --live' çalıştırın."
  exit 0
fi

python3 - <<'PY'
import json
import socket
import sys

DEVICE = "192.168.4.1"
CONTROL_PORT = 4211
SPECTRUM_PORT = 4210

def crc16(data):
    crc = 0xFFFF
    for value in data:
        crc ^= value << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc

udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
udp.bind(("0.0.0.0", SPECTRUM_PORT))
udp.settimeout(8)

tcp = socket.create_connection((DEVICE, CONTROL_PORT), timeout=5)
tcp.sendall(b'{"action":"hello"}\n')
hello = tcp.makefile("r", encoding="utf-8").readline()
message = json.loads(hello)
if message.get("type") != "hello" or message.get("protocol") != 2:
    raise SystemExit("Geçersiz TCP hello yanıtı")
print("[OK] TCP kontrol kanalı:", message.get("device"), "firmware", message.get("firmware"))

packet, _ = udp.recvfrom(256)
if len(packet) != 144 or packet[:4] != b"SP24":
    raise SystemExit("Geçersiz UDP spectrum paketi")
expected = int.from_bytes(packet[142:144], "little")
if crc16(packet[:142]) != expected:
    raise SystemExit("UDP CRC doğrulaması başarısız")
print("[OK] UDP waterfall: 126 kanal, ortalama %d, tepe %d" % (packet[12], packet[13]))
tcp.close()
udp.close()
PY

pass "Canlı TCP/UDP protokolü"
