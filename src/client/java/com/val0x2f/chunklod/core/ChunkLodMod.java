package com.val0x2f.chunklod.core;

import com.val0x2f.chunklod.cache.ChunkCacheManager;
import com.val0x2f.chunklod.culling.ParticleCapManager;
import com.val0x2f.chunklod.memory.FrameMetrics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only entry point. It never changes vanilla/Sodium video options. */
public final class ChunkLodMod implements ClientModInitializer {
    public static final String MOD_ID = "val0x2f";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile Val0x2FConfig config = Val0x2FConfig.mobileLodDefaults();
    private static ChunkLodMod instance;

    private ModCompat compat;
    private TaskScheduler scheduler;
    private ChunkCacheManager cacheManager;
    private ParticleCapManager particleCapManager;
    private FrameMetrics frameMetrics;

    public static ChunkLodMod get() { return instance; }
    public static Val0x2FConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        instance = this;
        compat = new ModCompat();
        compat.refresh();
        scheduler = new TaskScheduler();
        frameMetrics = new FrameMetrics(720);
        cacheManager = new ChunkCacheManager(scheduler);
        particleCapManager = new ParticleCapManager(config.particleCap(), frameMetrics);

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> cacheManager.captureLoadedChunk(world, chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> cacheManager.captureUnloadingChunk(world, chunk));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            frameMetrics.beginTick();
            cacheManager.tick(client);
            particleCapManager.beginTick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            cacheManager.close();
            scheduler.closeGracefully(2_000L);
        });
        LOGGER.info("Val0x2F cache initialized; LOD/bypass disabled; Sodium={}, BRD={}, EntityCulling={}, Iris={}, particles=OFF",
                compat.isSodiumLoaded(), compat.isBetterRenderDistanceLoaded(),
                compat.isEntityCullingLoaded(), compat.isIrisLoaded());
    }

    public ModCompat compat() { return compat; }
    public ChunkCacheManager cacheManager() { return cacheManager; }
    public ParticleCapManager particleCapManager() { return particleCapManager; }
    public FrameMetrics frameMetrics() { return frameMetrics; }
}
