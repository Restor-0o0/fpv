package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.restor.fpv.assembly.AssemblyStationMenu;

// Programmatic background (no texture needed): dark panel, slot frames, hint lines
// from the frame slot to its satellites.
public class AssemblyStationScreen extends AbstractContainerScreen<AssemblyStationMenu> {
    public AssemblyStationScreen(AssemblyStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 225;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Panel
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2B33);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF3C3C46);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF23232B);
        // Inventory zone
        g.fill(x + 4, y + 138, x + imageWidth - 4, y + imageHeight - 4, 0xFF2E2E38);

        // Slot frames for every menu slot in the station area
        for (int i = 0; i < AssemblyStationMenu.SLOT_COUNT; i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            drawSlot(g, x + slot.x - 1, y + slot.y - 1, i == AssemblyStationMenu.SLOT_RESULT);
        }
        // Player inventory slots
        for (int i = AssemblyStationMenu.SLOT_COUNT; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            drawSlot(g, x + slot.x - 1, y + slot.y - 1, false);
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y, boolean result) {
        int border = result ? 0xFF66CC66 : 0xFF8B8B99;
        g.fill(x, y, x + 18, y + 18, border);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF17171D);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
