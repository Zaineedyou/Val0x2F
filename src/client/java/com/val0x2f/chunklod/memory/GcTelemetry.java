package com.val0x2f.chunklod.memory;

/** Allocation-free telemetry for correlating frame spikes with Val0x2F pool pressure. */
public final class GcTelemetry {
    private final long[] frameNanos;
    private final int[] snapshotPoolMisses;
    private final int[] directBufferRejects;
    private int cursor;
    private long lastFrameStart;

    public GcTelemetry(int samples) {
        frameNanos = new long[samples];
        snapshotPoolMisses = new int[samples];
        directBufferRejects = new int[samples];
    }

    public void beginFrame() {
        lastFrameStart = System.nanoTime();
    }

    public void endFrame(int snapshotMisses, long directRejects) {
        int slot = cursor++ % frameNanos.length;
        frameNanos[slot] = System.nanoTime() - lastFrameStart;
        snapshotPoolMisses[slot] = snapshotMisses;
        directBufferRejects[slot] = directRejects > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) directRejects;
    }

    public long lastFrameNanos() {
        return frameNanos[(cursor - 1 + frameNanos.length) % frameNanos.length];
    }

    public int latestSnapshotPoolMisses() {
        return snapshotPoolMisses[(cursor - 1 + snapshotPoolMisses.length) % snapshotPoolMisses.length];
    }

    public int latestDirectBufferRejects() {
        return directBufferRejects[(cursor - 1 + directBufferRejects.length) % directBufferRejects.length];
    }
}
