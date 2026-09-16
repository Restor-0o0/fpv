package org.restor.fpv.client.ui.screen.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

abstract class SettingRow {
    static final int PLAIN_HEIGHT = 24;
    static final int HINT_HEIGHT = 34;

    protected final Component label;
    protected final Component hint;

    SettingRow(Component label, Component hint) {
        this.label = label;
        this.hint = hint;
    }

    int height() {
        return hint == null ? PLAIN_HEIGHT : HINT_HEIGHT;
    }

    boolean hoverable() {
        return true;
    }

    static int controlWidth(int rowWidth) {
        return Math.max(96, Math.min(170, rowWidth * 44 / 100));
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
        int textWidth = w - controlWidth(w) - 18;
        if (hint == null) {
            drawClipped(g, font, label, x + 8, y + (height() - 8) / 2, textWidth, SettingsUi.TEXT);
        } else {
            drawClipped(g, font, label, x + 8, y + 7, textWidth, SettingsUi.TEXT);
            drawClipped(g, font, hint, x + 8, y + 19, textWidth, SettingsUi.MUTED);
        }
        renderControl(g, font, x + w - controlWidth(w) - 8, y, controlWidth(w), mouseX, mouseY, delta);
    }

    abstract void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta);

    boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
        return false;
    }

    boolean mouseDragged(double mx, double my, int x, int w) {
        return false;
    }

    void mouseReleased() {}

    boolean keyPressed(int keyCode, int scanCode) {
        return false;
    }

    boolean listenMouse(int button) {
        return false;
    }

    void stopListening() {}

    static void drawClipped(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, maxWidth - font.width("…")));
        if (lines.isEmpty()) return;
        g.drawString(font, lines.get(0), x, y, color, false);
        g.drawString(font, "…", x + font.width(lines.get(0)), y, color, false);
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
    }

    static final class Section extends SettingRow {
        Section(Component label, Component hint) {
            super(label, hint);
        }

        @Override
        int height() {
            return hint == null ? 26 : 36;
        }

        @Override
        boolean hoverable() {
            return false;
        }

        @Override
        void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            int top = y + 12;
            g.drawString(font, label.copy().withStyle(s -> s.withBold(true)), x + 2, top, SettingsUi.ACCENT, false);
            int lineX = x + 8 + font.width(label.copy().withStyle(s -> s.withBold(true)));
            g.fill(lineX, top + 4, x + w, top + 5, SettingsUi.WINDOW_EDGE);
            if (hint != null) drawClipped(g, font, hint, x + 2, top + 12, w - 4, SettingsUi.FAINT);
        }

        @Override
        void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {}
    }

    static final class Toggle extends SettingRow {
        private final java.util.function.BooleanSupplier getter;
        private final java.util.function.Consumer<Boolean> setter;
        private float anim;

        Toggle(Component label, Component hint, java.util.function.BooleanSupplier getter,
               java.util.function.Consumer<Boolean> setter) {
            super(label, hint);
            this.getter = getter;
            this.setter = setter;
            this.anim = getter.getAsBoolean() ? 1f : 0f;
        }

        @Override
        void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            boolean on = getter.getAsBoolean();
            anim += ((on ? 1f : 0f) - anim) * Math.min(1f, delta * 0.5f);
            int pw = 24, ph = 12;
            int px = x + w - pw;
            int py = y + (height() - ph) / 2;
            SettingsUi.pill(g, px, py, pw, ph, SettingsUi.blend(SettingsUi.TRACK, SettingsUi.ACCENT, anim));
            int kx = px + 2 + Math.round((pw - 12) * anim);
            SettingsUi.pill(g, kx, py + 2, 8, 8, SettingsUi.KNOB);
            Component state = Component.translatable(on ? "options.on" : "options.off");
            g.drawString(font, state, px - 6 - font.width(state), py + 2, on ? SettingsUi.TEXT : SettingsUi.FAINT, false);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
            if (button != 0) return false;
            setter.accept(!getter.getAsBoolean());
            click();
            return true;
        }
    }

    static final class Slider extends SettingRow {
        interface Format {
            String apply(double value);
        }

        private final double min, max, step;
        private final java.util.function.DoubleSupplier getter;
        private final java.util.function.DoubleConsumer setter;
        private final Format format;
        private boolean dragging;

        Slider(Component label, Component hint, double min, double max, double step,
               java.util.function.DoubleSupplier getter, java.util.function.DoubleConsumer setter, Format format) {
            super(label, hint);
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.format = format;
        }

        private static final int VALUE_WIDTH = 42;

        @Override
        void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            int trackX = x + 4;
            int trackW = w - VALUE_WIDTH - 12;
            int cy = y + height() / 2;
            double t = Math.max(0, Math.min(1, (getter.getAsDouble() - min) / (max - min)));
            int fillW = (int) Math.round(trackW * t);
            SettingsUi.rect(g, trackX, cy - 2, trackW, 4, SettingsUi.TRACK);
            SettingsUi.rect(g, trackX, cy - 2, Math.max(2, fillW), 4, SettingsUi.ACCENT);
            boolean hot = dragging || SettingsUi.inside(mouseX, mouseY, trackX - 4, y, trackW + 8, height());
            int knob = hot ? 10 : 8;
            SettingsUi.pill(g, trackX + fillW - knob / 2, cy - knob / 2, knob, knob, SettingsUi.KNOB);
            String value = format.apply(getter.getAsDouble());
            int vx = x + w - VALUE_WIDTH;
            SettingsUi.rect(g, vx, cy - 7, VALUE_WIDTH, 14, SettingsUi.CHIP);
            g.drawString(font, value, vx + (VALUE_WIDTH - font.width(value)) / 2, cy - 4,
                dragging ? SettingsUi.ACCENT : SettingsUi.TEXT, false);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
            if (button != 0) return false;
            int cw = controlWidth(w);
            int trackX = x + w - cw - 8 + 4;
            int trackW = cw - VALUE_WIDTH - 12;
            if (mx < trackX - 6 || mx > trackX + trackW + 6) return false;
            dragging = true;
            apply(mx, trackX, trackW);
            return true;
        }

        @Override
        boolean mouseDragged(double mx, double my, int x, int w) {
            if (!dragging) return false;
            int cw = controlWidth(w);
            apply(mx, x + w - cw - 8 + 4, cw - VALUE_WIDTH - 12);
            return true;
        }

        @Override
        void mouseReleased() {
            dragging = false;
        }

        private void apply(double mx, int trackX, int trackW) {
            double t = Math.max(0, Math.min(1, (mx - trackX) / trackW));
            double v = min + t * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (Math.abs(v - getter.getAsDouble()) > 1e-9) setter.accept(v);
        }
    }

    static final class Action extends SettingRow {
        private final Component button;
        private final Runnable action;
        private final boolean danger;

        Action(Component label, Component hint, Component button, boolean danger, Runnable action) {
            super(label, hint);
            this.button = button;
            this.action = action;
            this.danger = danger;
        }

        @Override
        void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            int bw = Math.min(w, font.width(button) + 20);
            int bx = x + w - bw;
            int by = y + (height() - 16) / 2;
            boolean hot = SettingsUi.inside(mouseX, mouseY, bx, by, bw, 16);
            SettingsUi.rect(g, bx, by, bw, 16, hot ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);
            SettingsUi.outline(g, bx, by, bw, 16, danger ? 0x66FF5F5F : SettingsUi.WINDOW_EDGE);
            g.drawString(font, button, bx + (bw - font.width(button)) / 2, by + 4,
                danger ? SettingsUi.DANGER : SettingsUi.TEXT, false);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
            if (button != 0) return false;
            int cw = controlWidth(w);
            int bw = Math.min(cw, Minecraft.getInstance().font.width(this.button) + 20);
            if (mx < x + w - 8 - bw || mx > x + w - 8) return false;
            action.run();
            click();
            return true;
        }
    }
}
