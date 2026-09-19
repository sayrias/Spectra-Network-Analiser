package com.spectra.analyzer;

/** Bounded, real measurements only. Offset is measured from the newest row. */
final class SpectrumHistory {
    static final int CHANNELS = 126, CAPACITY = 512, VISIBLE = 96;
    private final byte[][] frames = new byte[CAPACITY][CHANNELS];
    private final long[] times = new long[CAPACITY];
    private int head = -1, size, offset, visibleRows = VISIBLE;
    void push(byte[] values, long time) {
        if (values == null || values.length != CHANNELS) return;
        head = (head + 1) % CAPACITY;
        System.arraycopy(values, 0, frames[head], 0, CHANNELS);
        times[head] = time;
        size = Math.min(CAPACITY, size + 1);
        if (offset > 0) offset = Math.min(maxOffset(), offset + 1);
    }
    void seek(int value) { offset = Math.max(0, Math.min(maxOffset(), value)); }
    int offset() { return offset; }
    int size() { return size; }
    void setVisibleRows(int value) {
        visibleRows = Math.max(1, Math.min(CAPACITY, value));
        seek(offset);
    }
    int maxOffset() { return Math.max(0, size - visibleRows); }
    byte[] row(int visibleRow) {
        int age = offset + visibleRow;
        return age < 0 || age >= size ? null : frames[(head - age + CAPACITY) % CAPACITY];
    }
    long time() { return size == 0 ? 0 : times[(head - offset + CAPACITY) % CAPACITY]; }
    void clear() { head = -1; size = offset = 0; }
}
