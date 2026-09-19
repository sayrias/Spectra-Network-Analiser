#!/usr/bin/env python3
"""SPECTRA 24 Android arayüzü için ESP32 protokol simülatörü."""

import argparse
import json
import math
import random
import select
import socketserver
import threading
import time


WIFI_NETWORKS = [
    {"ssid": "DEMO-AĞ-01", "bssid": "02:00:00:24:00:01", "ch": 1, "rssi": -46, "security": "DEMO"},
    {"ssid": "DEMO-AĞ-06", "bssid": "02:00:00:24:00:06", "ch": 6, "rssi": -61, "security": "DEMO"},
    {"ssid": "DEMO-AĞ-11", "bssid": "02:00:00:24:00:11", "ch": 11, "rssi": -74, "security": "DEMO"},
]

BLE_DEVICES = [
    {"name": "DEMO-BLE-01", "mac": "02:24:00:00:00:01", "rssi": -54, "packets": 42},
    {"name": "DEMO-BLE-02", "mac": "02:24:00:00:00:02", "rssi": -69, "packets": 18},
    {"name": "DEMO-FIND-MY", "mac": "02:24:00:00:00:03", "rssi": -58, "manufacturer": 76, "mfgPrefix": "4c001219", "mfgLength": 29, "signatureAgeMs": 0},
    {"name": "DEMO-IBEACON", "mac": "02:24:00:00:00:04", "rssi": -65, "manufacturer": 76, "mfgPrefix": "4c000215", "mfgLength": 25, "signatureAgeMs": 0},
    {"name": "DEMO-SENSOR", "mac": "02:24:00:00:00:05", "rssi": -70, "services": "181a"},
    {"name": "DEMO-APPLE-OTHER", "mac": "02:24:00:00:00:06", "rssi": -60, "manufacturer": 76, "mfgPrefix": "4c001005", "mfgLength": 9, "signatureAgeMs": 0},
]


class SimulatorState:
    def __init__(self):
        self.started = time.monotonic()
        self.settings = {"threshold": 30, "channels": 28, "hold": 4000,
                         "cooldown": 30000, "alerts": True, "samples": 4}
        self.alarm_demo = False
        self.lock = threading.Lock()

    def update_settings(self, message):
        with self.lock:
            for key, low, high in (("threshold",10,80),("channels",8,100),("hold",1500,20000),("cooldown",10000,300000),("samples",4,12)):
                self.settings[key] = max(low,min(high,int(message.get(key,self.settings[key]))))
            self.settings["alerts"] = bool(message.get("alerts", self.settings["alerts"]))

    def settings_copy(self):
        with self.lock:
            return dict(self.settings)

    def elapsed(self):
        return time.monotonic() - self.started


STATE = SimulatorState()


def gaussian(channel, center, width, height):
    return height * math.exp(-0.5 * ((channel - center) / width) ** 2)


def spectrum_frame(sequence, elapsed, force_alarm=False):
    phase = elapsed % 36.0
    values = []
    for channel in range(126):
        value = 4.0 + random.uniform(0, 8)
        value += gaussian(channel, 12, 4.5, 45 + 8 * math.sin(elapsed * 1.3))
        value += gaussian(channel, 37, 6.5, 27 + 6 * math.sin(elapsed * 0.8))
        value += gaussian(channel, 62, 5.0, 38 + 7 * math.sin(elapsed))
        if 10.0 <= phase < 18.0:
            value += gaussian(channel, 87, 13, 52)
        elif force_alarm or (STATE.alarm_demo and 18.0 <= phase < 28.0):
            value += 68 + 12 * math.sin(channel * 0.18 + elapsed * 2.0)
        values.append(max(0, min(100, round(value))))

    settings = STATE.settings_copy()
    affected = sum(value >= settings["threshold"] for value in values)
    candidate = affected >= settings["channels"]
    # Simülasyonun ilk 1,5 saniyesi aday, devamı alarm durumudur.
    alarm = candidate and (force_alarm or (STATE.alarm_demo and 19.5 <= phase < 28.0)) and settings["alerts"]
    peak = max(values)
    return {
        "type": "spectrum",
        "sequence": sequence & 0xFFFF,
        "flags": 1 | (2 if candidate else 0) | (4 if alarm else 0),
        "average": round(sum(values) / len(values)),
        "peak": peak,
        "peakChannel": values.index(peak),
        "affected": affected,
        "values": values,
    }


