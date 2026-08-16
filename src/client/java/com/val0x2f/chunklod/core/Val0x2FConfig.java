package com.val0x2f.chunklod.core;

/** Immutable Val0x2F-only configuration. Vanilla and Sodium video options are never written here. */
public record Val0x2FConfig(
        int lodDistanceChunks,
        int lodFadeWidthChunks,
        int particleCap,
        int completedResultsPerTick,
        long directMemoryBudgetBytes,
        boolean enableEntityOcclusion,
        boolean experimentalFullDetailCaveCulling,
        boolean allowIrisLodRendering,
        boolean particlesOff
) {
    public static Val0x2FConfig mobileLodDefaults() {
        return new Val0x2FConfig(
                32,
                6,
                0,
                2,
                24L * 1024L * 1024L,
                true,
                false,
                false,
                true
        );
    }
}
