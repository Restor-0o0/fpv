package org.restor.fpv.client.ui.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.restor.fpv.controller.RCController;
import org.restor.fpv.entity.DroneEntity;

final class HudWidgets {
    static final Minecraft MC = Minecraft.getInstance();
    static final int GREEN = 0xFF00FF00;
    static final int RED = 0xFFFF0000;
    static final int MARGIN = 10;

    private HudWidgets() {}

    static int lineHeight() {
        return MC.font.lineHeight + 2;
    }

    // Built from the live keybindings so labels localize and follow rebinds.
    // Returns the Y just below the block so telemetry can stack underneath.
    static int keyHints(GuiGraphicsExtractor graphics, int startY, KeyMapping[] keys, String... extra) {
        Font font = MC.font;
        int color = 0xC0FFFF55;
        int y = startY;
        for (KeyMapping k : keys) {
            String name = Component.translatable(k.getName()).getString();
            graphics.text(font, "[" + k.getTranslatedKeyMessage().getString() + "] " + name, MARGIN, y, color);
            y += lineHeight();
        }
        for (String line : extra) {
            graphics.text(font, line, MARGIN, y, color);
            y += lineHeight();
        }
        return y + 4;
    }

    static String bindHint(KeyMapping key, String labelKey) {
        return "[" + key.getTranslatedKeyMessage().getString() + "] "
            + Component.translatable(labelKey).getString();
    }

    static float[] attitude(Entity e, float prevRoll, float roll, float partialTick) {
        Quaternionf q = new Quaternionf(DroneEntity.attitudeQuat(e.yRotO, e.xRotO, prevRoll))
            .slerp(DroneEntity.attitudeQuat(e.getYRot(), e.getXRot(), roll), partialTick);
        return DroneEntity.decomposeAttitude(q);
    }

