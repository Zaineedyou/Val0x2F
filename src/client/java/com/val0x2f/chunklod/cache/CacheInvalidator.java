package com.val0x2f.chunklod.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Revision tokens invalidate stale worker results without synchronously touching disk. */
public final class CacheInvalidator {
    private final ConcurrentHashMap<Long, AtomicLong> revisions = new ConcurrentHashMap<>();

    public long revision(ChunkPos pos) {
        return revisions.computeIfAbsent(pos.toLong(), ignored -> new AtomicLong()).get();
    }

    public long invalidateBlock(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        return revisions.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long invalidateChunk(ChunkPos pos) {
        return revisions.computeIfAbsent(pos.toLong(), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void clear() {
        revisions.clear();
    }
}
