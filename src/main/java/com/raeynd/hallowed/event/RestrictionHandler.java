package com.raeynd.hallowed.event;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.HallowedConfig;
import com.raeynd.hallowed.bonfire.BonfireHelper;
import com.raeynd.hallowed.currency.CurrencyService;
import com.raeynd.hallowed.data.HallowedAttachments;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

/**
 * Enforces all Hallowed-state restrictions on the server side.
 * All enforcement is done by cancelling relevant NeoForge events.
 * Client-side events are ignored (server-authoritative).
 */
public final class RestrictionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // -------------------------------------------------------------------------
    // Block interactions
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked block-break for {}.", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked block-place for {}.", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // Allow all players (including Hallowed) to interact with lit Bonfires;
        // BonfireInteractionHandler handles the actual logic for each case.
        if (BonfireHelper.isLitBonfire(player.level(), event.getPos())) return;

        if (!isHallowed(player)) return;

        // Allow doors, trapdoors, fence gates, levers, buttons
        Block block = player.level().getBlockState(event.getPos()).getBlock();
        if (isAllowedInteraction(block)) return;

        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked right-click block for {}.", player.getGameProfile().getName());
    }

    private boolean isAllowedInteraction(Block block) {
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof LeverBlock
                || block instanceof ButtonBlock;
    }

    // -------------------------------------------------------------------------
    // Item interactions
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked item-use for {}.", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked item-drop for {}.", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (!isHallowed(player)) return;
        // Allow Hallowed players to pick up Lightman's Currency coin items
        if (CurrencyService.isCoinItem(player, event.getItem().getItem())) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked item-pickup for {}.", player.getGameProfile().getName());
    }

    // -------------------------------------------------------------------------
    // Entity interactions
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked entity-interact for {}.", player.getGameProfile().getName());
    }

    // -------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (!isHallowed(attacker)) return;

        HallowedConfig cfg = HallowedConfig.SERVER;

        if (!cfg.isCombatEnabled()) {
            event.setCanceled(true);
            LOGGER.debug("[Hallowed] Blocked attack (combat disabled) for {}.", attacker.getGameProfile().getName());
            return;
        }

        // Combat enabled: only bare-fist attacks
        ItemStack heldItem = attacker.getMainHandItem();
        if (!heldItem.isEmpty()) {
            event.setCanceled(true);
            LOGGER.debug("[Hallowed] Blocked weapon attack for {}.", attacker.getGameProfile().getName());
            return;
        }

        // hostile_only check
        if (cfg.isHostileOnly() && !(event.getTarget() instanceof Monster)) {
            event.setCanceled(true);
            LOGGER.debug("[Hallowed] Blocked attack on non-hostile for {}.", attacker.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (!isHallowed(attacker)) return;
        if (!HallowedConfig.SERVER.isCombatEnabled()) return;

        // Apply damage multiplier
        float scaled = event.getAmount() * (float) HallowedConfig.SERVER.getDamageMultiplier();
        event.setAmount(scaled);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (!isHallowed(attacker)) return;
        if (!HallowedConfig.SERVER.isAllowLoot()) {
            event.setCanceled(true);
            LOGGER.debug("[Hallowed] Suppressed loot drop for kill by {}.", attacker.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        Player attacker = event.getAttackingPlayer();
        if (attacker == null || !isHallowed(attacker)) return;
        if (!HallowedConfig.SERVER.isAllowXp()) {
            event.setCanceled(true);
            LOGGER.debug("[Hallowed] Suppressed XP drop for kill by {}.", attacker.getGameProfile().getName());
        }
    }

    // -------------------------------------------------------------------------
    // XP pickup (general Hallowed restriction)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (!isHallowed(player)) return;
        event.setCanceled(true);
        LOGGER.debug("[Hallowed] Blocked XP pickup for {}.", player.getGameProfile().getName());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isHallowed(Player player) {
        return player.getData(HallowedAttachments.HALLOWED_DATA).isHallowed();
    }
}
