package org.restor.fpv.client.ui.screen;

import net.minecraft.client.gui.GuiGraphics;

// Shared flat-panel drawing used by every fpv screen so they look like one mod.
public final class GuiPanels {
    public static final int EDGE = 0xFF14141A;
    public static final int FRAME = 0xFF3C3C46;
    public static final int BODY = 0xFF23232B;
    public static final int SUNKEN = 0xFF17171D;
    public static final int SLOT_BORDER = 0xFF8B8B99;
    public static final int SLOT_RESULT = 0xFF66CC66;
    public static final int TEXT = 0xFFE0E0E8;
    public static final int LABEL = 0xFF9EA2B0;
    public static final int ACCENT = 0xFFFFB733;

    private GuiPanels() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, BODY);
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    // Slot squares are drawn one pixel outside the 16x16 item area, like vanilla.
    public static void slot(GuiGraphics g, int x, int y, int border) {
        g.fill(x - 1, y - 1, x + 17, y + 17, border);
        g.fill(x, y, x + 16, y + 16, SUNKEN);
    }

    public static void slot(GuiGraphics g, int x, int y) {
        slot(g, x, y, SLOT_BORDER);
    }

    public static void arrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 11, y + 6, color);
        for (int i = 0; i < 5; i++) g.fill(x + 11 + i, y + i, x + 12 + i, y + 9 - i, color);
    }
}
