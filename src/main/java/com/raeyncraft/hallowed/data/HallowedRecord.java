package com.raeyncraft.hallowed.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable record of a single Hallowed player stored in {@link HallowedSavedData}.
 */
public final class HallowedRecord {

    private final UUID uuid;
    private final String username;

    // Death location
    private final int deathX;
    private final int deathY;
    private final int deathZ;
    private final ResourceKey<Level> deathDimension;

    private final long timeOfDeath;
    private final boolean currentlyOnline;
    private final int coinsRequired;
    private final int xpAtDeath;
    private final boolean resurrectOnLogin;
    private final boolean pendingResurrection;

    public HallowedRecord(
            UUID uuid,
            String username,
            int deathX,
            int deathY,
            int deathZ,
            ResourceKey<Level> deathDimension,
            long timeOfDeath,
            boolean currentlyOnline,
            int coinsRequired,
            int xpAtDeath,
            boolean resurrectOnLogin) {
        this(uuid, username, deathX, deathY, deathZ, deathDimension,
                timeOfDeath, currentlyOnline, coinsRequired, xpAtDeath, resurrectOnLogin, false);
    }

    public HallowedRecord(
            UUID uuid,
            String username,
            int deathX,
            int deathY,
            int deathZ,
            ResourceKey<Level> deathDimension,
            long timeOfDeath,
            boolean currentlyOnline,
            int coinsRequired,
            int xpAtDeath,
            boolean resurrectOnLogin,
            boolean pendingResurrection) {
        this.uuid = uuid;
        this.username = username;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.deathDimension = deathDimension;
        this.timeOfDeath = timeOfDeath;
        this.currentlyOnline = currentlyOnline;
        this.coinsRequired = coinsRequired;
        this.xpAtDeath = xpAtDeath;
        this.resurrectOnLogin = resurrectOnLogin;
        this.pendingResurrection = pendingResurrection;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public int getDeathX() { return deathX; }
    public int getDeathY() { return deathY; }
    public int getDeathZ() { return deathZ; }
    public BlockPos getDeathLocation() { return new BlockPos(deathX, deathY, deathZ); }
    public ResourceKey<Level> getDeathDimension() { return deathDimension; }
    public long getTimeOfDeath() { return timeOfDeath; }
    public boolean isCurrentlyOnline() { return currentlyOnline; }
    public int getCoinsRequired() { return coinsRequired; }
    public int getXpAtDeath() { return xpAtDeath; }
    public boolean isResurrectOnLogin() { return resurrectOnLogin; }
    public boolean isPendingResurrection() { return pendingResurrection; }

    // -------------------------------------------------------------------------
    // Wither-style mutators (return new instances)
    // -------------------------------------------------------------------------

    public HallowedRecord withCurrentlyOnline(boolean online) {
        return new HallowedRecord(uuid, username, deathX, deathY, deathZ, deathDimension,
                timeOfDeath, online, coinsRequired, xpAtDeath, resurrectOnLogin, pendingResurrection);
    }

    public HallowedRecord withResurrectOnLogin(boolean flag) {
        return new HallowedRecord(uuid, username, deathX, deathY, deathZ, deathDimension,
                timeOfDeath, currentlyOnline, coinsRequired, xpAtDeath, flag, pendingResurrection);
    }

    public HallowedRecord withCoinsRequired(int coins) {
        return new HallowedRecord(uuid, username, deathX, deathY, deathZ, deathDimension,
                timeOfDeath, currentlyOnline, coins, xpAtDeath, resurrectOnLogin, pendingResurrection);
    }

    public HallowedRecord withPendingResurrection(boolean pending) {
        return new HallowedRecord(uuid, username, deathX, deathY, deathZ, deathDimension,
                timeOfDeath, currentlyOnline, coinsRequired, xpAtDeath, resurrectOnLogin, pending);
    }
}
