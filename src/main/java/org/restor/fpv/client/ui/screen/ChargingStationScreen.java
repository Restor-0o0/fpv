package org.restor.fpv.client.ui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.item.BatteryCharge;
import org.restor.fpv.menu.ChargingStationMenu;

public class ChargingStationScreen extends AbstractContainerScreen<ChargingStationMenu> {
    private static final int SLOTS = 6;

    public ChargingStationScreen(ChargingStationMenu menu, Inventory inv, Component title) {
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
            drawBar(g, x + slot.x + 20, y + slot.y + 5, slot.getItem());
        }
    }

    private void drawBar(GuiGraphics g, int x, int y, ItemStack stack) {
        g.fill(x - 1, y - 1, x + 19, y + 7, 0xFF14141A);
        g.fill(x, y, x + 18, y + 6, 0xFF2F2F38);
        float charge = BatteryCharge.get(stack);
        if (charge < 0) return;
        int w = Math.max(1, Math.round(18 * charge));
        int color = charge >= 0.999f ? 0xFF66CC66 : charge < 0.25f ? 0xFFCC5544 : GuiPanels.ACCENT;
        g.fill(x, y, x + w, y + 6, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.fpv.charging_station.hint"), 8, 20, GuiPanels.LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
