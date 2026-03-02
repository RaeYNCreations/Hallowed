package com.raeynd.hallowed.gui;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup: registers GUI screen factories.
 * Loaded only on the {@link Dist#CLIENT} logical side.
 */
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(HallowedMenuTypes.RESURRECTION_MENU.get(), ResurrectionScreen::new));
    }
}
