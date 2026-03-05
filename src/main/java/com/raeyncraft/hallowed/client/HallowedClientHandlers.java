package com.raeyncraft.hallowed.client;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.network.HallowedSyncPayload;
import com.raeyncraft.hallowed.network.ResurrectionEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public final class HallowedClientHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedClientHandlers() {}

    public static void handleSync(HallowedSyncPayload payload) {
        HallowedClientState.update(
                payload.isHallowed(),
                payload.blueOverlayEnabled(),
                payload.overlayIntensity(),
                payload.spectralRendering(),
                payload.spectralAlpha()
        );
        // Close the death screen if we're now in the Hallowed state
        if (payload.isHallowed()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
                mc.setScreen(null);
            }
        }
        LOGGER.debug("[Hallowed] Client state updated — isHallowed={}", payload.isHallowed());
    }

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