package com.val0x2f.chunklod.mixin;

import com.val0x2f.chunklod.core.ChunkLodMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Narrow client-only cache invalidation; does not target Sodium, C2ME, or Lithium internals. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockStateMixin {
    @Inject(method = "setBlock", at = @At("RETURN"))
    private void val0x2f$invalidateCachedChunk(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ChunkLodMod mod = ChunkLodMod.get();
            if (mod != null) mod.cacheManager().onBlockChanged(pos);
        }
    }
}
