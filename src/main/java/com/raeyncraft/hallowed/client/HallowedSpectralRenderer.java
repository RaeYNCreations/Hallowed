package com.raeyncraft.hallowed.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.raeyncraft.hallowed.Hallowed;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HallowedSpectralRenderer {

    // Alpha is configurable via /hallowed config alpha_level; fetched dynamically from HallowedClientState
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    private HallowedSpectralRenderer() {}

    private static boolean isPlayerHallowed(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
            return HallowedClientState.isHallowed();
        }
        try {
            HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
            return data.isHallowed();
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Third-person player model rendering
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (RENDERING.get()) return;
        if (!HallowedClientState.isSpectralRendering()) return;
        if (!isPlayerHallowed(event.getEntity())) return;

        event.setCanceled(true);

        MultiBufferSource original = event.getMultiBufferSource();
        int alpha = (int) (HallowedClientState.getSpectralAlpha() * 255);
        MultiBufferSource wrapped = renderType -> new AlphaOverrideVertexConsumer(
                original.getBuffer(renderType), alpha);

        RENDERING.set(true);
        try {
            event.getRenderer().render(
                    (AbstractClientPlayer) event.getEntity(),
                    event.getEntity().getYRot(),
                    event.getPartialTick(),
                    event.getPoseStack(),
                    wrapped,
                    event.getPackedLight()
            );
        } finally {
            RENDERING.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // First-person arm rendering (main hand AND off hand)
    // RenderArmEvent fires separately for each arm — we intercept both so the
    // offhand arm is translucent in first-person view.
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (!HallowedClientState.isSpectralRendering()) return;
        if (!HallowedClientState.isHallowed()) return;

        // Cancel the default render and re-render with alpha override
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher()
                .getRenderer(mc.player);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        int alpha = (int) (HallowedClientState.getSpectralAlpha() * 255);
        MultiBufferSource wrapped = renderType -> new AlphaOverrideVertexConsumer(
                bufferSource.getBuffer(renderType), alpha);

        // Render the arm with our wrapped translucent buffer
        renderer.renderArm(
                event.getPoseStack(),
                wrapped,
                event.getPackedLight(),
                mc.player,
                event.getArm()
        );

        bufferSource.endBatch();
    }

    // -------------------------------------------------------------------------
    // Shared vertex consumer that overrides alpha and applies blue tint
    // -------------------------------------------------------------------------

    private static final class AlphaOverrideVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final int alpha;

        AlphaOverrideVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor((int)(r * 0.7f), (int)(g * 0.7f), b, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            delegate.setNormal(nx, ny, nz);
            return this;
        }
    }
}