    static double altitude(Entity e) {
        int surfaceY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
            e.blockPosition().getX(), e.blockPosition().getZ());
        return Math.max(0, e.getY() - surfaceY);
    }

    static void controllerAndSpeed(GuiGraphicsExtractor graphics, int height, double speed) {
        String ctrl = RCController.isConnected() ? "TX: " + RCController.getControllerName() : "TX: NONE";
        graphics.text(MC.font, ctrl, MARGIN, height - MARGIN - lineHeight() * 2, GREEN);
        graphics.text(MC.font, String.format("SPD: %.1f m/s", speed), MARGIN, height - MARGIN - lineHeight(), GREEN);
    }

    static void heading(GuiGraphicsExtractor graphics, int width, int height, float yaw) {
        int hdg = Math.floorMod(Math.round(yaw) + 180, 360);
        String s = String.format("HDG %03d", hdg);
        graphics.text(MC.font, s, width / 2 - MC.font.width(s) / 2, (int) (height * 0.06f), GREEN);
    }

    static void throttleBar(GuiGraphicsExtractor graphics, int width, int height, float throttle) {
        Font font = MC.font;
        int barWidth = 6;
        int barHeight = (int) (height * 0.3);
        int barX = width - MARGIN - barWidth;
        int barY = height / 2 - barHeight / 2;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x88000000);
        int fillH = (int) (throttle * barHeight);
        graphics.fill(barX, barY + barHeight - fillH, barX + barWidth, barY + barHeight, throttle > 0.9f ? RED : GREEN);

        graphics.text(font, "THR", barX - 2, barY - lineHeight(), GREEN);
        String pct = String.format("%.0f%%", throttle * 100);
        graphics.text(font, pct, barX + barWidth - font.width(pct), barY + barHeight + 3, GREEN);
    }

    static void verticalSpeedBar(GuiGraphicsExtractor graphics, int width, int height, float throttle, float verticalSpeed) {
        Font font = MC.font;
        int barWidth = 6;
        int barHeight = (int) (height * 0.3);
        int barX = width - MARGIN - barWidth;
        int barY = height / 2 - barHeight / 2;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x88000000);
        int mid = barY + barHeight / 2;
        float cmd = throttle * 2f - 1f;
        int h = Math.round(Math.abs(cmd) * (barHeight / 2f));
        int top = cmd >= 0 ? mid - h : mid;
        graphics.fill(barX, top, barX + barWidth, top + Math.max(1, h), GREEN);
        graphics.fill(barX - 2, mid, barX + barWidth + 2, mid + 1, GREEN);

        graphics.text(font, "V/S", barX - 2, barY - lineHeight(), GREEN);
        String vs = String.format("%+.1f", verticalSpeed);
        graphics.text(font, vs, barX + barWidth - font.width(vs), barY + barHeight + 3, GREEN);
    }

    // Mode 2 layout: left = yaw (x) / throttle (y), right = roll (x) / pitch (y).
    static void stickBoxes(GuiGraphicsExtractor graphics, int width, int height,
                           float yaw, float throttle, float roll, float pitch) {
        int size = 34;
        int gap = 16;
        int boxY = height - size - 8;
        int cx = width / 2;
        stickBox(graphics, cx - size - gap / 2, boxY, size, yaw, throttle * 2f - 1f);
        stickBox(graphics, cx + gap / 2, boxY, size, roll, pitch);
    }

    private static void stickBox(GuiGraphicsExtractor graphics, int x, int y, int size, float sx, float sy) {
        int border = 0x88FFFFFF;
        graphics.fill(x, y, x + size, y + size, 0x55000000);
        graphics.horizontalLine(x, x + size - 1, y, border);
        graphics.horizontalLine(x, x + size - 1, y + size - 1, border);
        graphics.verticalLine(x, y, y + size - 1, border);
        graphics.verticalLine(x + size - 1, y, y + size - 1, border);
        graphics.horizontalLine(x + 2, x + size - 3, y + size / 2, 0x33FFFFFF);
        graphics.verticalLine(x + size / 2, y + 2, y + size - 3, 0x33FFFFFF);
        int half = size / 2 - 3;
        int px = x + size / 2 + Math.round(Mth.clamp(sx, -1f, 1f) * half);
        int py = y + size / 2 - Math.round(Mth.clamp(sy, -1f, 1f) * half);
        graphics.fill(px - 2, py - 2, px + 2, py + 2, GREEN);
    }

    // The camera itself is rolled by +roll, so the real horizon appears at -roll;
    // the dashes counter-rotate to stay on it.
    static void rollLine(GuiGraphicsExtractor graphics, int width, int height, float roll) {
        int cx = width / 2;
        int cy = height / 2;
        float rad = (float) Math.toRadians(-roll);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        int half = (int) (width * 0.10f);
        int centerGap = 12;
        for (int t = -half; t <= half; t += 6) {
            if (Math.abs(t) < centerGap) continue;
            int x = cx + Math.round(t * cos);
            int ly = cy + Math.round(t * sin);
            graphics.fill(x - 1, ly, x + 2, ly + 1, 0xCCFFFFFF);
        }
    }

    // Tick + label every 10°, window ±32°. Displayed value is -xRot so positive = nose up.
    static void pitchLadder(GuiGraphicsExtractor graphics, int width, int height, float pitch) {
        Font font = MC.font;
        int cx = width / 2;
        int cy = height / 2;
        int x = cx + (int) (width * 0.13f);
        float pxPerDeg = 1.4f;
        float disp = -pitch;
        for (int a = -90; a <= 90; a += 10) {
            int ty = cy - Math.round((a - disp) * pxPerDeg);
            if (Math.abs(ty - cy) > 45) continue;
            boolean zero = a == 0;
            graphics.horizontalLine(x, x + (zero ? 14 : 8), ty, zero ? 0xFFFFFFFF : 0x99FFFFFF);
            graphics.text(font, String.valueOf(a), x + 18, ty - 4, 0x99FFFFFF);
        }
        graphics.horizontalLine(x - 7, x - 3, cy, GREEN);
        graphics.fill(x - 3, cy - 1, x - 1, cy + 2, GREEN);
    }

    static void crosshair(GuiGraphicsExtractor graphics, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;
        int size = 8;
        int gap = 3;
        int color = 0xAAFFFFFF;
        graphics.verticalLine(cx, cy - gap - size, cy - gap, color);
        graphics.verticalLine(cx, cy + gap, cy + gap + size, color);
        graphics.horizontalLine(cx - gap - size, cx - gap, cy, color);
        graphics.horizontalLine(cx + gap, cx + gap + size, cy, color);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
    }
}
