package com.raeyncraft.hallowed.command;

import java.util.Collection;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.data.HallowedPlayerData;
import com.raeyncraft.hallowed.data.HallowedRecord;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.event.PlayerConnectionHandler;
import com.raeyncraft.hallowed.network.HallowedNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Registers all /hallowed admin commands.
 * All sub-commands require OP level 2 or higher.
 */
public final class HallowedCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HallowedCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("hallowed")
                        .requires(src -> src.hasPermission(2) && HallowedConfig.SERVER.isAllowCommands())

                        // /hallowed set <player> hallowed|human
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("hallowed")
                                                .executes(ctx -> executeSetHallowed(ctx, true)))
                                        .then(Commands.literal("human")
                                                .executes(ctx -> executeSetHallowed(ctx, false)))))

                        // /hallowed reload
                        .then(Commands.literal("reload")
                                .executes(HallowedCommand::executeReload))

                        // /hallowed status <player>
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(HallowedCommand::executeStatus)))

                        // /hallowed list
                        .then(Commands.literal("list")
                                .executes(HallowedCommand::executeList))
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
                // Force into Hallowed state
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
                HallowedNetworking.syncToPlayer(target);

                source.sendSuccess(() -> Component.translatable("hallowed.command.set.hallowed.success",
                        target.getGameProfile().getName()), true);
                LOGGER.info("[Hallowed] {} was forced into Hallowed state by {}.",
                        target.getGameProfile().getName(), source.getTextName());
            } else {
                // Force resurrect
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
        // NeoForge config reloads automatically on file change; this command is a manual trigger point.
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
}
