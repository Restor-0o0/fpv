package org.restor.fpv.client.ui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.item.BatteryCharge;
import org.restor.fpv.menu.GuardStationMenu;

public class GuardStationScreen extends AbstractContainerScreen<GuardStationMenu> {
    private static final int SLOTS = 4;

    public GuardStationScreen(GuardStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 178;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 7;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 85;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiPanels.panel(g, x, y, imageWidth, imageHeight);
        GuiPanels.inset(g, x + 6, y + 20, imageWidth - 12, 60, 0xFF2A2A33);
        GuiPanels.inset(g, x + 6, y + 94, imageWidth - 12, 78, 0xFF2A2A33);

        for (int i = 0; i < SLOTS; i++) {
            var slot = menu.slots.get(i);
            GuiPanels.slot(g, x + slot.x, y + slot.y);
            drawBar(g, x + slot.x, y + slot.y + 20, slot.getItem());
        }
    }

    private void drawBar(GuiGraphics g, int x, int y, ItemStack stack) {
        g.fill(x - 1, y - 1, x + 17, y + 5, 0xFF14141A);
        g.fill(x, y, x + 16, y + 4, 0xFF2F2F38);
        float charge = BatteryCharge.get(stack);
        if (charge < 0) return;
        int w = Math.max(1, Math.round(16 * charge));
        int color = charge >= 0.85f ? 0xFF66CC66 : charge < 0.25f ? 0xFFCC5544 : GuiPanels.ACCENT;
        g.fill(x, y, x + w, y + 4, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int y = 58;
        for (var line : font.split(Component.translatable("screen.fpv.guard_station.hint"), imageWidth - 24)) {
            g.drawString(font, line, 8, y, GuiPanels.LABEL, false);
            y += 10;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
