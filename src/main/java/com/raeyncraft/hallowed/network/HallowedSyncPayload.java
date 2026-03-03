package com.raeyncraft.hallowed.network;

import com.raeyncraft.hallowed.Hallowed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload that syncs a player's Hallowed state and visual config from server → client.
 *
 * @param isHallowed         Whether the local player is currently Hallowed.
 * @param blueOverlayEnabled Whether the blue vignette overlay is enabled.
 * @param overlayIntensity   Intensity of the blue overlay (0.0–1.0).
 * @param spectralRendering  Whether spectral/translucent rendering is enabled.
 */
public record HallowedSyncPayload(
        boolean isHallowed,
        boolean blueOverlayEnabled,
        float overlayIntensity,
        boolean spectralRendering
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HallowedSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "sync_state"));

    public static final StreamCodec<FriendlyByteBuf, HallowedSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.isHallowed());
                        buf.writeBoolean(payload.blueOverlayEnabled());
                        buf.writeFloat(payload.overlayIntensity());
                        buf.writeBoolean(payload.spectralRendering());
                    },
                    buf -> new HallowedSyncPayload(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readFloat(),
                            buf.readBoolean()
                    )
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
