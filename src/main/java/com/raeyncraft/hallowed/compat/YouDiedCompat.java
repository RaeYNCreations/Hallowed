package com.raeyncraft.hallowed.compat;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.network.YouDiedOverlayPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Optional integration with the 'You Died' mod.
 *
 * <p>Checks at mod initialisation whether 'youdied' is present on the class path.
 * All public methods are safe to call regardless of whether You Died is installed.
 */
public final class YouDiedCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code true} if the 'youdied' mod was detected at startup. */
    private static boolean YOU_DIED_LOADED = false;

    private YouDiedCompat() {}

    /**
     * Must be called during mod initialisation to detect the You Died mod.
     */
    public static void init() {
        YOU_DIED_LOADED = ModList.get().isLoaded("youdied");
        LOGGER.info("[Hallowed] You Died integration: {}",
                YOU_DIED_LOADED ? "detected" : "not detected");
    }

    /**
     * Returns {@code true} if the You Died mod is present AND the config option
     * {@code compatibility.enable_you_died_integration} is enabled.
     */
    public static boolean isAvailable() {
        return YOU_DIED_LOADED && HallowedConfig.SERVER.isEnableYouDiedIntegration();
    }

    /**
     * Sends a {@link YouDiedOverlayPayload} to the given player's client so the
     * You Died overlay is shown or hidden.
     *
     * <p>This is a no-op if {@link #isAvailable()} returns {@code false}.
     *
     * @param player the player to notify
     * @param show   {@code true} to show the overlay, {@code false} to dismiss it
     */
    public static void triggerYouDiedOverlay(ServerPlayer player, boolean show) {
        if (!isAvailable()) return;
        PacketDistributor.sendToPlayer(player, new YouDiedOverlayPayload(show));
        LOGGER.debug("[Hallowed] Sent YouDied overlay ({}) to {}.", show, player.getGameProfile().getName());
    }
}
