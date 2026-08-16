package com.val0x2f.chunklod.core;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * One-shot mobile profile. The user may raise these values manually after the
 * test; this class deliberately never runs again in the same session.
 */
public final class AggressivePerformanceProfile {
    private static final int TARGET_RENDER_DISTANCE = 4;
    private static final int TARGET_SIMULATION_DISTANCE = 4;
    private static final int TARGET_FPS_LIMIT = 60;
    private static final double TARGET_ENTITY_DISTANCE = 0.50D;
    private int joinTicks;
    private boolean profileAnnounced;

    public void applyOnceAfterJoin(Minecraft client) {
        if (client.player == null) {
            joinTicks = 0;
            return;
        }
        // Better Render Distance changes the vanilla value after the play stage.
        // Re-check every two seconds for 90 seconds, then stop touching user settings.
        if (++joinTicks > 20 * 90 || (joinTicks % 40) != 0) return;

        Options options = client.options;
        boolean changed = false;
        if (options.graphicsPreset().get() != GraphicsPreset.FAST) {
            options.applyGraphicsPreset(GraphicsPreset.FAST);
            changed = true;
        }
        changed |= setIfDifferent(options.ambientOcclusion(), false);
        changed |= setIfDifferent(options.mipmapLevels(), 0);
        changed |= lowerInteger(options.renderDistance(), TARGET_RENDER_DISTANCE);
        changed |= lowerInteger(options.simulationDistance(), TARGET_SIMULATION_DISTANCE);
        changed |= lowerDouble(options.entityDistanceScaling(), TARGET_ENTITY_DISTANCE);
        changed |= setIfDifferent(options.particles(), ParticleStatus.MINIMAL);
        changed |= setIfDifferent(options.cloudStatus(), CloudStatus.OFF);
        changed |= setIfDifferent(options.biomeBlendRadius(), 0);
        changed |= setIfDifferent(options.entityShadows(), false);
        int currentFpsLimit = options.framerateLimit().get();
        if (currentFpsLimit == 0 || currentFpsLimit > TARGET_FPS_LIMIT) {
            options.framerateLimit().set(TARGET_FPS_LIMIT);
            changed = true;
        }
        if (changed) options.save();
        if (!profileAnnounced) {
            profileAnnounced = true;
            ChunkLodMod.LOGGER.info("Applied aggressive mobile profile: fastPreset render={} simulation={} entities={} particles=minimal clouds=off ao=off mipmap=0 biomeBlend=0 fpsLimit={}",
                    options.renderDistance().get(), options.simulationDistance().get(), options.entityDistanceScaling().get(), options.framerateLimit().get());
        }
    }

    private static boolean lowerInteger(net.minecraft.client.OptionInstance<Integer> option, int target) {
        int next = Math.min(option.get(), target);
        if (next == option.get()) return false;
        option.set(next);
        return true;
    }

    private static boolean lowerDouble(net.minecraft.client.OptionInstance<Double> option, double target) {
        double next = Math.min(option.get(), target);
        if (Double.compare(next, option.get()) == 0) return false;
        option.set(next);
        return true;
    }

    private static <T> boolean setIfDifferent(net.minecraft.client.OptionInstance<T> option, T target) {
        if (option.get().equals(target)) return false;
        option.set(target);
        return true;
    }
}
