package com.raeynd.hallowed.network;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
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
                        // Client handler — update local cached state for HUD rendering
                        (payload, ctx) -> {
                            // Client-side: store Hallowed state locally (Phase 2 HUD will read this)
                            LOGGER.debug("[Hallowed] Received state sync from server: isHallowed={}", payload.isHallowed());
                        },
                        // Server handler — not used; state changes originate on server
                        (payload, ctx) -> {}
                )
        );

        // Server → Client: full Hallowed player list for the Resurrection GUI.
        // Registered here so the packet type is known on both sides; the client
        // receives list data via the menu extra-data buffer when the GUI opens.
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
    }

    /**
     * Sends a state-sync packet to the given player.
     * Must be called on the server thread.
     */
    public static void syncToPlayer(ServerPlayer player) {
        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA);
        PacketDistributor.sendToPlayer(player, new HallowedSyncPayload(data.isHallowed()));
    }
}
