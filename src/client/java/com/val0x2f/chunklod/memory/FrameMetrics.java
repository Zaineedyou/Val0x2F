package com.val0x2f.chunklod.memory;

/** Allocation-free rolling metrics; CSV/export and JFR correlation can be added on top. */
public final class FrameMetrics {
    private final long[] frameStartNanos;
    private final long[] frameDurationNanos;
    private int cursor;
    private long tickStartNanos;
    private int lodVisible;
    private int lodDrawCalls;
    private int droppedParticles;

    public FrameMetrics(int samples) {
        frameStartNanos = new long[samples];
        frameDurationNanos = new long[samples];
    }

    public void beginTick() {
        tickStartNanos = System.nanoTime();
    }

    public void endFrame(int visible, int drawCalls) {
        long now = System.nanoTime();
        frameStartNanos[cursor] = tickStartNanos;
        frameDurationNanos[cursor] = now - tickStartNanos;
        lodVisible = visible;
        lodDrawCalls = drawCalls;
        cursor = (cursor + 1) % frameStartNanos.length;
    }

    public void incrementDroppedParticles() { droppedParticles++; }
    public long lastFrameNanos() { return frameDurationNanos[(cursor - 1 + frameDurationNanos.length) % frameDurationNanos.length]; }
    public int lodVisible() { return lodVisible; }
    public int lodDrawCalls() { return lodDrawCalls; }
    public int droppedParticles() { return droppedParticles; }
}
