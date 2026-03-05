package com.raeyncraft.hallowed;

import com.mojang.logging.LogUtils;
import com.raeyncraft.hallowed.bonfire.BonfireInteractionHandler;
import com.raeyncraft.hallowed.command.HallowedCommand;
import com.raeyncraft.hallowed.compat.YouDiedCompat;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import com.raeyncraft.hallowed.event.DeathHandler;
import com.raeyncraft.hallowed.event.PlayerConnectionHandler;
import com.raeyncraft.hallowed.event.RestrictionHandler;
import com.raeyncraft.hallowed.gui.HallowedMenuTypes;
import com.raeyncraft.hallowed.network.HallowedNetworking;
import com.raeyncraft.hallowed.util.HallowedSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Main entry point for the Hallowed mod.
 * Registers configuration, attachments, networking, and event handlers.
 */
@Mod(Hallowed.MOD_ID)
public final class Hallowed {

    public static final String MOD_ID = "hallowed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Hallowed(IEventBus modBus, ModContainer container) {
        LOGGER.info("[Hallowed] Initialising Hallowed mod.");

        // Register config
        HallowedConfig.register(container);

        // Register attachment types
        HallowedAttachments.register(modBus);

        // Register network payloads
        HallowedNetworking.register(modBus);

        // Register menu types
        HallowedMenuTypes.register(modBus);

        // Register custom sounds
        HallowedSounds.register(modBus);

        // Register server-side game event handlers on the NeoForge event bus
        NeoForge.EVENT_BUS.register(new DeathHandler());
        NeoForge.EVENT_BUS.register(new RestrictionHandler());
        NeoForge.EVENT_BUS.register(new PlayerConnectionHandler());
        NeoForge.EVENT_BUS.register(new BonfireInteractionHandler());

        // Register commands
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Detect optional You Died integration
        YouDiedCompat.init();

        LOGGER.info("[Hallowed] Initialisation complete.");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        HallowedCommand.register(event.getDispatcher());
    }
}