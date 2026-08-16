package com.val0x2f.chunklod.mixin;

import com.val0x2f.chunklod.core.ChunkLodMod;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops particles before construction; add() guards mods that create custom Particle directly. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void val0x2f$skipCreation(ParticleOptions options, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ,
                                       CallbackInfoReturnable<Particle> cir) {
        ChunkLodMod mod = ChunkLodMod.get();
        if (mod != null && mod.particleCapManager().isOff()) cir.setReturnValue(null);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void val0x2f$skipExternalAdd(Particle particle, CallbackInfo ci) {
        ChunkLodMod mod = ChunkLodMod.get();
        if (mod != null && mod.particleCapManager().isOff()) ci.cancel();
    }
}
