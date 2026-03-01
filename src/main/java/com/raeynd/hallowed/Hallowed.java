package com.raeynd.hallowed;

import com.mojang.logging.LogUtils;
import com.raeynd.hallowed.command.HallowedCommand;
import com.raeynd.hallowed.data.HallowedAttachments;
import com.raeynd.hallowed.event.DeathHandler;
import com.raeynd.hallowed.event.PlayerConnectionHandler;
import com.raeynd.hallowed.event.RestrictionHandler;
import com.raeynd.hallowed.network.HallowedNetworking;
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

        // Register server-side game event handlers on the NeoForge event bus
        NeoForge.EVENT_BUS.register(new DeathHandler());
        NeoForge.EVENT_BUS.register(new RestrictionHandler());
        NeoForge.EVENT_BUS.register(new PlayerConnectionHandler());

        // Register commands
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("[Hallowed] Initialisation complete.");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        HallowedCommand.register(event.getDispatcher());
    }
}
