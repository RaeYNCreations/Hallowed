package com.raeynd.hallowed.client;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Client-only renderer responsible for:
 * <ul>
 *   <li>G1 — Blue vignette overlay when the local player is Hallowed.</li>
 *   <li>G3 — "HALLOWED" HUD status indicator.</li>
 *   <li>G5 — White-flash transition effect when entering the Hallowed state.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HallowedOverlayRenderer {

    private HallowedOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!HallowedClientState.isHallowed()) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // G5: White flash on death → Hallowed transition (fades out over 1.5 s)
        float transitionAlpha = HallowedClientState.getTransitionAlpha();
        if (transitionAlpha > 0.0f) {
            int flashAlpha = (int) (transitionAlpha * 255);
            guiGraphics.fill(0, 0, screenWidth, screenHeight,
                    (flashAlpha << 24) | 0xFFFFFF);
        }

        // G1: Blue vignette overlay
        if (HallowedClientState.isBlueOverlayEnabled()) {
            float intensity = Math.clamp(HallowedClientState.getOverlayIntensity(), 0.0f, 1.0f);
            if (intensity > 0.0f) {
                int overlayAlpha = (int) (intensity * 100); // max ~39% opacity (100/255)
                // ARGB: alpha | R | G | B — blue tint
                int color = (overlayAlpha << 24) | 0x001A66;
                guiGraphics.fill(0, 0, screenWidth, screenHeight, color);
            }
        }

        // G3: "HALLOWED" status text (top-left)
        guiGraphics.drawString(
                mc.font,
                net.minecraft.network.chat.Component.translatable("hallowed.status.hallowed"),
                4, 4,
                0x88AAFF, // bright blue
                true      // shadow
        );
    }
}
