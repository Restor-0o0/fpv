package org.restor.fpv.client.ui.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

// One "[-] label: value [+]" row. The plate is drawn behind the widgets and the
// text on top of them, so a wide label can never end up under a button.
public record StepperRow(int x, int y, int width, Component label, Supplier<String> value) {
    public static final int BUTTON = 20;
    public static final int GAP = 2;

    public int minusX() { return x; }
    public int plusX() { return x + width - BUTTON; }
    public int plateX() { return x + BUTTON + GAP; }
    public int plateWidth() { return width - 2 * (BUTTON + GAP); }

    public void drawPlate(GuiGraphics g) {
        GuiPanels.inset(g, plateX(), y, plateWidth(), BUTTON, 0xFF1B1B22);
    }

    public void drawText(GuiGraphics g, Font font) {
        int ty = y + (BUTTON - 8) / 2;
        g.drawString(font, label, plateX() + 5, ty, GuiPanels.LABEL, false);
        String v = value.get();
        g.drawString(font, v, plateX() + plateWidth() - 5 - font.width(v), ty, GuiPanels.TEXT, false);
    }
}
