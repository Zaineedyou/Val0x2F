package com.val0x2f.chunklod.core;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Compatibility decisions are deliberately limited to mod presence and local
 * feature gates. This class never reflects into Sodium, C2ME, or other mod internals.
 */
public final class ModCompat {
    private boolean sodium;
    private boolean lithium;
    private boolean c2me;
    private boolean betterRenderDistance;
    private boolean entityCulling;
    private boolean cullLeaves;
    private boolean moreCulling;
    private boolean iris;

    public void refresh() {
        FabricLoader loader = FabricLoader.getInstance();
        sodium = loader.isModLoaded("sodium");
        lithium = loader.isModLoaded("lithium");
        c2me = loader.isModLoaded("c2me");
        betterRenderDistance = loader.isModLoaded("betterrenderdistance");
        entityCulling = loader.isModLoaded("entityculling");
        cullLeaves = loader.isModLoaded("cullleaves");
        moreCulling = loader.isModLoaded("moreculling");
        iris = loader.isModLoaded("iris");
    }

    public boolean isSodiumLoaded() { return sodium; }
    public boolean isLithiumLoaded() { return lithium; }
    public boolean isC2MELoaded() { return c2me; }
    public boolean isBetterRenderDistanceLoaded() { return betterRenderDistance; }
    public boolean isEntityCullingLoaded() { return entityCulling; }
    public boolean isCullLeavesLoaded() { return cullLeaves; }
    public boolean isMoreCullingLoaded() { return moreCulling; }
    public boolean isIrisLoaded() { return iris; }

    public boolean shouldRunOwnEntityOcclusion(Val0x2FConfig config) {
        return config.enableEntityOcclusion() && !entityCulling;
    }

    /**
     * The user's current client render distance owns full-detail terrain. BRD receives
     * one extra guard chunk because it performs its own diagonal/vertical culling.
     */
    public int lodStartDistanceChunks(int userRenderDistanceChunks) {
        return userRenderDistanceChunks + (betterRenderDistance ? 2 : 1);
    }

    public boolean isShaderSafeLodEnabled(Val0x2FConfig config) {
        return !iris || config.allowIrisLodRendering();
    }
}
