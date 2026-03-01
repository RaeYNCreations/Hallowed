package com.raeynd.hallowed.resurrection;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import com.raeynd.hallowed.bonfire.BonfireHelper;
import com.raeynd.hallowed.currency.CurrencyService;
import com.raeynd.hallowed.currency.ResurrectionCostCalculator;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.data.HallowedPlayerData;
import com.raeynd.hallowed.data.HallowedRecord;
import com.raeynd.hallowed.data.HallowedSavedData;
import com.raeynd.hallowed.network.HallowedNetworking;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

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

        synchronized (savedData) {
            UUID uuid = player.getUUID();

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
                return ResurrectionResult.DELAY_NOT_MET;
            }

            MoneyValue cost = ResurrectionCostCalculator.calculateSelfCostAsValue(record.getXpAtDeath());

            // Attempt withdrawal (internally simulates first)
            if (!CurrencyService.withdrawFunds(player, cost)) {
                player.sendSystemMessage(Component.translatable(
                        "hallowed.resurrection.fail.insufficient_funds", cost.getString(0)));
                return ResurrectionResult.INSUFFICIENT_FUNDS;
            }

            // Resurrection
            savedData.markResurrected(uuid);
            player.setData(HallowedAttachments.HALLOWED_DATA, HallowedPlayerData.DEFAULT);
            player.setHealth(player.getMaxHealth());
            HallowedNetworking.syncToPlayer(player);

            player.sendSystemMessage(Component.translatable("hallowed.resurrection.success.self"));
            LOGGER.info("[Hallowed] {} self-resurrected at bonfire. Cost: {}",
                    player.getGameProfile().getName(), cost.getString(0));
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

            // Attempt withdrawal from the resurrector's funds
            if (!CurrencyService.withdrawFunds(resurrector, cost)) {
                resurrector.sendSystemMessage(Component.translatable(
                        "hallowed.resurrection.fail.insufficient_funds", cost.getString(0)));
                return ResurrectionResult.INSUFFICIENT_FUNDS;
            }

            // Look up the target player on the server
            ServerPlayer target = resurrector.getServer().getPlayerList().getPlayer(targetUUID);

            if (target != null) {
                // Target is online — resurrect immediately
                savedData.markResurrected(targetUUID);
                target.setData(HallowedAttachments.HALLOWED_DATA, HallowedPlayerData.DEFAULT);
                target.setHealth(target.getMaxHealth());
                HallowedNetworking.syncToPlayer(target);

                target.sendSystemMessage(Component.translatable(
                        "hallowed.resurrection.success.target",
                        resurrector.getGameProfile().getName()));
                resurrector.sendSystemMessage(Component.translatable(
                        "hallowed.resurrection.success.other",
                        record.getUsername()));
            } else {
                // Target is offline — set the resurrect-on-login flag
                HallowedRecord flagged = record.withResurrectOnLogin(true);
                savedData.updateRecord(targetUUID, flagged);

                resurrector.sendSystemMessage(Component.translatable(
                        "hallowed.resurrection.offline", record.getUsername()));
            }

            LOGGER.info("[Hallowed] {} resurrected {} (target online={}). Cost: {}",
                    resurrector.getGameProfile().getName(), record.getUsername(),
                    target != null, cost.getString(0));
        }

        return ResurrectionResult.SUCCESS;
    }
}
