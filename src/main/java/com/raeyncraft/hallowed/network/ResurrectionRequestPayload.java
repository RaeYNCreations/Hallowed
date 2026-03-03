package com.raeyncraft.hallowed.network;

import com.raeyncraft.hallowed.Hallowed;
import com.raeyncraft.hallowed.resurrection.ResurrectionEngine;
import com.raeyncraft.hallowed.resurrection.ResurrectionResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client → Server payload that requests resurrection of the specified player.
 * The server re-validates all conditions before acting.
 */
public record ResurrectionRequestPayload(UUID targetUUID) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResurrectionRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "resurrection_request"));

    public static final StreamCodec<FriendlyByteBuf, ResurrectionRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUUID(payload.targetUUID()),
                    buf -> new ResurrectionRequestPayload(buf.readUUID())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side handler: validate and execute other-player resurrection.
     */
    public static void handleServer(ResurrectionRequestPayload payload,
                                    net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer resurrector)) return;
            ResurrectionResult result = ResurrectionEngine.attemptOtherResurrection(resurrector, payload.targetUUID());
            Hallowed.LOGGER.debug("[Hallowed] ResurrectionRequest from {} for {}: {}",
                    resurrector.getGameProfile().getName(), payload.targetUUID(), result);
        });
    }
}
