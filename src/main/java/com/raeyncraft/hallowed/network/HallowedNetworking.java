package com.raeyncraft.hallowed.network;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.client.HallowedClientHandlers;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class HallowedNetworking {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(HallowedNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playBidirectional(
                HallowedSyncPayload.TYPE,
                HallowedSyncPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, ctx) -> {
                            LOGGER.debug("[Hallowed] Received state sync from server: isHallowed={}", payload.isHallowed());
                            if (FMLEnvironment.dist == Dist.CLIENT) {
                                HallowedClientHandlers.handleSync(payload);
                            }
                        },
                        (payload, ctx) -> {}
                )
        );

        registrar.playToClient(
                ResurrectionListPayload.TYPE,
                ResurrectionListPayload.STREAM_CODEC,
                (payload, ctx) ->
                        LOGGER.debug("[Hallowed] Received resurrection list ({} entries).", payload.entries().size())
        );

        registrar.playToServer(
                ResurrectionRequestPayload.TYPE,
                ResurrectionRequestPayload.STREAM_CODEC,
                ResurrectionRequestPayload::handleServer
        );

        registrar.playToClient(
                YouDiedOverlayPayload.TYPE,
                YouDiedOverlayPayload.STREAM_CODEC,
                (payload, ctx) ->
                        LOGGER.debug("[Hallowed] Received YouDied overlay packet: show={}", payload.show())
        );

        registrar.playToClient(
                ResurrectionEffectPayload.TYPE,
                ResurrectionEffectPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        HallowedClientHandlers.handleResurrectionEffect(payload);
                    }
                }
        );
    }

    public static void syncToPlayer(ServerPlayer player) {
        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        PacketDistributor.sendToPlayer(player, new HallowedSyncPayload(
                data.isHallowed(),
                HallowedConfig.SERVER.isBlueOverlayEnabled(),
                (float) HallowedConfig.SERVER.getOverlayIntensity(),
                HallowedConfig.SERVER.isSpectralRendering(),
                (float) HallowedConfig.SERVER.getSpectralAlpha()
        ));
    }

    public static void sendResurrectionEffect(ServerPlayer resurrected) {
        ResurrectionEffectPayload payload = new ResurrectionEffectPayload(
                resurrected.getX(), resurrected.getY(), resurrected.getZ());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(resurrected, payload);
    }
}