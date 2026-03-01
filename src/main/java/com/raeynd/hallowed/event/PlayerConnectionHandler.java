package com.raeynd.hallowed.event;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import com.raeynd.hallowed.data.HallowedRecord;
import com.raeynd.hallowed.data.HallowedSavedData;
import com.raeynd.hallowed.network.HallowedNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Handles player login and logout events to update Hallowed state
 * and enforce restrictions on reconnect.
 */
public final class PlayerConnectionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);

        // Update online status
        savedData.setOnlineStatus(player.getUUID(), true);

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA);

        if (data.isHallowed()) {
            LOGGER.info("[Hallowed] {} logged in while Hallowed — re-enforcing restrictions.",
                    player.getGameProfile().getName());

            // Check resurrectOnLogin on both the attachment and the HallowedRecord.
            // The HallowedRecord flag is set when another player resurrects an offline target.
            HallowedRecord record = savedData.getHallowedRecord(player.getUUID());
            boolean shouldResurrect = data.isResurrectOnLogin()
                    || (record != null && record.isResurrectOnLogin());

            if (shouldResurrect) {
                LOGGER.info("[Hallowed] resurrectOnLogin flag set for {} — resurrecting on login.",
                        player.getGameProfile().getName());
                resurrectPlayer(player, savedData);
                return;
            }

            // Re-sync state to client so HUD displays correctly
            HallowedNetworking.syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);
        savedData.setOnlineStatus(player.getUUID(), false);

        LOGGER.debug("[Hallowed] {} logged out — online status updated.", player.getGameProfile().getName());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resurrects a player: clears attachment data, removes world record, restores health.
     */
    public static void resurrectPlayer(ServerPlayer player, HallowedSavedData savedData) {
        player.setData(HallowedAttachments.HALLOWED_DATA, HallowedPlayerData.DEFAULT);
        savedData.markResurrected(player.getUUID());
        player.setHealth(player.getMaxHealth());
        HallowedNetworking.syncToPlayer(player);
        LOGGER.info("[Hallowed] {} has been resurrected.", player.getGameProfile().getName());
    }
}
