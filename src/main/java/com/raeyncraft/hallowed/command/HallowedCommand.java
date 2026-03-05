package com.raeyncraft.hallowed.command;

import java.util.Collection;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedRecord;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.event.PlayerConnectionHandler;
import com.raeyncraft.hallowed.network.HallowedNetworking;
import com.raeyncraft.hallowed.util.RespawnMode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class HallowedCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("hallowed")
                .requires(src -> src.hasPermission(2) && HallowedConfig.SERVER.isAllowCommands())

                .then(Commands.literal("set")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("hallowed")
                            .executes(ctx -> executeSetHallowed(ctx, true)))
                        .then(Commands.literal("human")
                            .executes(ctx -> executeSetHallowed(ctx, false)))))

                .then(Commands.literal("reload")
                    .executes(HallowedCommand::executeReload))

                .then(Commands.literal("status")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(HallowedCommand::executeStatus)))

                .then(Commands.literal("list")
                    .executes(HallowedCommand::executeList))

                .then(Commands.literal("config")
                    .then(Commands.literal("respawn_mode")
                        .then(Commands.argument("value", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (RespawnMode m : RespawnMode.values()) builder.suggest(m.name());
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String val = StringArgumentType.getString(ctx, "value").toUpperCase();
                                try {
                                    RespawnMode mode = RespawnMode.valueOf(val);
                                    HallowedConfig.SERVER.setRespawnMode(mode);
                                    ctx.getSource().sendSuccess(() -> Component.literal("respawn_mode = " + mode.name()), true);
                                } catch (IllegalArgumentException e) {
                                    ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: IN_PLACE, LAST_BONFIRE, BED_OR_WORLDSPAWN"));
                                }
                                return 1;
                            })))
                    .then(Commands.literal("resurrection_delay_seconds")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> setIntConfig(ctx, "resurrection_delay_seconds",
                                IntegerArgumentType.getInteger(ctx, "value"),
                                v -> HallowedConfig.SERVER.setResurrectionDelaySeconds(v)))))
                    .then(Commands.literal("base_cost")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                int v = IntegerArgumentType.getInteger(ctx, "value");
                                HallowedConfig.SERVER.setBaseCost(v);
                                recalculateCosts(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("base_cost = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("other_base_cost")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                int v = IntegerArgumentType.getInteger(ctx, "value");
                                HallowedConfig.SERVER.setOtherBaseCost(v);
                                recalculateCosts(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("other_base_cost = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("level_multiplier")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                int v = IntegerArgumentType.getInteger(ctx, "value");
                                HallowedConfig.SERVER.setLevelMultiplier(v);
                                recalculateCosts(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("level_multiplier = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("scaling_enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                HallowedConfig.SERVER.setScalingEnabled(v);
                                recalculateCosts(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("scaling_enabled = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("factor_number")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int v = IntegerArgumentType.getInteger(ctx, "value");
                                HallowedConfig.SERVER.setFactorNumber(v);
                                recalculateCosts(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("factor_number = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("combat_enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "combat_enabled",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setCombatEnabled(v)))))
                    .then(Commands.literal("damage_multiplier")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                            .executes(ctx -> setDoubleConfig(ctx, "damage_multiplier",
                                DoubleArgumentType.getDouble(ctx, "value"),
                                v -> HallowedConfig.SERVER.setDamageMultiplier(v)))))
                    .then(Commands.literal("hostile_only")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "hostile_only",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setHostileOnly(v)))))
                    .then(Commands.literal("allow_loot")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "allow_loot",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setAllowLoot(v)))))
                    .then(Commands.literal("allow_xp")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "allow_xp",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setAllowXp(v)))))
                    .then(Commands.literal("allow_flight")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                HallowedConfig.SERVER.setAllowFlight(v);
                                reapplyFlightAndNoclip(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("allow_flight = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("allow_block_break")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "allow_block_break",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setAllowBlockBreak(v)))))
                    .then(Commands.literal("allow_block_place")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "allow_block_place",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setAllowBlockPlace(v)))))
                    .then(Commands.literal("blue_overlay_enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                HallowedConfig.SERVER.setBlueOverlayEnabled(v);
                                syncAllHallowed(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("blue_overlay_enabled = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("overlay_intensity")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                            .executes(ctx -> {
                                double v = DoubleArgumentType.getDouble(ctx, "value");
                                HallowedConfig.SERVER.setOverlayIntensity(v);
                                syncAllHallowed(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("overlay_intensity = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("spectral_rendering")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                HallowedConfig.SERVER.setSpectralRendering(v);
                                syncAllHallowed(ctx.getSource());
                                ctx.getSource().sendSuccess(() -> Component.literal("spectral_rendering = " + v), true);
                                return 1;
                            })))
                    .then(Commands.literal("override_hardcore")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "override_hardcore",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setOverrideHardcore(v)))))
                    .then(Commands.literal("enable_you_died_integration")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "enable_you_died_integration",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setEnableYouDiedIntegration(v)))))
                    .then(Commands.literal("burn_in_sunlight")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "burn_in_sunlight",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setBurnInSunlight(v)))))
                    .then(Commands.literal("allow_only_coin_loot")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setBoolConfig(ctx, "allow_only_coin_loot",
                                BoolArgumentType.getBool(ctx, "value"),
                                v -> HallowedConfig.SERVER.setAllowOnlyCoinLoot(v)))))
                    .then(Commands.literal("alpha_level")
                        .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                            .executes(ctx -> setDoubleConfig(ctx, "alpha_level",
                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value"),
                                v -> HallowedConfig.SERVER.setSpectralAlpha(v))))))
        );
    }

    // -------------------------------------------------------------------------
    // Command executors
    // -------------------------------------------------------------------------

    private static int executeSetHallowed(CommandContext<CommandSourceStack> ctx, boolean hallowed) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            CommandSourceStack source = ctx.getSource();
            ServerLevel level = source.getLevel();
            HallowedSavedData savedData = HallowedSavedData.get(level);

            if (hallowed) {
                BlockPos pos = target.blockPosition();
                ResourceKey<Level> dim = target.level().dimension();
                HallowedPlayerData newData = HallowedPlayerData.DEFAULT
                        .withHallowed(true)
                        .withDeathLocation(pos, dim)
                        .withTimeOfDeath(System.currentTimeMillis())
                        .withXpAtDeath(target.experienceLevel);
                target.setData(HallowedAttachments.HALLOWED_DATA.get(), newData);
                savedData.markHallowed(target, pos, dim);
                target.setHealth(target.getMaxHealth());
                PlayerConnectionHandler.stripArmor(target);
                HallowedNetworking.syncToPlayer(target);
                source.sendSuccess(() -> Component.translatable("hallowed.command.set.hallowed.success",
                        target.getGameProfile().getName()), true);
                LOGGER.info("[Hallowed] {} was forced into Hallowed state by {}.",
                        target.getGameProfile().getName(), source.getTextName());
            } else {
                PlayerConnectionHandler.resurrectPlayer(target, savedData);
                source.sendSuccess(() -> Component.translatable("hallowed.command.set.human.success",
                        target.getGameProfile().getName()), true);
                LOGGER.info("[Hallowed] {} was force-resurrected by {}.",
                        target.getGameProfile().getName(), source.getTextName());
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("hallowed.command.error", e.getMessage()));
            return 0;
        }
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        HallowedSavedData savedData = HallowedSavedData.get(level);
        int count = savedData.getHallowedPlayers().size();
        savedData.recalculateAllCosts();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "hallowed.command.reload.recalculated", count), true);
        LOGGER.info("[Hallowed] Config reload requested by {}. Costs recalculated for {} player(s).",
                ctx.getSource().getTextName(), count);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            boolean hallowed = target.getData(HallowedAttachments.HALLOWED_DATA.get()).isHallowed();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    hallowed ? "hallowed.command.status.hallowed" : "hallowed.command.status.human",
                    target.getGameProfile().getName()), false);
            return hallowed ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("hallowed.command.error", e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        HallowedSavedData savedData = HallowedSavedData.get(level);
        Collection<HallowedRecord> hallowed = savedData.getHallowedPlayers();

        if (hallowed.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("hallowed.command.list.empty"), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        for (HallowedRecord record : hallowed) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(record.getUsername());
        }
        String names = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.translatable("hallowed.command.list.result",
                hallowed.size(), names), false);
        LOGGER.info("[Hallowed] /hallowed list: {} player(s) — {}", hallowed.size(), names);
        return hallowed.size();
    }

    // -------------------------------------------------------------------------
    // Runtime apply helpers
    // -------------------------------------------------------------------------

    /** Re-applies flight/noclip to all online hallowed players immediately. */
    private static void reapplyFlightAndNoclip(CommandSourceStack source) {
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (!p.getData(HallowedAttachments.HALLOWED_DATA.get()).isHallowed()) return;
            boolean flight = HallowedConfig.SERVER.isAllowFlight();
            p.getAbilities().mayfly = flight;
            if (!flight) p.getAbilities().flying = false;
            p.onUpdateAbilities();
        });
    }

    /** Syncs updated visual config to all online hallowed players immediately. */
    private static void syncAllHallowed(CommandSourceStack source) {
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (!p.getData(HallowedAttachments.HALLOWED_DATA.get()).isHallowed()) return;
            HallowedNetworking.syncToPlayer(p);
        });
    }

    /** Recalculates resurrection costs for all hallowed players after a cost config change. */
    private static void recalculateCosts(CommandSourceStack source) {
        HallowedSavedData savedData = HallowedSavedData.get(source.getLevel());
        savedData.recalculateAllCosts();
    }

    // -------------------------------------------------------------------------
    // Generic config setter helpers
    // -------------------------------------------------------------------------

    private static int setBoolConfig(CommandContext<CommandSourceStack> ctx, String key,
                                     boolean value, java.util.function.Consumer<Boolean> setter) {
        setter.accept(value);
        ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), true);
        LOGGER.info("[Hallowed] Config {} set to {} by {}.", key, value, ctx.getSource().getTextName());
        return 1;
    }

    private static int setIntConfig(CommandContext<CommandSourceStack> ctx, String key,
                                    int value, java.util.function.Consumer<Integer> setter) {
        setter.accept(value);
        ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), true);
        LOGGER.info("[Hallowed] Config {} set to {} by {}.", key, value, ctx.getSource().getTextName());
        return 1;
    }

    private static int setDoubleConfig(CommandContext<CommandSourceStack> ctx, String key,
                                       double value, java.util.function.Consumer<Double> setter) {
        setter.accept(value);
        ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), true);
        LOGGER.info("[Hallowed] Config {} set to {} by {}.", key, value, ctx.getSource().getTextName());
        return 1;
    }
}