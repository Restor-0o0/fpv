package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.restor.fpv.entity.CargoPlaneEntity;
import org.restor.fpv.menu.CargoBayMenu;

public class CargoBayScreen extends AbstractContainerScreen<CargoBayMenu> {
    public CargoBayScreen(CargoBayMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 7;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 89;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiPanels.panel(g, x, y, imageWidth, imageHeight);
        GuiPanels.inset(g, x + 40, y + 22, 92, 56, 0xFF2A2A33);
        GuiPanels.inset(g, x + 6, y + 98, imageWidth - 12, 78, 0xFF2A2A33);
        for (int i = 0; i < CargoPlaneEntity.BAY_SLOTS; i++) {
            var slot = menu.slots.get(i);
            GuiPanels.slot(g, x + slot.x, y + slot.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.fpv.cargo_bay.mass",
            String.format("%.1f", menu.cargoMass())), 8, 20, GuiPanels.LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
