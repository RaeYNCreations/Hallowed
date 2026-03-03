package com.raeyncraft.hallowed.util;

import com.raeyncraft.hallowed.Hallowed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom {@link SoundEvent}s for the Hallowed mod.
 */
public final class HallowedSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Hallowed.MOD_ID);

    /** Played when a resurrection completes successfully. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RESURRECTION_SUCCESS =
            SOUNDS.register("resurrection_success",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "resurrection_success")));

    /** Played when a player enters the Hallowed state. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BECOME_HALLOWED =
            SOUNDS.register("become_hallowed",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "become_hallowed")));

    private HallowedSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
