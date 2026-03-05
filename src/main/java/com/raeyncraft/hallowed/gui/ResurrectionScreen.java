package com.raeyncraft.hallowed.gui;

import com.raeyncraft.hallowed.network.ResurrectionListPayload;
import com.raeyncraft.hallowed.network.ResurrectionRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side Resurrection GUI.
 *
 * <p>Displays a scrollable, searchable list of Hallowed players. Each row shows the
 * player name, online/offline status, and resurrection cost. A selected player can
 * be resurrected via a confirmation dialog that sends
 * {@link ResurrectionRequestPayload} to the server.
 */
public final class ResurrectionScreen extends AbstractContainerScreen<ResurrectionMenu> {

    // Layout constants
    private static final int GUI_WIDTH  = 230;
    private static final int GUI_HEIGHT = 200;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP   = 50;
    private static final int LIST_BOTTOM_OFFSET = 40;

    // State
    private final List<ResurrectionListPayload.Entry> entries;
    private final List<ResurrectionListPayload.Entry> filtered = new ArrayList<>();

    private EditBox searchBox;
    private Button onlineOnlyButton;
    private Button resurrectButton;

    private boolean onlineOnly = false;
    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    // Confirmation dialog state
    private boolean confirmOpen = false;
    private Button confirmYes;
    private Button confirmNo;

    public ResurrectionScreen(ResurrectionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth  = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.entries = new ArrayList<>(menu.getEntries());
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // Search box
        searchBox = new EditBox(font, x + 5, y + 22, GUI_WIDTH - 10, 16,
                Component.translatable("hallowed.gui.resurrection.search"));
        searchBox.setHint(Component.translatable("hallowed.gui.resurrection.search"));
        searchBox.setResponder(text -> {
            scrollOffset = 0;
            selectedIndex = -1;
            rebuildFilter();
        });
        addRenderableWidget(searchBox);

        // Online Only toggle
        onlineOnlyButton = Button.builder(
                Component.translatable("hallowed.gui.resurrection.online_only"),
                btn -> {
                    onlineOnly = !onlineOnly;
                    scrollOffset = 0;
                    selectedIndex = -1;
                    rebuildFilter();
                })
                .bounds(x + GUI_WIDTH - 85, y + 5, 80, 14)
                .build();
        addRenderableWidget(onlineOnlyButton);

        // Resurrect button (enabled only when a row is selected)
        resurrectButton = Button.builder(
                Component.translatable("hallowed.gui.resurrection.confirm"),
                btn -> openConfirmDialog())
                .bounds(x + GUI_WIDTH / 2 - 40, y + GUI_HEIGHT - 30, 80, 20)
                .build();
        resurrectButton.active = false;
        addRenderableWidget(resurrectButton);

        rebuildFilter();
    }

