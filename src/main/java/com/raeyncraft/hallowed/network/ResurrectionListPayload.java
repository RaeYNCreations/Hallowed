package com.raeyncraft.hallowed.network;

import com.raeyncraft.hallowed.Hallowed;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload that carries the list of currently Hallowed players.
 * Sent when a living player opens the Resurrection GUI at a Bonfire.
 */
public record ResurrectionListPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResurrectionListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hallowed.MOD_ID, "resurrection_list"));

            public static final StreamCodec<FriendlyByteBuf, ResurrectionListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.entries().size());
                        for (Entry e : payload.entries()) {
                            buf.writeUUID(e.uuid());
                            buf.writeUtf(e.username());
                            buf.writeBoolean(e.online());
                            buf.writeInt(e.coinsRequired());
                            buf.writeLong(e.timeOfDeath());
                            buf.writeUtf(e.costDisplay());
                        }
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<Entry> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(new Entry(
                                    buf.readUUID(),
                                    buf.readUtf(),
                                    buf.readBoolean(),
                                    buf.readInt(),
                                    buf.readLong(),
                                    buf.readUtf()
                            ));
                        }
                        return new ResurrectionListPayload(list);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Data for a single Hallowed player shown in the Resurrection GUI.
     *
     * @param uuid          player UUID
     * @param username      display name
     * @param online        whether the player is currently online
     * @param coinsRequired coin cost for resurrection
     * @param timeOfDeath   epoch-millis of the death event
     */
    public record Entry(UUID uuid, String username, boolean online, int coinsRequired, long timeOfDeath, String costDisplay) {}
}
