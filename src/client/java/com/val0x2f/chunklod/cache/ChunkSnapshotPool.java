package com.val0x2f.chunklod.cache;

import java.util.concurrent.ArrayBlockingQueue;

/** Bounded reusable snapshot pool; a full pool drops low-priority cache work. */
public final class ChunkSnapshotPool {
    private final ArrayBlockingQueue<ChunkSnapshot> free;
    private int misses;

    public ChunkSnapshotPool(int capacity) {
        free = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < capacity; i++) free.offer(new ChunkSnapshot());
    }

    public ChunkSnapshot acquire() {
        ChunkSnapshot snapshot = free.poll();
        if (snapshot == null) misses++;
        return snapshot;
    }

    public void release(ChunkSnapshot snapshot) {
        snapshot.clearReferences();
        free.offer(snapshot);
    }

    public int misses() { return misses; }
    public int available() { return free.size(); }
}
