package com.raeynd.hallowed.network;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload that triggers resurrection visual/audio effects
 * at the given world position.
 *
 * @param x X coordinate of the resurrection position.
 * @param y Y coordinate of the resurrection position.
 * @param z Z coordinate of the resurrection position.
 */
public record ResurrectionEffectPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResurrectionEffectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "resurrection_effect"));

    public static final StreamCodec<FriendlyByteBuf, ResurrectionEffectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeDouble(payload.x());
                        buf.writeDouble(payload.y());
                        buf.writeDouble(payload.z());
                    },
                    buf -> new ResurrectionEffectPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
