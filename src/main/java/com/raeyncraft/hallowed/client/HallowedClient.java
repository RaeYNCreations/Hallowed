package com.raeyncraft.hallowed.client;

import com.raeyncraft.hallowed.Hallowed;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side mod initialisation.
 * Forces loading of all client-only classes that use @EventBusSubscriber.
 */
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HallowedClient {

    private HallowedClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Force-load client event handler classes so their @EventBusSubscriber
        // annotations register their handlers even if nothing else references them.
        Class<?> ignored1 = HallowedSpectralRenderer.class;
        Class<?> ignored2 = HallowedOverlayRenderer.class;

        Hallowed.LOGGER.info("[Hallowed] Client setup complete.");
    }
}
