package com.raeynd.hallowed.client;

import com.raeynd.hallowed.network.HallowedSyncPayload;
import com.raeynd.hallowed.network.ResurrectionEffectPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/**
 * Client-only network handlers for Hallowed payloads.
 * Kept in a separate class so that client-only imports ({@code Minecraft},
 * particle types, etc.) are never referenced by the shared networking class.
 */
@OnlyIn(Dist.CLIENT)
public final class HallowedClientHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedClientHandlers() {}

    /**
     * Handles an incoming {@link HallowedSyncPayload} on the client thread.
     * Updates {@link HallowedClientState} with the received values.
     */
    public static void handleSync(HallowedSyncPayload payload) {
        HallowedClientState.update(
                payload.isHallowed(),
                payload.blueOverlayEnabled(),
                payload.overlayIntensity(),
                payload.spectralRendering()
        );
        LOGGER.debug("[Hallowed] Client state updated — isHallowed={}", payload.isHallowed());
    }

    /**
     * Handles an incoming {@link ResurrectionEffectPayload} on the client thread.
     * Spawns a burst of end-rod particles at the resurrection position.
     */
    public static void handleResurrectionEffect(ResurrectionEffectPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (int i = 0; i < 30; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 2.0;
            double oy = mc.level.random.nextDouble() * 2.0;
            double oz = (mc.level.random.nextDouble() - 0.5) * 2.0;
            mc.level.addParticle(ParticleTypes.END_ROD,
                    payload.x() + ox, payload.y() + oy, payload.z() + oz,
                    ox * 0.1, oy * 0.1, oz * 0.1);
        }

        LOGGER.debug("[Hallowed] Resurrection effect played at ({}, {}, {}).",
                payload.x(), payload.y(), payload.z());
    }
}
