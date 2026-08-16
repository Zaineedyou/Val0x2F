package com.val0x2f.chunklod.culling;

import com.val0x2f.chunklod.memory.FrameMetrics;
import net.minecraft.client.Minecraft;

/** Admission policy evaluated before particle creation; Android default is total OFF. */
public final class ParticleCapManager {
    public enum Mode { OFF, CAP_NEAREST, VANILLA }

    private final int cap;
    private final FrameMetrics metrics;
    private volatile Mode mode = Mode.OFF;
    private int admittedThisTick;

    public ParticleCapManager(int cap, FrameMetrics metrics) {
        this.cap = Math.max(0, cap);
        this.metrics = metrics;
    }

    public void beginTick(Minecraft ignoredClient) {
        admittedThisTick = 0;
    }

    public boolean shouldAdmit(double distanceSquared, boolean important) {
        if (mode == Mode.OFF) {
            metrics.incrementDroppedParticles();
            return false;
        }
        if (mode == Mode.VANILLA) return true;
        if (important && distanceSquared <= 64.0D) return true;
        if (admittedThisTick++ < cap) return true;
        metrics.incrementDroppedParticles();
        return false;
    }

    public boolean isOff() { return mode == Mode.OFF; }
    public void mode(Mode next) { mode = next; }
}
