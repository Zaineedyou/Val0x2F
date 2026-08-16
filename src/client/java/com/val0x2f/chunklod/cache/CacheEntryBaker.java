package com.val0x2f.chunklod.cache;

/**
 * CPU-only conversion from a reusable snapshot to a compact persistent cache entry.
 * It owns no direct buffer, OpenGL state, or LOD mesh.
 */
final class CacheEntryBaker {
    ChunkCacheEntry bake(ChunkSnapshot snapshot) {
        ChunkCacheEntry entry = new ChunkCacheEntry();
        entry.reset(snapshot.key(), snapshot.revision(), snapshot.minBuildHeight());
        System.arraycopy(snapshot.heights(), 0, entry.heights(), 0, ChunkCacheEntry.COLUMN_COUNT);
        System.arraycopy(snapshot.colors(), 0, entry.colors(), 0, ChunkCacheEntry.COLUMN_COUNT);
        System.arraycopy(snapshot.solidMask(), 0, entry.solidMask(), 0, entry.solidMask().length);
        return entry;
    }
}
