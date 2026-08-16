package com.val0x2f.chunklod.cache;

/** Mutable only while owned by a pool or one worker; published entries are immutable by convention. */
public final class ChunkCacheEntry {
    public static final int COLUMN_COUNT = 16 * 16;

    private CacheKey key;
    private long revision;
    private int minBuildHeight;
    private final short[] heights = new short[COLUMN_COUNT];
    private final short[] colors = new short[COLUMN_COUNT];
    private final long[] solidMask = new long[4];
    private int crc32c;

    public void reset(CacheKey key, long revision, int minBuildHeight) {
        this.key = key;
        this.revision = revision;
        this.minBuildHeight = minBuildHeight;
        this.crc32c = 0;
    }

    public CacheKey key() { return key; }
    public long revision() { return revision; }
    public int minBuildHeight() { return minBuildHeight; }
    public short[] heights() { return heights; }
    public short[] colors() { return colors; }
    public long[] solidMask() { return solidMask; }
    public int crc32c() { return crc32c; }
    public void crc32c(int value) { crc32c = value; }
    public boolean isCurrent(long expectedRevision) { return revision == expectedRevision; }
}
