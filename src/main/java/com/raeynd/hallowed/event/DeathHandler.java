package com.raeynd.hallowed.event;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import com.raeynd.hallowed.data.HallowedSavedData;
import com.raeynd.hallowed.network.HallowedNetworking;
import com.raeynd.hallowed.util.RespawnMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

/**
 * Intercepts player deaths and transitions them into the Hallowed state.
 */
public final class DeathHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Intercepts the death event for players.
     * Priority HIGH so we can cancel before other mods act on the death.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        HallowedPlayerData currentData = player.getData(HallowedAttachments.HALLOWED_DATA);

        // Rule 1: already Hallowed — let vanilla death proceed
        if (currentData.isHallowed()) {
            LOGGER.debug("[Hallowed] {} died while already Hallowed — skipping intercept.",
                    player.getGameProfile().getName());
            return;
        }

        // Rule 2: hardcore with override disabled — let vanilla handle it
        if (player.level().getLevelData().isHardcore() && !HallowedConfig.SERVER.isOverrideHardcore()) {
            LOGGER.debug("[Hallowed] Hardcore world and override_hardcore=false — skipping intercept for {}.",
                    player.getGameProfile().getName());
            return;
        }

        // Cancel vanilla death
        event.setCanceled(true);

        BlockPos deathPos = player.blockPosition();
        ResourceKey<Level> dimension = player.level().dimension();
        int xpLevel = player.experienceLevel;

        // Build and store attachment data
        HallowedPlayerData newData = HallowedPlayerData.DEFAULT
                .withHallowed(true)
                .withDeathLocation(deathPos, dimension)
                .withTimeOfDeath(System.currentTimeMillis())
                .withXpAtDeath(xpLevel);

        player.setData(HallowedAttachments.HALLOWED_DATA, newData);

        // Record in world-scoped storage
        ServerLevel serverLevel = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(serverLevel);
        savedData.markHallowed(player, deathPos, dimension);

        // Restore health so the player is a ghost, not dead
        player.setHealth(player.getMaxHealth());

        // Reposition the player based on config
        repositionPlayer(player, newData, serverLevel);

        // Sync new state to client
        HallowedNetworking.syncToPlayer(player);

        LOGGER.info("[Hallowed] {} entered the Hallowed state at {} in {}.",
                player.getGameProfile().getName(), deathPos, dimension.location());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void repositionPlayer(ServerPlayer player, HallowedPlayerData data, ServerLevel level) {
        RespawnMode mode = HallowedConfig.SERVER.getRespawnMode();
        switch (mode) {
            case IN_PLACE -> {
                // No teleport needed — player stays at death position
                LOGGER.debug("[Hallowed] RespawnMode=IN_PLACE — {} stays in place.", player.getGameProfile().getName());
            }
            case LAST_BONFIRE -> {
                BlockPos bonfirePos = data.getLastBonfirePos();
                ResourceKey<Level> bonfireDim = data.getLastBonfireDimension();
                if (bonfirePos != null && bonfireDim != null) {
                    ServerLevel targetLevel = level.getServer().getLevel(bonfireDim);
                    if (targetLevel != null) {
                        Vec3 pos = Vec3.atCenterOf(bonfirePos);
                        player.teleportTo(targetLevel, pos.x, pos.y, pos.z,
                                player.getYRot(), player.getXRot());
                        LOGGER.info("[Hallowed] Teleported {} to last bonfire at {} in {}.",
                                player.getGameProfile().getName(), bonfirePos, bonfireDim.location());
                    } else {
                        LOGGER.warn("[Hallowed] Last bonfire dimension {} not found for {}, falling back.",
                                bonfireDim.location(), player.getGameProfile().getName());
                        teleportToBedOrWorldSpawn(player, level);
                    }
                } else {
                    // Player never used a Bonfire — fall back to bed/worldspawn
                    LOGGER.debug("[Hallowed] No last bonfire for {} — falling back to bed/worldspawn.",
                            player.getGameProfile().getName());
                    teleportToBedOrWorldSpawn(player, level);
                }
            }
            case BED_OR_WORLDSPAWN -> teleportToBedOrWorldSpawn(player, level);
        }
    }

    private void teleportToBedOrWorldSpawn(ServerPlayer player, ServerLevel level) {
        // Try bed / respawn anchor position first
        BlockPos respawnPos = player.getRespawnPosition();
        ServerLevel respawnLevel = level.getServer().getLevel(
                player.getRespawnDimension() != null ? player.getRespawnDimension() : Level.OVERWORLD);

        if (respawnPos != null && respawnLevel != null) {
            Vec3 pos = Vec3.atCenterOf(respawnPos);
            player.teleportTo(respawnLevel, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            LOGGER.debug("[Hallowed] Teleported {} to bed/respawn-anchor at {}.", player.getGameProfile().getName(), respawnPos);
        } else {
            // World spawn fallback
            BlockPos worldSpawn = level.getServer().overworld().getSharedSpawnPos();
            player.teleportTo(level.getServer().overworld(),
                    worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            LOGGER.debug("[Hallowed] Teleported {} to world spawn at {}.", player.getGameProfile().getName(), worldSpawn);
        }
    }
}
