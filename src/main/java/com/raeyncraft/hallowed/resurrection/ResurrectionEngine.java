package com.raeyncraft.hallowed.resurrection;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.bonfire.BonfireHelper;
import com.raeyncraft.hallowed.currency.CurrencyService;
import com.raeyncraft.hallowed.currency.ResurrectionCostCalculator;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedRecord;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.network.HallowedNetworking;
import com.raeyncraft.hallowed.util.HallowedAudit;
import com.raeyncraft.hallowed.util.HallowedSounds;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Core resurrection logic.
 *
 * <p>All public methods synchronise on the {@link HallowedSavedData} instance
 * to prevent race conditions when multiple players attempt resurrection
 * simultaneously.
 */
public final class ResurrectionEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ResurrectionEngine() {}

    // -------------------------------------------------------------------------
    // Self-resurrection (Hallowed player at a Bonfire)
    // -------------------------------------------------------------------------

    /**
     * Attempts to resurrect {@code player} by spending coins at a nearby Bonfire.
     *
     * @return the outcome of the attempt
     */
    public static ResurrectionResult attemptSelfResurrection(ServerPlayer player) {
        HallowedSavedData savedData = HallowedSavedData.get(player.serverLevel());
        UUID uuid = player.getUUID();

        // 3E: Prevent concurrent resurrection attempts
        if (!savedData.tryLock(uuid)) {
            player.sendSystemMessage(Component.translatable("hallowed.resurrection.fail.concurrent"));
            HallowedAudit.logResurrectionFailed(player.getGameProfile().getName(), uuid,
                    "CONCURRENT_ATTEMPT", "self");
            return ResurrectionResult.CONCURRENT_ATTEMPT;
        }

        try {
            synchronized (savedData) {
                if (!savedData.isHallowed(uuid)) {
                    return ResurrectionResult.NOT_HALLOWED;
                }

                if (!BonfireHelper.isPlayerAtBonfire(player)) {
                    return ResurrectionResult.NOT_AT_BONFIRE;
                }

                HallowedRecord record = savedData.getHallowedRecord(uuid);
                if (record == null) {
                    return ResurrectionResult.NOT_HALLOWED;
                }

                // Check resurrection delay
                long elapsedSeconds = (System.currentTimeMillis() - record.getTimeOfDeath()) / 1000L;
                long requiredSeconds = HallowedConfig.SERVER.getResurrectionDelaySeconds();
                if (elapsedSeconds < requiredSeconds) {
                    long remaining = requiredSeconds - elapsedSeconds;
                    player.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.fail.delay", remaining));
                    HallowedAudit.logResurrectionFailed(player.getGameProfile().getName(), uuid,
                            "DELAY_NOT_MET", "self");
                    return ResurrectionResult.DELAY_NOT_MET;
                }

                MoneyValue cost = ResurrectionCostCalculator.calculateSelfCostAsValue(record.getXpAtDeath());

                // 3D: Mark pending before withdrawing funds
                savedData.setPendingResurrection(uuid, true);

                // Attempt withdrawal (internally simulates first)
                if (!CurrencyService.withdrawFunds(player, cost)) {
                    savedData.setPendingResurrection(uuid, false);
                    player.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.fail.insufficient_funds", cost.toString()));
                    HallowedAudit.logResurrectionFailed(player.getGameProfile().getName(), uuid,
                            "INSUFFICIENT_FUNDS", "self");
                    return ResurrectionResult.INSUFFICIENT_FUNDS;
                }

                // Find the bonfire position for audit logging
                Optional<net.minecraft.core.BlockPos> nearbyBonfire = BonfireHelper.findNearbyBonfire(player);

                // Resurrection
                savedData.markResurrected(uuid);
                player.setData(HallowedAttachments.HALLOWED_DATA.get(), HallowedPlayerData.DEFAULT);
                player.setHealth(player.getMaxHealth());

                // G8: Revoke flight/noclip granted during Hallowed state
                revokeFlightAndNoclip(player);

                HallowedNetworking.syncToPlayer(player);

                // G4 + G7: Resurrection VFX and sound
                HallowedNetworking.sendResurrectionEffect(player);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        HallowedSounds.RESURRECTION_SUCCESS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

                player.sendSystemMessage(Component.translatable("hallowed.resurrection.success.self"));

                HallowedAudit.logCoinTransaction(player.getGameProfile().getName(), uuid,
                        record.getCoinsRequired(), "self", player.getGameProfile().getName(),
                        nearbyBonfire.orElse(player.blockPosition()));
                HallowedAudit.logStateTransition(player.getGameProfile().getName(), uuid,
                        "HALLOWED", "ALIVE", "self-resurrection");
                LOGGER.info("[Hallowed] {} self-resurrected at bonfire. Cost: {}",
                        player.getGameProfile().getName(), cost.toString());
            }
        } finally {
            savedData.unlock(uuid);
        }

        return ResurrectionResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Other-player resurrection (living player pays to resurrect a Hallowed player)
    // -------------------------------------------------------------------------

    /**
     * Attempts to resurrect {@code targetUUID} using coins from {@code resurrector}.
     *
     * @return the outcome of the attempt
     */
    public static ResurrectionResult attemptOtherResurrection(ServerPlayer resurrector, UUID targetUUID) {
        HallowedSavedData savedData = HallowedSavedData.get(resurrector.serverLevel());

        // 3E: Prevent concurrent resurrection attempts for the same target
        if (!savedData.tryLock(targetUUID)) {
            resurrector.sendSystemMessage(Component.translatable("hallowed.resurrection.fail.concurrent"));
            HallowedAudit.logResurrectionFailed(resurrector.getGameProfile().getName(), resurrector.getUUID(),
                    "CONCURRENT_ATTEMPT", targetUUID.toString());
            return ResurrectionResult.CONCURRENT_ATTEMPT;
        }

        try {
            synchronized (savedData) {
                if (!savedData.isHallowed(targetUUID)) {
                    return ResurrectionResult.ALREADY_ALIVE;
                }

                if (!BonfireHelper.isPlayerAtBonfire(resurrector)) {
                    return ResurrectionResult.NOT_AT_BONFIRE;
                }

                HallowedRecord record = savedData.getHallowedRecord(targetUUID);
                if (record == null) {
                    return ResurrectionResult.ALREADY_ALIVE;
                }

                MoneyValue cost = ResurrectionCostCalculator.calculateOtherCostAsValue(record.getXpAtDeath());

                // 3D: Mark pending before withdrawing funds
                savedData.setPendingResurrection(targetUUID, true);

                // Attempt withdrawal from the resurrector's funds
                if (!CurrencyService.withdrawFunds(resurrector, cost)) {
                    savedData.setPendingResurrection(targetUUID, false);
                    resurrector.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.fail.insufficient_funds", cost.toString()));
                    HallowedAudit.logResurrectionFailed(resurrector.getGameProfile().getName(), resurrector.getUUID(),
                            "INSUFFICIENT_FUNDS", record.getUsername());
                    return ResurrectionResult.INSUFFICIENT_FUNDS;
                }

                // Find the bonfire position for audit logging
                Optional<net.minecraft.core.BlockPos> nearbyBonfire = BonfireHelper.findNearbyBonfire(resurrector);

                // Look up the target player on the server
                ServerPlayer target = resurrector.getServer().getPlayerList().getPlayer(targetUUID);

                if (target != null) {
                    // Target is online — resurrect immediately
                    savedData.markResurrected(targetUUID);
                    target.setData(HallowedAttachments.HALLOWED_DATA.get(), HallowedPlayerData.DEFAULT);
                    target.setHealth(target.getMaxHealth());

                    // G8: Revoke flight/noclip granted during Hallowed state
                    revokeFlightAndNoclip(target);

                    HallowedNetworking.syncToPlayer(target);

                    // G4 + G7: Resurrection VFX and sound
                    HallowedNetworking.sendResurrectionEffect(target);
                    target.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                            HallowedSounds.RESURRECTION_SUCCESS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

                    target.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.success.target",
                            resurrector.getGameProfile().getName()));
                    resurrector.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.success.other",
                            record.getUsername()));
                    HallowedAudit.logStateTransition(record.getUsername(), targetUUID,
                            "HALLOWED", "ALIVE", "other-resurrection by " + resurrector.getGameProfile().getName());
                } else {
                    // Target is offline — set the resurrect-on-login flag
                    // (pendingResurrection cleared by markResurrected path on login)
                    HallowedRecord flagged = record.withResurrectOnLogin(true).withPendingResurrection(false);
                    savedData.updateRecord(targetUUID, flagged);

                    resurrector.sendSystemMessage(Component.translatable(
                            "hallowed.resurrection.offline", record.getUsername()));
                }

                HallowedAudit.logCoinTransaction(resurrector.getGameProfile().getName(), resurrector.getUUID(),
                        record.getCoinsRequired(), "other", record.getUsername(),
                        nearbyBonfire.orElse(resurrector.blockPosition()));
                LOGGER.info("[Hallowed] {} resurrected {} (target online={}). Cost: {}",
                        resurrector.getGameProfile().getName(), record.getUsername(),
                        target != null, cost.toString());
            }
        } finally {
            savedData.unlock(targetUUID);
        }

        return ResurrectionResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * G8: Revokes flight and noclip abilities that were granted when the player
     * entered the Hallowed state. Called on all resurrection paths.
     */
    private static void revokeFlightAndNoclip(ServerPlayer player) {
        if (HallowedConfig.SERVER.isAllowFlight()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (HallowedConfig.SERVER.isAllowNoclip()) {
            player.noPhysics = false;
        }
    }
}
