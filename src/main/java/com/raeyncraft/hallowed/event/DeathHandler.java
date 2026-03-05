package com.raeyncraft.hallowed.event;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.bonfire.BonfireHelper;
import com.raeyncraft.hallowed.compat.YouDiedCompat;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.network.HallowedNetworking;
import com.raeyncraft.hallowed.util.HallowedAudit;
import com.raeyncraft.hallowed.util.HallowedSounds;
import com.raeyncraft.hallowed.util.RespawnMode;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class DeathHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, PendingTransition> PENDING_TRANSITIONS = new ConcurrentHashMap<>();
    private static final long YOU_DIED_DELAY_MS = 3_000L;

    private record PendingTransition(
            ServerPlayer player,
            BlockPos deathPos,
            ResourceKey<Level> dimension,
            int xpLevel,
            long applyAfterMs) {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        HallowedPlayerData currentData = player.getData(HallowedAttachments.HALLOWED_DATA.get());

        if (currentData.isHallowed()) {
            LOGGER.debug("[Hallowed] {} died while already Hallowed — skipping intercept.",
                    player.getGameProfile().getName());
            return;
        }

        if (player.level().getLevelData().isHardcore() && !HallowedConfig.SERVER.isOverrideHardcore()) {
            LOGGER.debug("[Hallowed] Hardcore world and override_hardcore=false — skipping intercept for {}.",
                    player.getGameProfile().getName());
            return;
        }

        event.setCanceled(true);

        // Restore health and reset client state immediately so the death screen
        // packet is overridden before the client processes it
        player.setHealth(player.getMaxHealth());
        player.resetSentInfo();

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
            YouDiedCompat.triggerYouDiedOverlay(player, true);
            PENDING_TRANSITIONS.put(player.getUUID(),
                    new PendingTransition(player, deathPos, dimension, xpLevel,
                            System.currentTimeMillis() + YOU_DIED_DELAY_MS));
            LOGGER.debug("[Hallowed] Queued You Died delay for {}.", player.getGameProfile().getName());
        } else {
            applyHallowedState(player, deathPos, dimension, xpLevel);
        }
    }

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

    private void applyHallowedState(ServerPlayer player, BlockPos deathPos,
                                    ResourceKey<Level> dimension, int xpLevel) {
        HallowedPlayerData newData = HallowedPlayerData.DEFAULT
                .withHallowed(true)
                .withDeathLocation(deathPos, dimension)
                .withTimeOfDeath(System.currentTimeMillis())
                .withXpAtDeath(xpLevel);

        player.setData(HallowedAttachments.HALLOWED_DATA.get(), newData);

        ServerLevel serverLevel = (ServerLevel) player.level();
        HallowedSavedData savedData = HallowedSavedData.get(serverLevel);
        savedData.markHallowed(player, deathPos, dimension);
        // Strip armor immediately on entering hallowed state
        PlayerConnectionHandler.stripArmor(player);

        repositionPlayer(player, newData, serverLevel);

        // Re-apply flight AFTER teleport — teleportTo resets player abilities
        if (HallowedConfig.SERVER.isAllowFlight()) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
        } else {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                HallowedSounds.BECOME_HALLOWED.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        // Sync AFTER all state is set — sends isHallowed=true, and visual config to client
        HallowedNetworking.syncToPlayer(player);

        HallowedAudit.logStateTransition(player.getGameProfile().getName(), player.getUUID(),
                "ALIVE", "HALLOWED", "death");
        LOGGER.info("[Hallowed] {} entered the Hallowed state at {} in {}.",
                player.getGameProfile().getName(), deathPos, dimension.location());
    }

    private void repositionPlayer(ServerPlayer player, HallowedPlayerData data, ServerLevel level) {
        RespawnMode mode = HallowedConfig.SERVER.getRespawnMode();
        switch (mode) {
            case IN_PLACE -> {
                LOGGER.debug("[Hallowed] RespawnMode=IN_PLACE — {} stays in place.", player.getGameProfile().getName());
            }
            case LAST_BONFIRE -> {
                BlockPos bonfirePos = data.getLastBonfirePos();
                ResourceKey<Level> bonfireDim = data.getLastBonfireDimension();
                if (bonfirePos != null && bonfireDim != null) {
                    ServerLevel targetLevel = level.getServer().getLevel(bonfireDim);
                    if (targetLevel == null) {
                        LOGGER.warn("[Hallowed] Dimension {} no longer exists — falling back to spawn for {}.",
                                bonfireDim.location(), player.getGameProfile().getName());
                        player.sendSystemMessage(Component.translatable("hallowed.dimension.removed"));
                        teleportToBedOrWorldSpawn(player, level);
                        return;
                    }
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
                    LOGGER.debug("[Hallowed] No last bonfire for {} — falling back to bed/worldspawn.",
                            player.getGameProfile().getName());
                    teleportToBedOrWorldSpawn(player, level);
                }
            }
            case BED_OR_WORLDSPAWN -> teleportToBedOrWorldSpawn(player, level);
        }
    }

    private void teleportToBedOrWorldSpawn(ServerPlayer player, ServerLevel level) {
        BlockPos respawnPos = player.getRespawnPosition();
        ServerLevel respawnLevel = level.getServer().getLevel(
                player.getRespawnDimension() != null ? player.getRespawnDimension() : Level.OVERWORLD);

        if (respawnPos != null && respawnLevel != null) {
            Vec3 pos = Vec3.atCenterOf(respawnPos);
            player.teleportTo(respawnLevel, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            LOGGER.debug("[Hallowed] Teleported {} to bed/respawn-anchor at {}.", player.getGameProfile().getName(), respawnPos);
        } else {
            BlockPos worldSpawn = level.getServer().overworld().getSharedSpawnPos();
            player.teleportTo(level.getServer().overworld(),
                    worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            LOGGER.debug("[Hallowed] Teleported {} to world spawn at {}.", player.getGameProfile().getName(), worldSpawn);
        }
    }
}