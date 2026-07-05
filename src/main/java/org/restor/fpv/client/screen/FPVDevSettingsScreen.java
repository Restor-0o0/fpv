package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.restor.fpv.Config;

// Dev tuning: weight, motor power, kamikaze explosion power, chunk-load radius.
public class FPVDevSettingsScreen extends Screen {
    private final Screen parent;

    public FPVDevSettingsScreen(Screen parent) {
        super(Component.translatable("screen.fpv.dev_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = 40;
        int w = 240;
        int gap = 26;

        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_mass",
            Config.DEV_MASS_MULT.get(), 0.2, 5.0, "%.2fx",
            v -> { Config.DEV_MASS_MULT.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_power",
            Config.DEV_POWER_MULT.get(), 0.2, 5.0, "%.2fx",
            v -> { Config.DEV_POWER_MULT.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_drag",
            Config.DEV_DRAG_MULT.get(), 0.1, 6.0, "%.2fx",
            v -> { Config.DEV_DRAG_MULT.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_maxspeed",
            Config.DEV_MAX_SPEED.get(), 10.0, 150.0, "%.0f m/s",
            v -> { Config.DEV_MAX_SPEED.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_explosion",
            Config.DEV_EXPLOSION_POWER.get(), 1.0, 100.0, "%.1f",
            v -> { Config.DEV_EXPLOSION_POWER.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_chunks",
            Config.DEV_CHUNK_RADIUS.get(), 0, 12, "%.0f",
            v -> { Config.DEV_CHUNK_RADIUS.set((int) Math.round(v)); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_hitbox",
            Config.DEV_HITBOX_SCALE.get(), 0.2, 3.0, "%.2fx",
            v -> { Config.DEV_HITBOX_SCALE.set(v); Config.save(); }));
        y += gap;
        addRenderableWidget(new DevSlider(cx - w / 2, y, w, 20, "settings.fpv.dev_hitbox_y",
            Config.DEV_HITBOX_Y_OFFSET.get(), -1.0, 1.0, "%.2f",
            v -> { Config.DEV_HITBOX_Y_OFFSET.set(v); Config.save(); }));
        y += gap + 6;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 75, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.dev_hint"), width / 2, 27, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    private static class DevSlider extends AbstractSliderButton {
        private final String label;
        private final String format;
        private final double min, max;
        private final java.util.function.DoubleConsumer setter;

        DevSlider(int x, int y, int w, int h, String label, double current, double min, double max,
                  String format, java.util.function.DoubleConsumer setter) {
            super(x, y, w, h, Component.empty(), (current - min) / (max - min));
            this.label = label;
            this.format = format;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(label).append(": ")
                .append(String.format(format, min + value * (max - min))));
        }

        @Override
        protected void applyValue() {
            setter.accept(min + value * (max - min));
        }
    }
}
