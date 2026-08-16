package com.val0x2f.chunklod.cache;

/** Small open-addressed primitive map used only by the cache journal index. */
public final class PrimitiveLongLongMap {
    @FunctionalInterface
    public interface Visitor {
        void visit(long key, long value);
    }

    private long[] keys;
    private long[] values;
    private byte[] used;
    private int mask;
    private int threshold;
    private int size;

    public PrimitiveLongLongMap(int expectedSize) {
        int capacity = 1;
        while (capacity < expectedSize * 2) capacity <<= 1;
        allocate(capacity);
    }

    public void put(long key, long value) {
        if (size >= threshold) rehash(keys.length << 1);
        int slot = slot(key);
        if (used[slot] == 0) {
            used[slot] = 1;
            keys[slot] = key;
            values[slot] = value;
            size++;
        } else {
            values[slot] = value;
        }
    }

    public long getOrDefault(long key, long fallback) {
        int slot = mix(key) & mask;
        while (used[slot] != 0) {
            if (keys[slot] == key) return values[slot];
            slot = (slot + 1) & mask;
        }
        return fallback;
    }

    public int size() {
        return size;
    }

    public void clear() {
        java.util.Arrays.fill(used, (byte) 0);
        size = 0;
    }

    public void forEach(Visitor visitor) {
        for (int i = 0; i < used.length; i++) if (used[i] != 0) visitor.visit(keys[i], values[i]);
    }

    private int slot(long key) {
        int slot = mix(key) & mask;
        while (used[slot] != 0 && keys[slot] != key) slot = (slot + 1) & mask;
        return slot;
    }

    private void rehash(int capacity) {
        long[] oldKeys = keys;
        long[] oldValues = values;
        byte[] oldUsed = used;
        allocate(capacity);
        for (int i = 0; i < oldUsed.length; i++) if (oldUsed[i] != 0) put(oldKeys[i], oldValues[i]);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new long[capacity];
        used = new byte[capacity];
        mask = capacity - 1;
        threshold = capacity * 3 / 5;
        size = 0;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (int) value;
    }
}
