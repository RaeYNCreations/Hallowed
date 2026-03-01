package com.raeynd.hallowed.bonfire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

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

    private BonfireHelper() {}

    /**
     * Returns {@code true} if the block entity at {@code pos} is a Bonfires
     * {@code BonfireTileEntity}.
     */
    public static boolean isBonfireBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof com.mango.bonfires.block.entity.BonfireTileEntity;
    }

    /**
     * Returns {@code true} if the block entity at {@code pos} is a lit Bonfires
     * {@code BonfireTileEntity}.
     */
    public static boolean isLitBonfire(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof com.mango.bonfires.block.entity.BonfireTileEntity bonfire)) return false;
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
}
