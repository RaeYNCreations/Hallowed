package com.raeynd.hallowed.network;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Simple payload that syncs a player's Hallowed state from server → client.
 *
 * @param isHallowed Whether the local player is currently Hallowed.
 */
public record HallowedSyncPayload(boolean isHallowed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HallowedSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "sync_state"));

    public static final StreamCodec<FriendlyByteBuf, HallowedSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.isHallowed()),
                    buf -> new HallowedSyncPayload(buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
