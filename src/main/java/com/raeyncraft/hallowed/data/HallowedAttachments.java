package com.raeyncraft.hallowed.data;

import com.raeyncraft.hallowed.Hallowed;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the {@link AttachmentType} used to store {@link HallowedPlayerData} on players.
 */
public final class HallowedAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hallowed.MOD_ID);

    /**
     * Per-player Hallowed state, serialised to NBT via {@link HallowedPlayerData#CODEC}
     * and copied to the respawn entity on death.
     */
    public static final Supplier<AttachmentType<HallowedPlayerData>> HALLOWED_DATA =
            ATTACHMENT_TYPES.register("hallowed_data", () ->
                    AttachmentType.<HallowedPlayerData>builder(() -> HallowedPlayerData.DEFAULT)
                            .serialize(HallowedPlayerData.CODEC)
                            .copyOnDeath()
                            .build());

    private HallowedAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
