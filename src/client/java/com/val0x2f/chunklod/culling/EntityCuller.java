package com.val0x2f.chunklod.culling;

import com.val0x2f.chunklod.core.ModCompat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Conservative compatibility backend. Path/ray tracing is intentionally not
 * enabled until its worker snapshot and renderer hook are version-tested.
 */
public final class EntityCuller {
    private final ModCompat compat;
    private final Set<Integer> occludedEntityIds = ConcurrentHashMap.newKeySet();

    public EntityCuller(ModCompat compat) {
        this.compat = compat;
    }

    public boolean shouldSkip(Entity entity) {
        return !compat.isEntityCullingLoaded() && occludedEntityIds.contains(entity.getId());
    }

    public void markVisible(Entity entity) {
        occludedEntityIds.remove(entity.getId());
    }

    public void markOccluded(Entity entity) {
        if (!compat.isEntityCullingLoaded()) occludedEntityIds.add(entity.getId());
    }

    public void clearWorld() {
        occludedEntityIds.clear();
    }
}
