package com.raeynd.hallowed.event;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import com.raeynd.hallowed.bonfire.BonfireHelper;
import com.raeynd.hallowed.compat.YouDiedCompat;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import com.raeynd.hallowed.data.HallowedSavedData;
import com.raeynd.hallowed.network.HallowedNetworking;
import com.raeynd.hallowed.util.HallowedAudit;
import com.raeynd.hallowed.util.RespawnMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts player deaths and transitions them into the Hallowed state.
 * Optionally delays the state transition to allow the You Died overlay to show.
 */
public final class DeathHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Pending You Died delay entries: UUID → earliest time (ms) at which the
     * Hallowed transition should be applied.
     */
    private static final Map<UUID, PendingTransition> PENDING_TRANSITIONS = new ConcurrentHashMap<>();

    /** Default You Died overlay display duration in milliseconds (3 seconds). */
    private static final long YOU_DIED_DELAY_MS = 3_000L;

    // -------------------------------------------------------------------------
    // Inner type for deferred transitions
    // -------------------------------------------------------------------------

    private record PendingTransition(
            ServerPlayer player,
            BlockPos deathPos,
            ResourceKey<Level> dimension,
            int xpLevel,
            long applyAfterMs) {}

    // -------------------------------------------------------------------------
    // Death event
    // -------------------------------------------------------------------------

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

        // 3H: In hardcore, ensure we don't land in spectator mode
        if (player.level().getLevelData().isHardcore()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
            }
            LOGGER.info("[Hallowed] Hardcore death intercepted for {} — preventing spectator mode.",
                    player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("hallowed.hardcore.intercepted"));
        }

        BlockPos deathPos = player.blockPosition();
        ResourceKey<Level> dimension = player.level().dimension();
        int xpLevel = player.experienceLevel;

        if (YouDiedCompat.isAvailable()) {
            // 3B: Delay Hallowed transition so the You Died overlay can display
            YouDiedCompat.triggerYouDiedOverlay(player, true);
            PENDING_TRANSITIONS.put(player.getUUID(),
                    new PendingTransition(player, deathPos, dimension, xpLevel,
                            System.currentTimeMillis() + YOU_DIED_DELAY_MS));
            LOGGER.debug("[Hallowed] Queued You Died delay for {}.", player.getGameProfile().getName());
        } else {
            applyHallowedState(player, deathPos, dimension, xpLevel);
        }
    }

    // -------------------------------------------------------------------------
    // Server tick — process deferred You Died transitions
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_TRANSITIONS.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingTransition>> iter = PENDING_TRANSITIONS.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, PendingTransition> entry = iter.next();
            PendingTransition pt = entry.getValue();
            if (now >= pt.applyAfterMs()) {
                iter.remove();
                YouDiedCompat.triggerYouDiedOverlay(pt.player(), false);
                applyHallowedState(pt.player(), pt.deathPos(), pt.dimension(), pt.xpLevel());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core state transition
    // -------------------------------------------------------------------------

    private void applyHallowedState(ServerPlayer player, BlockPos deathPos,
                                    ResourceKey<Level> dimension, int xpLevel) {
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

        HallowedAudit.logStateTransition(player.getGameProfile().getName(), player.getUUID(),
                "ALIVE", "HALLOWED", "death");
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
                    // 3F: Check if the dimension still exists
                    ServerLevel targetLevel = level.getServer().getLevel(bonfireDim);
                    if (targetLevel == null) {
                        LOGGER.warn("[Hallowed] Dimension {} no longer exists — falling back to spawn for {}.",
                                bonfireDim.location(), player.getGameProfile().getName());
                        player.sendSystemMessage(Component.translatable("hallowed.dimension.removed"));
                        teleportToBedOrWorldSpawn(player, level);
                        return;
                    }
                    // 3C: Validate the bonfire still exists and is lit
                    if (!BonfireHelper.isValidLitBonfire(targetLevel, bonfirePos)) {
                        LOGGER.warn("[Hallowed] {}'s last bonfire at {} was destroyed — falling back to spawn.",
                                player.getGameProfile().getName(), bonfirePos);
                        player.sendSystemMessage(Component.translatable("hallowed.bonfire.destroyed"));
                        teleportToBedOrWorldSpawn(player, level);
                        return;
                    }
                    Vec3 pos = Vec3.atCenterOf(bonfirePos);
                    player.teleportTo(targetLevel, pos.x, pos.y, pos.z,
                            player.getYRot(), player.getXRot());
                    LOGGER.info("[Hallowed] Teleported {} to last bonfire at {} in {}.",
                            player.getGameProfile().getName(), bonfirePos, bonfireDim.location());
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