class SpectraHandler(socketserver.BaseRequestHandler):
    def send_json(self, value):
        payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n"
        self.request.sendall(payload.encode("utf-8"))

    def handle_command(self, message):
        action = message.get("action")
        if action == "hello":
            self.send_json({
                "type": "hello", "protocol": 2, "device": "SPECTRA-24-DEMO",
                "firmware": "synthetic-demo-2.0", "radio": True, "demo": True,
                "settings": STATE.settings_copy(),
            })
        elif action == "ping":
            self.send_json({"type": "pong", "uptime": round(STATE.elapsed() * 1000)})
        elif action == "settings":
            STATE.update_settings(message)
            print(f"[SETTINGS] samples={STATE.settings_copy()['samples']}", flush=True)
            self.send_json({"type": "saved", "settings": STATE.settings_copy()})

    def handle(self):
        address = self.client_address[0]
        print(f"[BAĞLANDI] Android istemcisi: {address}", flush=True)
        self.request.setblocking(False)
        received = b""
        sequence = 0
        next_spectrum = next_flow = next_inventory = 0.0
        alarm_was_active = False

        try:
            while True:
                readable, _, _ = select.select([self.request], [], [], 0.04)
                if readable:
                    chunk = self.request.recv(8192)
                    if not chunk:
                        break
                    received += chunk
                    while b"\n" in received:
                        raw, received = received.split(b"\n", 1)
                        try:
                            self.handle_command(json.loads(raw.decode("utf-8")))
                        except (ValueError, UnicodeDecodeError):
                            self.send_json({"type": "error", "message": "invalid_json"})

                now = time.monotonic()
                if now >= next_spectrum:
                    sequence += 1
                    frame = spectrum_frame(sequence, STATE.elapsed())
                    self.send_json(frame)
                    alarm_active = bool(frame["flags"] & 4)
                    if alarm_active and not alarm_was_active:
                        self.send_json({
                            "type": "alert", "active": True, "sequence": sequence,
                            "affected": frame["affected"], "avg": frame["average"],
                            "peak": frame["peak"], "classification": "broadband_energy",
                        })
                    elif alarm_was_active and not alarm_active:
                        self.send_json({"type": "alert", "active": False, "sequence": sequence})
                    alarm_was_active = alarm_active
                    next_spectrum = now + 0.20

                if now >= next_flow:
                    mgmt = random.randint(18, 64)
                    data = random.randint(75, 240)
                    ctrl = random.randint(8, 38)
                    self.send_json({"type": "flow", "mgmt": mgmt, "data": data,
                                    "ctrl": ctrl, "total": mgmt + data + ctrl})
                    next_flow = now + 1.0

                if now >= next_inventory:
                    self.send_json({"type": "wifi", "networks": WIFI_NETWORKS})
                    self.send_json({"type": "ble", "devices": BLE_DEVICES})
                    next_inventory = now + 8.0
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            print(f"[AYRILDI] Android istemcisi: {address}", flush=True)


class ThreadedServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def self_test():
    normal = spectrum_frame(1, 4.0)
    alarm = spectrum_frame(2, 22.0, force_alarm=True)
    assert len(normal["values"]) == 126
    assert all(0 <= value <= 100 for value in normal["values"])
    assert alarm["flags"] & 4
    assert alarm["affected"] >= STATE.settings_copy()["channels"]
    print("[OK] Açıkça işaretli PC DEMO kaynağı: 126 kanal ve test senaryosu")


def main():
    parser = argparse.ArgumentParser(description="SPECTRA 24 ESP32 protokol simülatörü")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=4211)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--alarm-demo", action="store_true",
                        help="sentetik geniş bant alarm senaryosunu da döngüye ekle")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    STATE.alarm_demo = args.alarm_demo

    with ThreadedServer((args.host, args.port), SpectraHandler) as server:
        print(f"SPECTRA 24 PC simülatörü {args.host}:{args.port} adresinde hazır.", flush=True)
        print("Android Emulator bu sunucuya 10.0.2.2:4211 ile bağlanır. Durdurmak için Ctrl+C.", flush=True)
        try:
            server.serve_forever(poll_interval=0.25)
        except KeyboardInterrupt:
            print("\nSimülatör durduruldu.", flush=True)


if __name__ == "__main__":
    main()
