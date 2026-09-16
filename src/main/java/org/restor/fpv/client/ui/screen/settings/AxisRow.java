package org.restor.fpv.client.ui.screen.settings;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;
import org.restor.fpv.Config;
import org.restor.fpv.controller.RCController;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

final class AxisRow extends SettingRow {
    private static final int CHIP_WIDTH = 46;
    private static final int INVERT_WIDTH = 28;

    private final ModConfigSpec.IntValue axis;
    private final ModConfigSpec.BooleanValue invert;
    private final Consumer<SettingRow> listenHost;
    private boolean listening;
    private float[] baseline;

    AxisRow(Component label, ModConfigSpec.IntValue axis, ModConfigSpec.BooleanValue invert, Consumer<SettingRow> listenHost) {
        super(label, null);
        this.axis = axis;
        this.invert = invert;
        this.listenHost = listenHost;
    }

    static float[] liveAxes() {
        if (!RCController.isConnected()) return null;
        int joystick = RCController.getCurrentJoystick();
        if (joystick < 0) return null;
        FloatBuffer buffer = GLFW.glfwGetJoystickAxes(joystick);
        if (buffer == null) return null;
        float[] values = new float[buffer.remaining()];
        buffer.get(values);
        return values;
    }

    @Override
    void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
        float[] axes = liveAxes();
        if (listening) detect(axes);

        int by = y + (height() - 16) / 2;
        boolean chipHot = SettingsUi.inside(mouseX, mouseY, x, by, CHIP_WIDTH, 16);
        SettingsUi.rect(g, x, by, CHIP_WIDTH, 16, chipHot || listening ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);
        String text;
        int color;
        if (listening) {
            boolean pulse = (Util.getMillis() / 400) % 2 == 0;
            SettingsUi.outline(g, x, by, CHIP_WIDTH, 16, pulse ? SettingsUi.ACCENT : 0x88FFB733);
            text = "· · ·";
            color = SettingsUi.ACCENT;
        } else {
            SettingsUi.outline(g, x, by, CHIP_WIDTH, 16, SettingsUi.WINDOW_EDGE);
            int current = axis.get();
            text = current < 0 ? Component.translatable("settings.fpv.axis_off").getString() : "AX " + current;
            color = current < 0 ? SettingsUi.FAINT : SettingsUi.TEXT;
        }
        g.drawString(font, text, x + (CHIP_WIDTH - font.width(text)) / 2, by + 4, color, false);

        int barX = x + CHIP_WIDTH + 6;
        int barW = w - CHIP_WIDTH - INVERT_WIDTH - 12;
        int barY = by + 6;
        SettingsUi.rect(g, barX, barY, barW, 4, SettingsUi.TRACK);
        g.fill(barX + barW / 2, barY - 2, barX + barW / 2 + 1, barY + 6, SettingsUi.FAINT);
        int index = axis.get();
        if (axes != null && index >= 0 && index < axes.length) {
            float v = Math.max(-1f, Math.min(1f, axes[index]));
            if (invert.get()) v = -v;
            int mid = barX + barW / 2;
            int end = mid + Math.round(v * (barW / 2f));
            g.fill(Math.min(mid, end), barY, Math.max(mid, end) + 1, barY + 4, SettingsUi.ACCENT);
        }

        int ix = x + w - INVERT_WIDTH;
        boolean inv = invert.get();
        boolean invHot = SettingsUi.inside(mouseX, mouseY, ix, by, INVERT_WIDTH, 16);
        SettingsUi.rect(g, ix, by, INVERT_WIDTH, 16, inv ? 0x44FFB733 : invHot ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);
        SettingsUi.outline(g, ix, by, INVERT_WIDTH, 16, inv ? SettingsUi.ACCENT : SettingsUi.WINDOW_EDGE);
        String invText = "INV";
        g.drawString(font, invText, ix + (INVERT_WIDTH - font.width(invText)) / 2, by + 4,
            inv ? SettingsUi.ACCENT : SettingsUi.MUTED, false);
    }

    private void detect(float[] axes) {
        if (axes == null) return;
        if (baseline == null || baseline.length != axes.length) {
            baseline = axes.clone();
            return;
        }
        int best = -1;
        float bestDelta = 0.45f;
        for (int i = 0; i < axes.length; i++) {
            float d = Math.abs(axes[i] - baseline[i]);
            if (d > bestDelta) {
                bestDelta = d;
                best = i;
            }
        }
        if (best >= 0) {
            axis.set(best);
            Config.save();
            click();
            stopListening();
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
        int cw = controlWidth(w);
        int cx = x + w - cw - 8;
        if (mx >= cx + cw - INVERT_WIDTH && mx <= cx + cw && button == 0) {
            invert.set(!invert.get());
            Config.save();
            click();
            return true;
        }
        if (mx < cx || mx > cx + CHIP_WIDTH) return false;
        if (button == 1) {
            axis.set(-1);
            Config.save();
            stopListening();
            return true;
        }
        if (button != 0) return false;
        if (listening) {
            stopListening();
        } else {
            listening = true;
            baseline = liveAxes();
            listenHost.accept(this);
            click();
        }
        return true;
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode) {
        if (!listening) return false;
        stopListening();
        return true;
    }

    @Override
    boolean listenMouse(int button) {
        return false;
    }

    @Override
    void stopListening() {
        if (!listening) return;
        listening = false;
        baseline = null;
        listenHost.accept(null);
    }
}
