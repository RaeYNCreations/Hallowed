package com.raeyncraft.hallowed.event;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.bonfire.BonfireHelper;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedRecord;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.network.HallowedNetworking;
import com.raeyncraft.hallowed.util.HallowedAudit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Handles player login, logout, and dimension-change events to update Hallowed
 * state and enforce restrictions on reconnect.
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

        // 3A: Always sync state to all players on login so the client HUD is correct
        HallowedNetworking.syncToPlayer(player);

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());

        // 3F: Warn if the player's death dimension no longer exists (non-fatal)
        if (data.isHallowed() && data.getDeathDimension() != null) {
            if (level.getServer().getLevel(data.getDeathDimension()) == null) {
                LOGGER.warn("[Hallowed] {} logged in with a death dimension ({}) that no longer exists.",
                        player.getGameProfile().getName(), data.getDeathDimension().location());
            }
        }

        // 3C: Clean up any stale bonfire reference on login
        BonfireHelper.validatePlayerBonfire(player);

        if (data.isHallowed()) {
            LOGGER.info("[Hallowed] {} logged in while Hallowed — stripping armor and enforcing restrictions.",
                    player.getGameProfile().getName());

            // 3A: Strip armor so the ghost player is not wearing equipment
            stripArmor(player);

            // 3A: Clear active elytra flight
            if (player.isFallFlying()) {
                player.stopFallFlying();
            }

            // 3A: Stop riding any entity
            if (player.isPassenger()) {
                player.stopRiding();
            }

            // Check resurrectOnLogin on both the attachment and the HallowedRecord.
            HallowedRecord record = savedData.getHallowedRecord(player.getUUID());
            boolean shouldResurrect = data.isResurrectOnLogin()
                    || (record != null && record.isResurrectOnLogin());

            if (shouldResurrect) {
                LOGGER.info("[Hallowed] resurrectOnLogin flag set for {} — resurrecting on login.",
                        player.getGameProfile().getName());
                resurrectPlayer(player, savedData);
                // 3A: Notify the player they were resurrected while away
                player.sendSystemMessage(Component.translatable("hallowed.resurrection.success.on_login"));
                return;
            }

            // G8: Re-apply flight/noclip if the player is still Hallowed and config allows
            if (HallowedConfig.SERVER.isAllowFlight()) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            if (HallowedConfig.SERVER.isAllowNoclip()) {
                player.noPhysics = true;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);
        // setOnlineStatus is synchronized internally so this is safe even if a
        // resurrection is in progress for this player concurrently.
        savedData.setOnlineStatus(player.getUUID(), false);

        LOGGER.debug("[Hallowed] {} logged out — online status updated.", player.getGameProfile().getName());
    }

    /**
     * 3A: Handles players changing dimensions while Hallowed.
     * Re-syncs state and re-enforces restrictions (e.g. strips armor again in
     * case the dimension transfer reset player state).
     */
    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        if (!data.isHallowed()) return;

        LOGGER.info("[Hallowed] {} changed dimension while Hallowed — re-syncing state.",
                player.getGameProfile().getName());

        // Re-sync client state
        HallowedNetworking.syncToPlayer(player);

        // Re-enforce restrictions: strip armor in case the transfer reset it
        stripArmor(player);

        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Strips armor from a Hallowed player: moves items to inventory or drops them.
     */
    private static void stripArmor(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            ItemStack armor = player.getInventory().armor.get(slot);
            if (armor.isEmpty()) continue;
            // Try to add to inventory; drop if full
            if (!player.getInventory().add(armor)) {
                player.drop(armor, false);
            }
            player.getInventory().armor.set(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Resurrects a player: clears attachment data, removes world record, restores health.
     */
    public static void resurrectPlayer(ServerPlayer player, HallowedSavedData savedData) {
        HallowedAudit.logStateTransition(player.getGameProfile().getName(), player.getUUID(),
                "HALLOWED", "ALIVE", "resurrection");
        player.setData(HallowedAttachments.HALLOWED_DATA.get(), HallowedPlayerData.DEFAULT);
        savedData.markResurrected(player.getUUID());
        player.setHealth(player.getMaxHealth());

        // G8: Revoke flight/noclip granted during Hallowed state
        if (HallowedConfig.SERVER.isAllowFlight()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (HallowedConfig.SERVER.isAllowNoclip()) {
            player.noPhysics = false;
        }

        HallowedNetworking.syncToPlayer(player);
        LOGGER.info("[Hallowed] {} has been resurrected.", player.getGameProfile().getName());
    }
}
