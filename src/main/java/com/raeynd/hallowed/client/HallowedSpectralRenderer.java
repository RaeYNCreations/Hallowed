package com.raeynd.hallowed.client;

import com.raeynd.hallowed.Hallowed;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * G2 — Applies a translucent/spectral rendering effect to Hallowed players.
 * Uses {@link RenderPlayerEvent.Pre} and {@link RenderPlayerEvent.Post} to
 * bracket the player render call with GL blending state changes.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HallowedSpectralRenderer {

    /** Alpha value applied to Hallowed player models (0.0–1.0). */
    private static final float SPECTRAL_ALPHA = 0.45f;

    private HallowedSpectralRenderer() {}

    /**
     * Checks whether the given player is Hallowed.
     * For the local player, reads the cached {@link HallowedClientState}.
     * For remote players, attempts to read the attachment data which is only
     * populated if the server has synced it (best-effort).
     */
    private static boolean isPlayerHallowed(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
            return HallowedClientState.isHallowed();
        }
        // Remote player — try attachment data (may not be synced)
        try {
            HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA);
            return data.isHallowed();
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!HallowedClientState.isSpectralRendering()) return;
        if (!isPlayerHallowed(event.getEntity())) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Tint slightly blue and reduce opacity for a spectral look
        RenderSystem.setShaderColor(0.7f, 0.7f, 1.0f, SPECTRAL_ALPHA);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!HallowedClientState.isSpectralRendering()) return;
        if (!isPlayerHallowed(event.getEntity())) return;

        // Restore default render color and blend state
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
