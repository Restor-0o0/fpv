package org.restor.fpv.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.assembly.ComponentCondition;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.ModularDroneEntity;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.entity.DroneEntity;

// Airframe schematic in the bottom right: one ring per motor arm placed at the real
// mount position, plus a body dot for the frame. Colour is condition.
final class DamageHud {
    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static final int OK = 0xFF60D060;
    private static final int WARN = 0xFFFFAA20;
    private static final int CRIT = 0xFFFF3030;
    private static final int FRAME = 0x90FFFFFF;

    private static final int PANEL_SIZE = 46;
    private static final int ARM_RADIUS = 16;
    private static final int MOTOR_RADIUS = 5;

    private DamageHud() {}

    static void render(GuiGraphicsExtractor graphics, int width, int height, DroneEntity drone) {
        if (!(drone instanceof ModularDroneEntity modular)) return;
        DroneAssembly assembly = modular.getAssembly();
        if (assembly == null) return;
        FrameSpec spec = assembly.frameSpec();
        int motorCount = spec != null ? spec.motorCount() : 0;
        if (motorCount == 0) return;

        int cx = width - 10 - PANEL_SIZE / 2;
        int cy = height - 10 - PANEL_SIZE / 2;
        boolean blink = mc().level == null || (mc().level.getGameTime() % 12) < 7;

        float spread = 0f;
        for (int i = 0; i < motorCount; i++) {
            MountPoint m = spec.motorMounts().get(i);
            spread = Math.max(spread, Math.max(Math.abs(m.x()), Math.abs(m.z())));
        }
        if (spread <= 0.0001f) spread = 1f;
        float scale = ARM_RADIUS / spread;

        for (int i = 0; i < motorCount; i++) {
            MountPoint m = spec.motorMounts().get(i);
            int mx = cx + Math.round(m.x() * scale);
            int my = cy + Math.round(m.z() * scale);
            drawLine(graphics, cx, cy, mx, my, FRAME);
            drawRing(graphics, mx, my, MOTOR_RADIUS, conditionColor(ratioOf(assembly.props[i]), blink));
            drawDisc(graphics, mx, my, MOTOR_RADIUS - 2, conditionColor(ratioOf(assembly.motors[i]), blink));
        }

        drawDisc(graphics, cx, cy, 5, conditionColor(ratioOf(assembly.frame), blink));
    }

    private static int conditionColor(float ratio, boolean blink) {
        if (ratio >= 0.95f) return 0x70FFFFFF;
        if (ratio < 0.30f) return blink ? CRIT : 0x60FF3030;
        return ratio < 0.60f ? WARN : OK;
    }

    private static float ratioOf(ItemStack stack) {
        return stack.isEmpty() ? 1f : ComponentCondition.ratio(stack);
    }

    private static void drawDisc(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawRing(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int innerSq = (r - 1) * (r - 1) - dy * dy;
            int inner = innerSq > 0 ? (int) Math.round(Math.sqrt(innerSq)) : -1;
            if (inner < 0) {
                graphics.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, color);
            } else {
                graphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                graphics.fill(cx + inner + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
            }
        }
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }
}
