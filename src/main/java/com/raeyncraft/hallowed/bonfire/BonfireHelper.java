package com.raeyncraft.hallowed.bonfire;

import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import wehavecookies56.bonfires.tiles.BonfireTileEntity;

/**
 * Utility helpers for querying the Bonfires mod's block entities.
 *
 * <p>Bonfires is a hard dependency.  The Bonfires JAR must be placed in
 * {@code libs/} before compiling.  See README.md for instructions.
 *
 * <p><b>Import note:</b> {@code BonfireTileEntity} is imported from the Bonfires
 * mod.  If the class path differs in your version of the mod, adjust the import
 * accordingly (the class name and {@code isLit()} method must match exactly).
 */
public final class BonfireHelper {

    /** Radius (in blocks) searched when looking for a nearby lit Bonfire. */
    private static final int BONFIRE_RADIUS = 3;

    private static final Logger LOGGER = LogUtils.getLogger();

    private BonfireHelper() {}

    /**
     * Returns {@code true} if the block entity at {@code pos} is a Bonfires
     * {@code BonfireTileEntity}.
     */
    public static boolean isBonfireBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BonfireTileEntity;
    }

    /**
     * Returns {@code true} if the block entity at {@code pos} is a lit Bonfires
     * {@code BonfireTileEntity}.
     */
    public static boolean isLitBonfire(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BonfireTileEntity bonfire)) {
            return false;
        }
        return bonfire.isLit();
    }

    /**
     * Returns {@code true} if there is at least one lit Bonfire within
     * {@value #BONFIRE_RADIUS} blocks of the player.
     */
    public static boolean isPlayerAtBonfire(ServerPlayer player) {
        return findNearbyBonfire(player).isPresent();
    }

    /**
     * Returns the {@link BlockPos} of the nearest lit Bonfire within
     * {@value #BONFIRE_RADIUS} blocks of the player, or {@link Optional#empty()}.
     */
    public static Optional<BlockPos> findNearbyBonfire(ServerPlayer player) {
        Level level = player.level();
        BlockPos centre = player.blockPosition();

        for (int dx = -BONFIRE_RADIUS; dx <= BONFIRE_RADIUS; dx++) {
            for (int dy = -BONFIRE_RADIUS; dy <= BONFIRE_RADIUS; dy++) {
                for (int dz = -BONFIRE_RADIUS; dz <= BONFIRE_RADIUS; dz++) {
                    BlockPos candidate = centre.offset(dx, dy, dz);
                    if (isLitBonfire(level, candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns {@code true} if the block at {@code pos} in the given {@link ServerLevel}
     * still exists and is a lit Bonfire tile entity.
     *
     * <p>Returns {@code false} if the chunk is not loaded, the block is air, or
     * it is not a lit Bonfire.
     */
    public static boolean isValidLitBonfire(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BonfireTileEntity bonfire)) return false;
        return bonfire.isLit();
    }

    /**
     * Validates the player's stored last-bonfire reference.
     *
     * <p>Reads {@code lastBonfirePos} and {@code lastBonfireDimension} from the
     * player's attachment, checks whether the bonfire still exists, and clears
     * the reference if it does not.
     *
     * @return {@code true} if the bonfire is still valid, {@code false} if the
     *         reference was stale (and has been cleared from the attachment).
     */
    public static boolean validatePlayerBonfire(ServerPlayer player) {
        HallowedPlayerData data = player.getData(HallowedAttachments.HALLOWED_DATA.get());
        BlockPos pos = data.getLastBonfirePos();
        ResourceKey<Level> dim = data.getLastBonfireDimension();

        if (pos == null || dim == null) return false;

        ServerLevel targetLevel = player.getServer().getLevel(dim);
        if (targetLevel == null || !isValidLitBonfire(targetLevel, pos)) {
            // Clear the stale reference
            player.setData(HallowedAttachments.HALLOWED_DATA.get(),
                    data.withLastBonfire(null, null));
            LOGGER.debug("[Hallowed] Cleared stale bonfire reference for {}.", player.getGameProfile().getName());
            return false;
        }
        return true;
    }
}
