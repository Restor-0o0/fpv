package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.restor.fpv.entity.CarrierPlaneEntity;
import org.restor.fpv.menu.CarrierMenu;

public class CarrierScreen extends AbstractContainerScreen<CarrierMenu> {
    private static final int SLOTS = CarrierPlaneEntity.HARDPOINTS;

    public CarrierScreen(CarrierMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 182;
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
        GuiPanels.inset(g, x + 6, y + 30, imageWidth - 12, 50, 0xFF2A2A33);
        GuiPanels.inset(g, x + 6, y + 94, imageWidth - 12, 78, 0xFF2A2A33);
        for (int i = 0; i < SLOTS; i++) {
            var slot = menu.slots.get(i);
            GuiPanels.slot(g, x + slot.x, y + slot.y);
        }

        float load = menu.loadedMass();
        float frac = Math.min(1f, load / CarrierPlaneEntity.MAX_PAYLOAD_MASS);
        int bx = x + 10, by = y + 22, bw = imageWidth - 20;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF14141A);
        g.fill(bx, by, bx + bw, by + 6, 0xFF2F2F38);
        int color = frac > 0.85f ? 0xFFCC5544 : frac > 0.6f ? GuiPanels.ACCENT : 0xFF66CC66;
        g.fill(bx, by, bx + Math.round(bw * frac), by + 6, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.fpv.carrier.load",
            String.format("%.1f", menu.loadedMass()), String.format("%.0f", CarrierPlaneEntity.MAX_PAYLOAD_MASS)),
            8, 20, GuiPanels.LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
