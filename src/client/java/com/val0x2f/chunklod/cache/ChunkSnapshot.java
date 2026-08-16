package com.val0x2f.chunklod.cache;

/** Reusable primitive-only hand-off from the client thread to a baking worker. */
public final class ChunkSnapshot {
    public static final int COLUMN_COUNT = 16 * 16;
    private final short[] heights = new short[COLUMN_COUNT];
    private final short[] colors = new short[COLUMN_COUNT];
    private final long[] solidMask = new long[4];
    private CacheKey key;
    private long revision;
    private int minBuildHeight;

    public void reset(CacheKey nextKey, long nextRevision, int nextMinBuildHeight) {
        key = nextKey;
        revision = nextRevision;
        minBuildHeight = nextMinBuildHeight;
        java.util.Arrays.fill(solidMask, 0L);
    }

    public void clearReferences() {
        key = null;
        revision = 0L;
        minBuildHeight = 0;
    }

    public CacheKey key() { return key; }
    public long revision() { return revision; }
    public int minBuildHeight() { return minBuildHeight; }
    public short[] heights() { return heights; }
    public short[] colors() { return colors; }
    public long[] solidMask() { return solidMask; }
}
