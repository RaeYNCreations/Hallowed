package com.raeyncraft.hallowed.event;

import com.raeyncraft.hallowed.Hallowed;
import com.raeyncraft.hallowed.HallowedConfig;
import com.raeyncraft.hallowed.data.HallowedAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Enforces server-side Hallowed state every player tick.
 */
@EventBusSubscriber(modid = Hallowed.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class HallowedServerTickHandler {

    private HallowedServerTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        boolean isHallowed = player.getData(HallowedAttachments.HALLOWED_DATA.get()).isHallowed();
        if (!isHallowed) return;

        // Enforce sun burning
        if (!HallowedConfig.SERVER.isBurnInSunlight()) return;
        if (player.level().isDay()
                && !player.level().isRainingAt(player.blockPosition())
                && player.level().canSeeSky(player.blockPosition())) {
            if (!player.isOnFire()) {
                player.setRemainingFireTicks(200);
            }
        }
    }
}
