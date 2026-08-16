package com.val0x2f.chunklod.memory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Bounded direct-buffer arena. A budget miss drops optional LOD work instead of growing native memory. */
public final class BufferPool {
    private final long maxBytes;
    private final Map<Integer, ArrayDeque<ByteBuffer>> buckets = new HashMap<>();
    private long cachedBytes;
    private long reservedBytes;
    private long hits;
    private long misses;
    private long rejected;

    public BufferPool(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public synchronized ByteBuffer acquireBytes(int minimumBytes) {
        int size = bucketSize(minimumBytes);
        ArrayDeque<ByteBuffer> bucket = buckets.get(size);
        ByteBuffer buffer = bucket == null ? null : bucket.pollFirst();
        if (buffer != null) {
            cachedBytes -= size;
            hits++;
            buffer.clear();
            return buffer;
        }
        misses++;
        if (reservedBytes + size > maxBytes) {
            rejected++;
            return null;
        }
        reservedBytes += size;
        return ByteBuffer.allocateDirect(size);
    }

    public synchronized void release(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) return;
        int size = bucketSize(buffer.capacity());
        buffer.clear();
        if (cachedBytes + size <= maxBytes) {
            buckets.computeIfAbsent(size, ignored -> new ArrayDeque<>()).offerFirst(buffer);
            cachedBytes += size;
        } else {
            // Drop the last reference; the JVM may reclaim it later. Make the arena available now.
            reservedBytes = Math.max(0L, reservedBytes - size);
        }
    }

    public synchronized void trim() {
        buckets.clear();
        cachedBytes = 0L;
        reservedBytes = 0L;
    }

    public synchronized long hits() { return hits; }
    public synchronized long misses() { return misses; }
    public synchronized long rejected() { return rejected; }
    public synchronized long cachedBytes() { return cachedBytes; }
    public synchronized long reservedBytes() { return reservedBytes; }

    private static int bucketSize(int requested) {
        int bucket = 256;
        while (bucket < requested && bucket < (1 << 24)) bucket <<= 1;
        return Math.max(bucket, requested);
    }
}
