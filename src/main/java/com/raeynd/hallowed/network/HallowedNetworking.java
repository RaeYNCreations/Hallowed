package com.raeynd.hallowed.network;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import com.raeynd.hallowed.client.HallowedClientHandlers;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Registers and dispatches Hallowed network payloads.
 * All game logic remains server-authoritative; the client only receives state
 * for HUD/rendering purposes.
 */
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
                        // Client handler — update local cached state for HUD/overlay rendering
                        (payload, ctx) -> {
                            LOGGER.debug("[Hallowed] Received state sync from server: isHallowed={}", payload.isHallowed());
                            if (FMLEnvironment.dist == Dist.CLIENT) {
                                HallowedClientHandlers.handleSync(payload);
                            }
                        },
                        // Server handler — not used; state changes originate on server
                        (payload, ctx) -> {}
                )
        );

        // Server → Client: full Hallowed player list for the Resurrection GUI.
        registrar.playToClient(
                ResurrectionListPayload.TYPE,
                ResurrectionListPayload.STREAM_CODEC,
                (payload, ctx) ->
                        LOGGER.debug("[Hallowed] Received resurrection list ({} entries).", payload.entries().size())
        );

        // Client → Server: request to resurrect a target player
        registrar.playToServer(
                ResurrectionRequestPayload.TYPE,
                ResurrectionRequestPayload.STREAM_CODEC,
                ResurrectionRequestPayload::handleServer
        );

        // Server → Client: You Died overlay trigger (optional integration)
        registrar.playToClient(
                YouDiedOverlayPayload.TYPE,
                YouDiedOverlayPayload.STREAM_CODEC,
                (payload, ctx) ->
                        LOGGER.debug("[Hallowed] Received YouDied overlay packet: show={}", payload.show())
        );

        // Server → Client: resurrection visual/audio effects
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

    // -------------------------------------------------------------------------
    // Server-side send helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a full state-sync packet to the given player.
     * Must be called on the server thread.
     */
    public static void syncToPlayer(ServerPlayer player) {
        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA);
        PacketDistributor.sendToPlayer(player, new HallowedSyncPayload(
                data.isHallowed(),
                HallowedConfig.SERVER.isBlueOverlayEnabled(),
                (float) HallowedConfig.SERVER.getOverlayIntensity(),
                HallowedConfig.SERVER.isSpectralRendering()
        ));
    }

    /**
     * Broadcasts a resurrection effect payload to all players tracking the
     * given position. Call from the server thread after a successful resurrection.
     */
    public static void sendResurrectionEffect(ServerPlayer resurrected) {
        ResurrectionEffectPayload payload = new ResurrectionEffectPayload(
                resurrected.getX(), resurrected.getY(), resurrected.getZ());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(resurrected, payload);
    }
}
