package com.val0x2f.chunklod.cache;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/** Stable disk/index key; paths are built from hashes rather than world names. */
public record CacheKey(long worldFingerprint, String dimensionId, int chunkX, int chunkZ, int dataVersion) {
    public static CacheKey of(long worldFingerprint, ResourceKey<Level> dimension, ChunkPos pos, int dataVersion) {
        return new CacheKey(worldFingerprint, dimension.toString(), pos.x, pos.z, dataVersion);
    }

    public long packedChunkPos() {
        return ChunkPos.asLong(chunkX, chunkZ);
    }
}
