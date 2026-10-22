package org.restor.fpv.client.ui.screen.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

final class KeyBindRow extends SettingRow {
    private static final int RESET_WIDTH = 16;

    private final KeyMapping key;
    private final List<KeyMapping> peers;
    private final Consumer<SettingRow> listenHost;
    private boolean listening;

    KeyBindRow(Component label, Component hint, KeyMapping key, List<KeyMapping> peers, Consumer<SettingRow> listenHost) {
        super(label, hint);
        this.key = key;
        this.peers = peers;
        this.listenHost = listenHost;
    }

    boolean conflicts() {
        if (key.isUnbound()) return false;
        for (KeyMapping other : peers) {
            if (other != key && !other.isUnbound() && other.getKey().equals(key.getKey())
                && other.getKeyModifier() == key.getKeyModifier()) {
                return true;
            }
        }
        return false;
    }

    @Override
    void renderControl(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
        int chipW = w - RESET_WIDTH - 4;
        int by = y + (height() - 16) / 2;
        boolean hot = SettingsUi.inside(mouseX, mouseY, x, by, chipW, 16);
        SettingsUi.rect(g, x, by, chipW, 16, hot || listening ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);

        boolean conflict = conflicts();
        Component text;
        int color;
        if (listening) {
            boolean pulse = (Util.getMillis() / 400) % 2 == 0;
            SettingsUi.outline(g, x, by, chipW, 16, pulse ? SettingsUi.ACCENT : 0x88FFB733);
            text = Component.literal("> ").append(key.getTranslatedKeyMessage()).append(" <");
            color = SettingsUi.ACCENT;
        } else {
            SettingsUi.outline(g, x, by, chipW, 16, conflict ? 0x99FF5F5F : SettingsUi.WINDOW_EDGE);
            text = key.isUnbound() ? Component.translatable("settings.fpv.key_unbound") : key.getTranslatedKeyMessage();
            color = key.isUnbound() ? SettingsUi.FAINT : conflict ? SettingsUi.DANGER : SettingsUi.TEXT;
        }
        int textW = font.width(text);
        if (textW > chipW - 8) {
            drawClipped(g, font, text, x + 4, by + 4, chipW - 8, color);
        } else {
            g.drawString(font, text, x + (chipW - textW) / 2, by + 4, color, false);
        }

        if (!key.isDefault()) {
            int rx = x + w - RESET_WIDTH;
            boolean rhot = SettingsUi.inside(mouseX, mouseY, rx, by, RESET_WIDTH, 16);
            SettingsUi.rect(g, rx, by, RESET_WIDTH, 16, rhot ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);
            g.drawString(font, "↺", rx + (RESET_WIDTH - font.width("↺")) / 2, by + 4,
                rhot ? SettingsUi.ACCENT : SettingsUi.MUTED, false);
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
        int cw = controlWidth(w);
        int cx = x + w - cw - 8;
        int chipW = cw - RESET_WIDTH - 4;
        if (button == 0 && !key.isDefault() && mx >= cx + cw - RESET_WIDTH && mx <= cx + cw) {
            key.setToDefault();
            apply();
            click();
            return true;
        }
        if (mx < cx || mx > cx + chipW) return false;
        if (button == 0) {
            listening = true;
            listenHost.accept(this);
            click();
            return true;
        }
        if (button == 1) {
            bind(KeyModifier.NONE, InputConstants.UNKNOWN);
            return true;
        }
        return false;
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode) {
        if (!listening) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            bind(KeyModifier.NONE, InputConstants.UNKNOWN);
        } else {
            InputConstants.Key pressed = InputConstants.getKey(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, 0));
            KeyModifier modifier = KeyModifier.isKeyCodeModifier(pressed) ? KeyModifier.NONE : activeModifier();
            bind(modifier, pressed);
        }
        stopListening();
        return true;
    }

    @Override
    boolean listenMouse(int button) {
        if (!listening) return false;
        bind(activeModifier(), InputConstants.Type.MOUSE.getOrCreate(button));
        stopListening();
        return true;
    }

    @Override
    void stopListening() {
        if (!listening) return;
        listening = false;
        listenHost.accept(null);
    }

    private static KeyModifier activeModifier() {
        return KeyModifier.getActiveModifiers().stream().findFirst().orElse(KeyModifier.NONE);
    }

    private void bind(KeyModifier modifier, InputConstants.Key input) {
        key.setKeyModifierAndCode(modifier, input);
        apply();
    }

    private void apply() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping.resetMapping();
        mc.options.save();
    }
}
