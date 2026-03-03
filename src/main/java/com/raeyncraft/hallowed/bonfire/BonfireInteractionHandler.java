package com.raeyncraft.hallowed.bonfire;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.gui.ResurrectionMenuProvider;
import com.raeyncraft.hallowed.resurrection.ResurrectionEngine;
import com.raeyncraft.hallowed.resurrection.ResurrectionResult;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles player interactions with Bonfire blocks:
 *
 * <ul>
 *   <li><b>Living player, normal right-click</b> — records the Bonfire as the
 *       player's last-used respawn point.</li>
 *   <li><b>Living player, sneak right-click</b> — opens the Resurrection GUI
 *       if there are Hallowed players.</li>
 *   <li><b>Hallowed player, right-click</b> — attempts self-resurrection via
 *       {@link ResurrectionEngine}; cancels the event so the normal Bonfire GUI
 *       does not open.</li>
 * </ul>
 *
 * <p>Registered on {@code NeoForge.EVENT_BUS} in {@link com.raeyncraft.hallowed.Hallowed}.
 */
public final class BonfireInteractionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Minimum milliseconds between resurrection attempts per player. */
    private static final long COOLDOWN_MS = 1_000L;

    /** Tracks the last resurrection attempt timestamp per player UUID. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ServerLevel level = (ServerLevel) player.level();

        // Only interested in lit Bonfires
        if (!BonfireHelper.isLitBonfire(level, event.getPos())) return;

        boolean isHallowed = player.getData(HallowedAttachments.HALLOWED_DATA.get()).isHallowed();

        if (isHallowed) {
            // Hallowed player touches a lit Bonfire → attempt self-resurrection
            event.setCanceled(true);

            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            if (now - cooldowns.getOrDefault(uuid, 0L) < COOLDOWN_MS) {
                LOGGER.debug("[Hallowed] Rate-limited resurrection attempt from {}.", player.getGameProfile().getName());
                return;
            }
            cooldowns.put(uuid, now);

            ResurrectionResult result = ResurrectionEngine.attemptSelfResurrection(serverPlayer);
            LOGGER.debug("[Hallowed] Self-resurrection attempt for {} at bonfire {}: {}",
                    player.getGameProfile().getName(), event.getPos(), result);
        } else if (player.isShiftKeyDown()) {
            // Living player sneak + right-click → open the Resurrection GUI
            event.setCanceled(true);
            HallowedSavedData savedData = HallowedSavedData.get(level);
            if (!savedData.getHallowedPlayers().isEmpty()) {
                serverPlayer.openMenu(new ResurrectionMenuProvider(savedData),
                        (RegistryFriendlyByteBuf buf) -> ResurrectionMenuProvider.writeExtraData(savedData, buf));
            }
        } else {
            // Living player normal right-click → register this Bonfire as respawn point
            HallowedPlayerData current = player.getData(HallowedAttachments.HALLOWED_DATA.get());
            HallowedPlayerData updated = current.withLastBonfire(event.getPos(), level.dimension());
            player.setData(HallowedAttachments.HALLOWED_DATA.get(), updated);
            serverPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("hallowed.resurrection.bonfire_registered"));
            LOGGER.info("[Hallowed] {} registered bonfire at {} in {}",
                    player.getGameProfile().getName(), event.getPos(), level.dimension().location());
        }
    }
}
