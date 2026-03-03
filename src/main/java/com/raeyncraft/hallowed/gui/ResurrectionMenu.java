package com.raeyncraft.hallowed.gui;

import com.raeyncraft.hallowed.network.ResurrectionListPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-side (and client-side via {@link HallowedMenuTypes}) container menu for
 * the Resurrection GUI.
 *
 * <p>There are no item slots — this menu purely carries the Hallowed player list
 * from server to client via the container extra-data buffer.
 */
public final class ResurrectionMenu extends AbstractContainerMenu {

    private final List<ResurrectionListPayload.Entry> entries;

    /**
     * Client-side constructor — called by the {@link MenuType} factory with extra data.
     */
    public ResurrectionMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(windowId, HallowedMenuTypes.RESURRECTION_MENU.get(), readEntries(buf));
    }

    /**
     * Server-side constructor used by {@link ResurrectionMenuProvider}.
     */
    public ResurrectionMenu(int windowId, MenuType<?> type, List<ResurrectionListPayload.Entry> entries) {
        super(type, windowId);
        this.entries = new ArrayList<>(entries);
    }

    /** Returns an unmodifiable view of the Hallowed player list. */
    public List<ResurrectionListPayload.Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Your implementation here
        return ItemStack.EMPTY;
    }

    // -------------------------------------------------------------------------
    // Serialisation helpers (shared with ResurrectionMenuProvider)
    // -------------------------------------------------------------------------

    static void writeEntries(List<ResurrectionListPayload.Entry> entries, FriendlyByteBuf buf) {
        buf.writeInt(entries.size());
        for (ResurrectionListPayload.Entry e : entries) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.username());
            buf.writeBoolean(e.online());
            buf.writeInt(e.coinsRequired());
            buf.writeLong(e.timeOfDeath());
        }
    }

    private static List<ResurrectionListPayload.Entry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<ResurrectionListPayload.Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new ResurrectionListPayload.Entry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readLong()
            ));
        }
        return list;
    }
}
