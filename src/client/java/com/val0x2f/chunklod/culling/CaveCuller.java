package com.val0x2f.chunklod.culling;

import com.val0x2f.chunklod.cache.ChunkCacheEntry;

/** Conservative cave test for Val0x2F-owned LOD shells only. */
public final class CaveCuller {
    public boolean isLodShellPotentiallyVisible(ChunkCacheEntry entry, int column) {
        int word = column >>> 6;
        int bit = column & 63;
        return (entry.solidMask()[word] & (1L << bit)) == 0L || isBoundaryColumn(column);
    }

    public boolean isFullSectionCandidateOccluded() {
        // Deliberately false by default. Sodium full-detail integration is version-pinned experimental work.
        return false;
    }

    private static boolean isBoundaryColumn(int index) {
        int x = index & 15;
        int z = index >>> 4;
        return x == 0 || x == 15 || z == 0 || z == 15;
    }
}
