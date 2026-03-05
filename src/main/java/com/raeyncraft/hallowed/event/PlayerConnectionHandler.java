package com.raeyncraft.hallowed.event;

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
import org.slf4j.Logger;

/**
 * Handles player login, logout, respawn, and dimension-change events to keep
 * Hallowed state consistent across NeoForge's entity-replacement lifecycle.
 *
 * <p>NeoForge creates a BRAND NEW ServerPlayer object on every respawn (even
 * when the death is cancelled). Attachment data is therefore reset to DEFAULT
 * on the new entity. We restore it from HallowedSavedData — the authoritative
 * source of truth — in {@link #onPlayerRespawn}.
 */
public final class PlayerConnectionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);

        savedData.setOnlineStatus(player.getUUID(), true);

        // Restore attachment from SavedData — NeoForge resets it on new entity creation
        restoreHallowedIfNeeded(player, savedData);

        // Delay sync slightly so client player entity is fully loaded
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 5,
                () -> HallowedNetworking.syncToPlayer(player)
        ));

        BonfireHelper.validatePlayerBonfire(player);

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        if (data.isHallowed()) {
            LOGGER.info("[Hallowed] {} logged in while Hallowed — stripping armor and enforcing restrictions.",
                    player.getGameProfile().getName());

            stripArmor(player);

            if (player.isFallFlying()) player.stopFallFlying();
            if (player.isPassenger()) player.stopRiding();

            HallowedRecord record = savedData.getHallowedRecord(player.getUUID());
            boolean shouldResurrect = data.isResurrectOnLogin()
                    || (record != null && record.isResurrectOnLogin());

            if (shouldResurrect) {
                LOGGER.info("[Hallowed] resurrectOnLogin flag set for {} — resurrecting on login.",
                        player.getGameProfile().getName());
                resurrectPlayer(player, savedData);
                player.sendSystemMessage(Component.translatable("hallowed.resurrection.success.on_login"));
                return;
            }

            if (HallowedConfig.SERVER.isAllowFlight()) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Respawn — THE KEY FIX
    // NeoForge fires PlayerRespawnEvent on the NEW player object after creating
    // it. The attachment on that new object is DEFAULT (isHallowed=false) even
    // though the player is still Hallowed. We restore it here every time.
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);

        // Restore from authoritative SavedData onto the new player object
        restoreHallowedIfNeeded(player, savedData);

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        if (!data.isHallowed()) return;

        LOGGER.info("[Hallowed] Restored Hallowed state for {} after respawn.",
                player.getGameProfile().getName());

        // Re-apply flight abilities on the new player object
        if (HallowedConfig.SERVER.isAllowFlight()) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
        } else {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();

        // Sync to client immediately so restrictions take effect
        HallowedNetworking.syncToPlayer(player);
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(level);
        savedData.setOnlineStatus(player.getUUID(), false);

        LOGGER.debug("[Hallowed] {} logged out — online status updated.", player.getGameProfile().getName());
    }

    // -------------------------------------------------------------------------
    // Dimension change
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        if (!data.isHallowed()) return;

        LOGGER.info("[Hallowed] {} changed dimension while Hallowed — re-syncing state.",
                player.getGameProfile().getName());

        HallowedNetworking.syncToPlayer(player);
        stripArmor(player);

        if (player.isFallFlying()) player.stopFallFlying();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Restores the Hallowed attachment from SavedData if it has been reset to
     * DEFAULT by NeoForge's entity-replacement lifecycle (respawn, dimension
     * change, etc.).
     */
    public static void restoreHallowedIfNeeded(ServerPlayer player, HallowedSavedData savedData) {
        boolean isHallowedInSavedData = savedData.isHallowed(player.getUUID());
        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());

        if (isHallowedInSavedData && !data.isHallowed()) {
            player.setData(HallowedAttachments.HALLOWED_DATA.get(), data.withHallowed(true));
            LOGGER.info("[Hallowed] Restored hallowed attachment for {} from SavedData.",
                    player.getGameProfile().getName());
        }
    }

    /**
     * Strips armor from a Hallowed player: moves items to inventory or drops them.
     */
    public static void stripArmor(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            ItemStack armor = player.getInventory().armor.get(slot);
            if (armor.isEmpty()) continue;
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

        if (HallowedConfig.SERVER.isAllowFlight()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        HallowedNetworking.syncToPlayer(player);
        LOGGER.info("[Hallowed] {} has been resurrected.", player.getGameProfile().getName());
    }
}