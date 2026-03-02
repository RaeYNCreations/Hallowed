package com.raeynd.hallowed.data;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-scoped {@link SavedData} that persists the set of currently Hallowed players
 * across server restarts.  UUID is the authoritative key.
 *
 * <p>Access via {@link #get(ServerLevel)}.
 */
public final class HallowedSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "hallowed_data";

    /** Thread-safe storage keyed by player UUID. */
    private final Map<UUID, HallowedRecord> records = new ConcurrentHashMap<>();

    /**
     * UUIDs that are currently mid-resurrection.  Used to prevent concurrent
     * resurrection attempts for the same target.
     */
    private final Set<UUID> pendingLocks = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // Factory / access
    // -------------------------------------------------------------------------

    private static final SavedData.Factory<HallowedSavedData> FACTORY =
            new SavedData.Factory<>(HallowedSavedData::new, HallowedSavedData::load, null);

    /**
     * Returns (or creates) the HallowedSavedData for the given level's server.
     * Must be called on the server thread.
     */
    public static HallowedSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Marks the given player as Hallowed, recording their death location and XP level.
     * Calls {@link #setDirty()} immediately.
     */
    public synchronized void markHallowed(Player player, BlockPos deathPos, ResourceKey<Level> dimension) {
        UUID uuid = player.getUUID();
        int coinsRequired = computeCoinsRequired(player);

        HallowedRecord record = new HallowedRecord(
                uuid,
                player.getGameProfile().getName(),
                deathPos.getX(),
                deathPos.getY(),
                deathPos.getZ(),
                dimension,
                System.currentTimeMillis(),
                true,
                coinsRequired,
                player.experienceLevel,
                false
        );
        records.put(uuid, record);
        setDirty();
        LOGGER.info("[Hallowed] {} (UUID={}) is now Hallowed at {} in {}",
                player.getGameProfile().getName(), uuid, deathPos, dimension.location());
    }

    /**
     * Removes the Hallowed record for the given UUID (resurrection).
     * Calls {@link #setDirty()} immediately.
     */
    public synchronized void markResurrected(UUID uuid) {
        HallowedRecord removed = records.remove(uuid);
        if (removed != null) {
            setDirty();
            LOGGER.info("[Hallowed] {} (UUID={}) has been resurrected.", removed.getUsername(), uuid);
        }
    }

    /**
     * Updates the {@code currentlyOnline} flag for a player.
     */
    public synchronized void setOnlineStatus(UUID uuid, boolean online) {
        HallowedRecord record = records.get(uuid);
        if (record != null) {
            records.put(uuid, record.withCurrentlyOnline(online));
            setDirty();
        }
    }

    /**
     * Returns {@code true} if the given UUID is currently Hallowed.
     */
    public boolean isHallowed(UUID uuid) {
        return records.containsKey(uuid);
    }

    /**
     * Returns an unmodifiable view of all current Hallowed records.
     */
    public Collection<HallowedRecord> getHallowedPlayers() {
        return Collections.unmodifiableCollection(records.values());
    }

    /**
     * Returns the record for the given UUID, or {@code null} if not Hallowed.
     */
    @Nullable
    public HallowedRecord getHallowedRecord(UUID uuid) {
        return records.get(uuid);
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    private static final String TAG_RECORDS = "records";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_USERNAME = "username";
    private static final String TAG_DEATH_X = "death_x";
    private static final String TAG_DEATH_Y = "death_y";
    private static final String TAG_DEATH_Z = "death_z";
    private static final String TAG_DEATH_DIM = "death_dimension";
    private static final String TAG_TIME_OF_DEATH = "time_of_death";
    private static final String TAG_ONLINE = "currently_online";
    private static final String TAG_COINS = "coins_required";
    private static final String TAG_XP = "xp_at_death";
    private static final String TAG_RESURRECT = "resurrect_on_login";
    private static final String TAG_PENDING = "pending_resurrection";

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (HallowedRecord r : records.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_UUID, r.getUuid().toString());
            entry.putString(TAG_USERNAME, r.getUsername());
            entry.putInt(TAG_DEATH_X, r.getDeathX());
            entry.putInt(TAG_DEATH_Y, r.getDeathY());
            entry.putInt(TAG_DEATH_Z, r.getDeathZ());
            entry.putString(TAG_DEATH_DIM, r.getDeathDimension().location().toString());
            entry.putLong(TAG_TIME_OF_DEATH, r.getTimeOfDeath());
            entry.putBoolean(TAG_ONLINE, r.isCurrentlyOnline());
            entry.putInt(TAG_COINS, r.getCoinsRequired());
            entry.putInt(TAG_XP, r.getXpAtDeath());
            entry.putBoolean(TAG_RESURRECT, r.isResurrectOnLogin());
            entry.putBoolean(TAG_PENDING, r.isPendingResurrection());
            list.add(entry);
        }
        tag.put(TAG_RECORDS, list);
        return tag;
    }

    private static HallowedSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HallowedSavedData data = new HallowedSavedData();
        ListTag list = tag.getList(TAG_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(TAG_UUID));
                String username = entry.getString(TAG_USERNAME);
                int x = entry.getInt(TAG_DEATH_X);
                int y = entry.getInt(TAG_DEATH_Y);
                int z = entry.getInt(TAG_DEATH_Z);
                ResourceKey<Level> dim = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(entry.getString(TAG_DEATH_DIM)));
                long timeOfDeath = entry.getLong(TAG_TIME_OF_DEATH);
                boolean online = entry.getBoolean(TAG_ONLINE);
                int coins = entry.getInt(TAG_COINS);
                int xp = entry.getInt(TAG_XP);
                boolean resurrect = entry.getBoolean(TAG_RESURRECT);
                boolean pending = entry.getBoolean(TAG_PENDING);

                HallowedRecord record = new HallowedRecord(uuid, username, x, y, z, dim,
                        timeOfDeath, online, coins, xp, resurrect, pending);
                data.records.put(uuid, record);
            } catch (Exception e) {
                LOGGER.warn("[Hallowed] Failed to load record at index {}: {}", i, e.getMessage());
            }
        }
        LOGGER.info("[Hallowed] Loaded {} Hallowed player record(s).", data.records.size());

        // Recover any resurrections that were in-flight when the server crashed.
        // If pendingResurrection=true, the funds were already withdrawn so we must
        // complete the resurrection to avoid double-charging on the next attempt.
        List<UUID> toComplete = new ArrayList<>();
        for (HallowedRecord r : data.records.values()) {
            if (r.isPendingResurrection()) {
                toComplete.add(r.getUuid());
            }
        }
        for (UUID uuid : toComplete) {
            LOGGER.warn("[Hallowed] Completing pending resurrection for {} after server restart.",
                    data.records.get(uuid).getUsername());
            data.records.remove(uuid);
        }
        if (!toComplete.isEmpty()) {
            data.setDirty();
        }

        return data;
    }

    /**
     * Replaces the record for {@code uuid} with {@code updated} and calls
     * {@link #setDirty()}.  Used by the resurrection engine to set flags on
     * offline players (e.g. {@code resurrectOnLogin}).
     */
    public synchronized void updateRecord(UUID uuid, HallowedRecord updated) {
        records.put(uuid, updated);
        setDirty();
    }

    /**
     * Marks the resurrection as pending (mid-transaction safety).
     * Called before funds are withdrawn so a crash can be recovered on restart.
     */
    public synchronized void setPendingResurrection(UUID uuid, boolean pending) {
        HallowedRecord record = records.get(uuid);
        if (record != null) {
            records.put(uuid, record.withPendingResurrection(pending));
            setDirty();
        }
    }

    /**
     * Attempts to acquire a resurrection lock for {@code uuid}.
     *
     * @return {@code true} if the lock was acquired (no concurrent attempt),
     *         {@code false} if another resurrection for this UUID is already in progress.
     */
    public synchronized boolean tryLock(UUID uuid) {
        return pendingLocks.add(uuid);
    }

    /**
     * Releases the resurrection lock for {@code uuid}.
     */
    public synchronized void unlock(UUID uuid) {
        pendingLocks.remove(uuid);
    }

    /**
     * Recalculates resurrection costs for all currently-Hallowed players using
     * the current config values.  Called after a config reload so cost changes
     * take effect immediately without requiring a restart.
     */
    public synchronized void recalculateAllCosts() {
        HallowedConfig cfg = HallowedConfig.SERVER;
        int base = cfg.getBaseCost();
        boolean scaling = cfg.isScalingEnabled();
        int multiplier = cfg.getLevelMultiplier();
        int factor = cfg.getFactorNumber();

        for (Map.Entry<UUID, HallowedRecord> entry : records.entrySet()) {
            HallowedRecord r = entry.getValue();
            int newCost = base;
            if (scaling) {
                newCost += (r.getXpAtDeath() * multiplier) / factor;
            }
            records.put(entry.getKey(), r.withCoinsRequired(newCost));
        }
        setDirty();
        LOGGER.info("[Hallowed] Recalculated costs for {} Hallowed player(s) after config change.",
                records.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int computeCoinsRequired(Player player) {
        HallowedConfig cfg = HallowedConfig.SERVER;
        int base = cfg.getBaseCost();
        if (cfg.isScalingEnabled()) {
            base += (player.experienceLevel * cfg.getLevelMultiplier()) / cfg.getFactorNumber();
        }
        return base;
    }
}