    private void rebuildFilter() {
        filtered.clear();
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        for (ResurrectionListPayload.Entry e : entries) {
            if (onlineOnly && !e.online()) continue;
            if (!query.isEmpty() && !e.username().toLowerCase(Locale.ROOT).contains(query)) continue;
            filtered.add(e);
        }
        selectedIndex = -1;
        if (resurrectButton != null) {
            resurrectButton.active = false;
            resurrectButton.setMessage(Component.translatable("hallowed.gui.resurrection.confirm"));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Only intercept scroll when the mouse is over the list area
        int listTop = topPos + LIST_TOP;
        int listBottom = topPos + GUI_HEIGHT - LIST_BOTTOM_OFFSET;
        if (mouseX < leftPos || mouseX > leftPos + GUI_WIDTH || mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maxOffset = Math.max(0, filtered.size() - visibleRows());
        scrollOffset = (int) Math.max(0, Math.min(maxOffset, scrollOffset - scrollY));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmOpen) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Row selection
        int x = leftPos;
        int y = topPos;
        int listBottom = y + GUI_HEIGHT - LIST_BOTTOM_OFFSET;
        int relY = (int) mouseY - (y + LIST_TOP);
        if (mouseX >= x + 5 && mouseX <= x + GUI_WIDTH - 5 && relY >= 0 && (int) mouseY <= listBottom) {
            int clicked = relY / ROW_HEIGHT + scrollOffset;
            if (clicked < filtered.size()) {
                selectedIndex = clicked;
                resurrectButton.active = true;
                resurrectButton.setMessage(Component.translatable(
                        "hallowed.gui.resurrection.confirm",
                        filtered.get(selectedIndex).username(),
                        filtered.get(selectedIndex).costDisplay()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        renderRows(graphics);
        if (confirmOpen) renderConfirmDialog(graphics);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        // Draw a simple dark panel background
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xC0101010);
        graphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, 0xFFAAAAAA);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font,
                Component.translatable("hallowed.gui.resurrection.title"),
                5, 8, 0xFFFFFF, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int x = leftPos + 5;
        int listBottom = topPos + GUI_HEIGHT - LIST_BOTTOM_OFFSET;
        int rows = visibleRows();

        if (filtered.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("hallowed.gui.resurrection.no_players"),
                    leftPos + 10, topPos + LIST_TOP + 4, 0xAAAAAA, false);
            return;
        }

        for (int i = 0; i < rows; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;

            ResurrectionListPayload.Entry entry = filtered.get(idx);
            int rowY = topPos + LIST_TOP + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > listBottom) break;

            // Highlight selected row
            if (idx == selectedIndex) {
                graphics.fill(x - 2, rowY, x + GUI_WIDTH - 10, rowY + ROW_HEIGHT, 0x80FFFFFF);
            }

            // Online indicator (green = online, red = offline)
            int indicatorColor = entry.online() ? 0x55FF55 : 0xFF5555;
            graphics.fill(x, rowY + 6, x + 8, rowY + 14, indicatorColor);

            // Player name
            graphics.drawString(font, entry.username(), x + 12, rowY + 6, 0xFFFFFF, false);

            // Cost
            String costStr = entry.costDisplay();
            int costWidth = font.width(costStr);
            graphics.drawString(font, costStr, leftPos + GUI_WIDTH - 10 - costWidth, rowY + 6, 0xFFD700, false);

            // Offline resurrect flag
            if (!entry.online()) {
                graphics.drawString(font,
                        Component.translatable("hallowed.gui.resurrection.offline_flag").getString(),
                        x + 12, rowY + 6 + font.lineHeight, 0x888888, false);
            }
        }
    }

    private void openConfirmDialog() {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return;
        confirmOpen = true;

        ResurrectionListPayload.Entry target = filtered.get(selectedIndex);
        int cx = leftPos + GUI_WIDTH / 2;
        int cy = topPos + GUI_HEIGHT / 2;

        confirmYes = Button.builder(
                Component.translatable("hallowed.gui.resurrection.confirm.yes"),
                btn -> {
                    PacketDistributor.sendToServer(new ResurrectionRequestPayload(target.uuid()));
                    onClose();
                })
                .bounds(cx - 50, cy + 10, 45, 20)
                .build();

        confirmNo = Button.builder(
                Component.translatable("hallowed.gui.resurrection.confirm.no"),
                btn -> {
                    confirmOpen = false;
                    removeWidget(confirmYes);
                    removeWidget(confirmNo);
                })
                .bounds(cx + 5, cy + 10, 45, 20)
                .build();

        addRenderableWidget(confirmYes);
        addRenderableWidget(confirmNo);
    }

    private void renderConfirmDialog(GuiGraphics graphics) {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return;
        ResurrectionListPayload.Entry target = filtered.get(selectedIndex);

        int cx = leftPos + GUI_WIDTH / 2;
        int cy = topPos + GUI_HEIGHT / 2;
        int dw = 160, dh = 60;

        graphics.fill(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2, 0xD0000000);
        graphics.renderOutline(cx - dw / 2, cy - dh / 2, dw, dh, 0xFFCCCCCC);

        String confirmText = Component.translatable(
            "hallowed.gui.resurrection.confirm",
            target.username(), target.costDisplay()).getString();
        graphics.drawCenteredString(font, confirmText, cx, cy - dh / 2 + 8, 0xFFFFFF);
    }

    private int visibleRows() {
        int listHeight = (GUI_HEIGHT - LIST_BOTTOM_OFFSET) - LIST_TOP;
        return Math.max(0, listHeight / ROW_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
