package com.raeyncraft.hallowed.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Immutable record representing the per-player Hallowed attachment data.
 * Stored via NeoForge AttachmentType; serialised using {@link #CODEC}.
 */
public final class HallowedPlayerData {

    /** Codec used by NeoForge's AttachmentType serialisation. */
    public static final Codec<HallowedPlayerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("isHallowed").forGetter(HallowedPlayerData::isHallowed),
                    BlockPos.CODEC.optionalFieldOf("deathLocation")
                            .forGetter(d -> Optional.ofNullable(d.getDeathLocation())),
                    ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
                            .optionalFieldOf("deathDimension")
                            .forGetter(d -> Optional.ofNullable(d.getDeathDimension())),
                    Codec.LONG.fieldOf("timeOfDeath").forGetter(HallowedPlayerData::getTimeOfDeath),
                    BlockPos.CODEC.optionalFieldOf("lastBonfirePos")
                            .forGetter(d -> Optional.ofNullable(d.getLastBonfirePos())),
                    ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
                            .optionalFieldOf("lastBonfireDimension")
                            .forGetter(d -> Optional.ofNullable(d.getLastBonfireDimension())),
                    Codec.INT.fieldOf("xpAtDeath").forGetter(HallowedPlayerData::getXpAtDeath),
                    Codec.BOOL.fieldOf("resurrectOnLogin").forGetter(HallowedPlayerData::isResurrectOnLogin)
            ).apply(instance, (hallowed, deathLoc, deathDim, time, bonfirePos, bonfireDim, xp, resurrect) ->
                    new HallowedPlayerData(hallowed, deathLoc.orElse(null), deathDim.orElse(null),
                            time, bonfirePos.orElse(null), bonfireDim.orElse(null), xp, resurrect))
    );

    public static final HallowedPlayerData DEFAULT = new HallowedPlayerData(
            false, null, null, 0L, null, null, 0, false
    );

    private final boolean isHallowed;

    @Nullable
    private final BlockPos deathLocation;
    @Nullable
    private final ResourceKey<Level> deathDimension;

    private final long timeOfDeath;

    @Nullable
    private final BlockPos lastBonfirePos;
    @Nullable
    private final ResourceKey<Level> lastBonfireDimension;

    private final int xpAtDeath;
    private final boolean resurrectOnLogin;

    public HallowedPlayerData(
            boolean isHallowed,
            @Nullable BlockPos deathLocation,
            @Nullable ResourceKey<Level> deathDimension,
            long timeOfDeath,
            @Nullable BlockPos lastBonfirePos,
            @Nullable ResourceKey<Level> lastBonfireDimension,
            int xpAtDeath,
            boolean resurrectOnLogin) {
        this.isHallowed = isHallowed;
        this.deathLocation = deathLocation;
        this.deathDimension = deathDimension;
        this.timeOfDeath = timeOfDeath;
        this.lastBonfirePos = lastBonfirePos;
        this.lastBonfireDimension = lastBonfireDimension;
        this.xpAtDeath = xpAtDeath;
        this.resurrectOnLogin = resurrectOnLogin;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isHallowed() { return isHallowed; }

    @Nullable
    public BlockPos getDeathLocation() { return deathLocation; }

    @Nullable
    public ResourceKey<Level> getDeathDimension() { return deathDimension; }

    public long getTimeOfDeath() { return timeOfDeath; }

    @Nullable
    public BlockPos getLastBonfirePos() { return lastBonfirePos; }

    @Nullable
    public ResourceKey<Level> getLastBonfireDimension() { return lastBonfireDimension; }

    public int getXpAtDeath() { return xpAtDeath; }

    public boolean isResurrectOnLogin() { return resurrectOnLogin; }

    // -------------------------------------------------------------------------
    // Builder-style mutators (return new instances to remain immutable)
    // -------------------------------------------------------------------------

    public HallowedPlayerData withHallowed(boolean hallowed) {
        return new HallowedPlayerData(hallowed, deathLocation, deathDimension, timeOfDeath,
                lastBonfirePos, lastBonfireDimension, xpAtDeath, resurrectOnLogin);
    }

    public HallowedPlayerData withDeathLocation(BlockPos pos, ResourceKey<Level> dim) {
        return new HallowedPlayerData(isHallowed, pos, dim, timeOfDeath,
                lastBonfirePos, lastBonfireDimension, xpAtDeath, resurrectOnLogin);
    }

    public HallowedPlayerData withTimeOfDeath(long time) {
        return new HallowedPlayerData(isHallowed, deathLocation, deathDimension, time,
                lastBonfirePos, lastBonfireDimension, xpAtDeath, resurrectOnLogin);
    }

    public HallowedPlayerData withLastBonfire(@Nullable BlockPos pos, @Nullable ResourceKey<Level> dim) {
        return new HallowedPlayerData(isHallowed, deathLocation, deathDimension, timeOfDeath,
                pos, dim, xpAtDeath, resurrectOnLogin);
    }

    public HallowedPlayerData withXpAtDeath(int xp) {
        return new HallowedPlayerData(isHallowed, deathLocation, deathDimension, timeOfDeath,
                lastBonfirePos, lastBonfireDimension, xp, resurrectOnLogin);
    }

    public HallowedPlayerData withResurrectOnLogin(boolean flag) {
        return new HallowedPlayerData(isHallowed, deathLocation, deathDimension, timeOfDeath,
                lastBonfirePos, lastBonfireDimension, xpAtDeath, flag);
    }
}
