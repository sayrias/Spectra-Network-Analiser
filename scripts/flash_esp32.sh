#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$PROJECT_DIR/platformio.ini" ]]; then
  FIRMWARE_DIR="$PROJECT_DIR"
elif [[ -f "$PROJECT_DIR/firmware/platformio.ini" ]]; then
  FIRMWARE_DIR="$PROJECT_DIR/firmware"
else
  echo "HATA: Bu klasörde PlatformIO firmware projesi bulunamadı." >&2
  exit 1
fi
MONITOR_AFTER_UPLOAD=false
PORT=""

for argument in "$@"; do
  case "$argument" in
    --monitor) MONITOR_AFTER_UPLOAD=true ;;
    /dev/*) PORT="$argument" ;;
    -h|--help)
      echo "Kullanım: $0 [/dev/ttyUSB0] [--monitor]"
      exit 0
      ;;
    *) echo "HATA: Bilinmeyen seçenek: $argument" >&2; exit 2 ;;
  esac
done

if command -v pio >/dev/null 2>&1; then
  PIO="$(command -v pio)"
elif [[ -x "$HOME/.platformio/penv/bin/pio" ]]; then
  PIO="$HOME/.platformio/penv/bin/pio"
else
  echo "HATA: PlatformIO bulunamadı." >&2
  exit 1
fi

if [[ -z "$PORT" ]]; then
  shopt -s nullglob
  BY_ID=(/dev/serial/by-id/*)
  USB_PORTS=(/dev/ttyUSB* /dev/ttyACM*)
  if ((${#BY_ID[@]} == 1)); then
    PORT="${BY_ID[0]}"
  elif ((${#USB_PORTS[@]} == 1)); then
    PORT="${USB_PORTS[0]}"
  elif ((${#USB_PORTS[@]} > 1)); then
    echo "HATA: Birden fazla seri port bulundu; portu parametre olarak verin:" >&2
    printf '  %s\n' "${USB_PORTS[@]}" >&2
    exit 1
  else
    echo "HATA: /dev/ttyUSB* veya /dev/ttyACM* bulunamadı." >&2
    exit 1
  fi
fi

if [[ ! -e "$PORT" ]]; then
  echo "HATA: $PORT bulunamadı. ESP32'yi veri kablosuyla USB'ye bağlayın." >&2
  exit 1
fi
if [[ ! -r "$PORT" || ! -w "$PORT" ]]; then
  echo "HATA: $PORT için okuma/yazma izni yok. Kullanıcıyı dialout grubuna ekleyin." >&2
  exit 1
fi

echo "ESP32 portu: $PORT"
echo "Güvenli yükleme hızı: 115200 baud"
"$PIO" run --project-dir "$FIRMWARE_DIR" --target upload --upload-port "$PORT"

mkdir -p "$PROJECT_DIR/dist"
install -m 0644 "$FIRMWARE_DIR/.pio/build/esp32doit-devkit-v1/firmware.bin" \
  "$PROJECT_DIR/dist/spectra24-esp32.bin"

echo "Firmware başarıyla yüklendi."
if $MONITOR_AFTER_UPLOAD; then
  echo "Seri monitör açılıyor; çıkmak için Ctrl+C."
  "$PIO" device monitor --project-dir "$FIRMWARE_DIR" --port "$PORT" --baud 115200 --rts 0 --dtr 0
fi
