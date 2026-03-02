package com.raeynd.hallowed.network;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload that instructs the client to show or hide the
 * You Died overlay (when the You Died mod is present).
 *
 * <p>The client handler sets a local flag used for rendering; Phase 4 will
 * wire in the actual overlay renderer.
 */
public record YouDiedOverlayPayload(boolean show) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<YouDiedOverlayPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "you_died_overlay"));

    public static final StreamCodec<FriendlyByteBuf, YouDiedOverlayPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.show()),
                    buf -> new YouDiedOverlayPayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
