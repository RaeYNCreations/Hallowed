package com.raeyncraft.hallowed.gui;

import com.raeyncraft.hallowed.data.HallowedRecord;
import com.raeyncraft.hallowed.data.HallowedSavedData;
import com.raeyncraft.hallowed.network.ResurrectionListPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-side {@link MenuProvider} that opens {@link ResurrectionMenu} for a living player
 * standing at a lit Bonfire.
 */
public final class ResurrectionMenuProvider implements MenuProvider {

    private final HallowedSavedData savedData;

    public ResurrectionMenuProvider(HallowedSavedData savedData) {
        this.savedData = savedData;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("hallowed.gui.resurrection.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        List<ResurrectionListPayload.Entry> entries = buildEntries(savedData);
        return new ResurrectionMenu(windowId, HallowedMenuTypes.RESURRECTION_MENU.get(), entries);
    }

    /**
     * Writes the current Hallowed player list to {@code buf} so the client-side
     * menu factory can reconstruct the data. Called via
     * {@code player.openMenu(provider, buf -> ResurrectionMenuProvider.writeExtraData(savedData, buf))}.
     */
    public static void writeExtraData(HallowedSavedData savedData, FriendlyByteBuf buf) {
        ResurrectionMenu.writeEntries(buildEntries(savedData), buf);
    }

    private static List<ResurrectionListPayload.Entry> buildEntries(HallowedSavedData savedData) {
        return savedData.getHallowedPlayers().stream()
                .map(r -> new ResurrectionListPayload.Entry(
                        r.getUuid(),
                        r.getUsername(),
                        r.isCurrentlyOnline(),
                        r.getCoinsRequired(),
                        r.getTimeOfDeath()))
                .collect(Collectors.toList());
    }
}
