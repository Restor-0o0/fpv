package org.restor.fpv.client.ui.screen.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class SettingsUi {
    static final int WINDOW = 0xF2181A20;
    static final int WINDOW_EDGE = 0xFF2B2E37;
    static final int SIDEBAR = 0xFF121419;
    static final int ROW_HOVER = 0x10FFFFFF;
    static final int TAB_HOVER = 0x14FFFFFF;
    static final int TAB_ACTIVE = 0x22FFB733;
    static final int TEXT = 0xFFE8EAF0;
    static final int MUTED = 0xFF8D92A0;
    static final int FAINT = 0xFF5A5F6B;
    static final int ACCENT = 0xFFFFB733;
    static final int TRACK = 0xFF2E313B;
    static final int CHIP = 0xFF22252D;
    static final int CHIP_HOVER = 0xFF2C3039;
    static final int DANGER = 0xFFFF5F5F;
    static final int KNOB = 0xFFF4F5F8;

    private SettingsUi() {}

    static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    // 12x12 quad silhouette: X arms, four rotors, a body.
    static void droneIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, color);
            g.fill(x + 9 - i, y + 2 + i, x + 10 - i, y + 3 + i, color);
        }
        int[][] rotors = {{0, 0}, {8, 0}, {0, 8}, {8, 8}};
        for (int[] r : rotors) {
            g.fill(x + r[0] + 1, y + r[1], x + r[0] + 3, y + r[1] + 4, color);
            g.fill(x + r[0], y + r[1] + 1, x + r[0] + 4, y + r[1] + 3, color);
        }
        g.fill(x + 4, y + 4, x + 8, y + 8, color);
    }
}
