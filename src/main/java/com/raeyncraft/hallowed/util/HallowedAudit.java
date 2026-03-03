package com.raeyncraft.hallowed.util;

import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;

/**
 * Structured audit logging for Hallowed state transitions and coin transactions.
 * All methods write at INFO level so entries appear in server logs.
 */
public final class HallowedAudit {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedAudit() {}

    /**
     * Logs a state transition for a player.
     *
     * @param playerName player's in-game name
     * @param uuid       player UUID
     * @param fromState  previous state (e.g. "ALIVE")
     * @param toState    new state (e.g. "HALLOWED")
     * @param trigger    what caused the transition (e.g. "death", "resurrection", "command")
     */
    public static void logStateTransition(String playerName, UUID uuid,
                                          String fromState, String toState, String trigger) {
        LOGGER.info("[Hallowed] [STATE] {} (UUID={}) {} → {} (trigger: {})",
                playerName, uuid, fromState, toState, trigger);
    }

    /**
     * Logs a coin transaction during a resurrection attempt.
     *
     * @param playerName  player who paid (resurrector)
     * @param uuid        resurrector UUID
     * @param amount      coin amount (in base units)
     * @param type        transaction type (e.g. "self", "other")
     * @param targetName  name of the player being resurrected
     * @param bonfirePos  bonfire position where the transaction occurred
     */
    public static void logCoinTransaction(String playerName, UUID uuid, long amount, String type,
                                          String targetName, BlockPos bonfirePos) {
        LOGGER.info("[Hallowed] [COINS] {} (UUID={}) withdrew {} coins ({}) target={} bonfire={}",
                playerName, uuid, amount, type, targetName, bonfirePos);
    }

    /**
     * Logs a failed resurrection attempt.
     *
     * @param playerName player who attempted the resurrection
     * @param uuid       their UUID
     * @param reason     human-readable failure reason
     * @param targetName name of the target player (may be "self")
     */
    public static void logResurrectionFailed(String playerName, UUID uuid,
                                             String reason, String targetName) {
        LOGGER.info("[Hallowed] [FAILED] {} (UUID={}) resurrection failed: {} target={}",
                playerName, uuid, reason, targetName);
    }
}
