package com.spectra.analyzer;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Shared, Android-independent validation for Wi-Fi and BLE. */
final class WireProtocol {
    static int crc16(byte[] bytes, int length) {
        int crc = 0xffff;
        for (int i = 0; i < length; i++) {
            crc ^= (bytes[i] & 255) << 8;
            for (int bit = 0; bit < 8; bit++)
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
        }
        return crc;
    }

    static boolean validSpectrum(byte[] p, int length) {
        if (p == null || length != 144 || p.length < length
                || p[0] != 'S' || p[1] != 'P' || p[2] != '2' || p[3] != '4'
                || (p[4] != 1 && p[4] != 2)) return false;
        if ((p[12] & 255) > 100 || (p[13] & 255) > 100
                || (p[14] & 255) >= 126 || (p[15] & 255) > 126) return false;
        for (int i = 16; i < 142; i++) if ((p[i] & 255) > 100) return false;
        return crc16(p, 142) == ((p[142] & 255) | ((p[143] & 255) << 8));
    }

    static final class FragmentAssembler {
        private final byte marker;
        private int id = -1;
        private byte[][] parts;
        FragmentAssembler(byte marker) { this.marker = marker; }

        synchronized byte[] add(byte[] frame) {
            if (frame == null || frame.length < 5 || frame.length > 184 || frame[0] != marker) return null;
            int nextId = frame[1] & 255, index = frame[2] & 255, total = frame[3] & 255;
            if (total == 0 || index >= total || (marker == 'S' && total > 9)) return null;
            if (nextId != id || parts == null || parts.length != total) {
                id = nextId;
                parts = new byte[total][];
            }
            parts[index] = Arrays.copyOfRange(frame, 4, frame.length);
            int size = 0;
            for (byte[] part : parts) {
                if (part == null) return null;
                size += part.length;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(size);
            for (byte[] part : parts) output.write(part, 0, part.length);
            parts = null;
            return output.toByteArray();
        }
    }
}
