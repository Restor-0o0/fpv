package org.restor.fpv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.restor.fpv.Config;
import org.restor.fpv.client.ModKeyBindings;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.network.DroneRatesPacket;
import org.restor.fpv.network.ModNetworking;

public class FPVSettingsScreen extends Screen {
    private final Screen parent;

    public FPVSettingsScreen(Screen parent) {
        super(Component.translatable("screen.fpv.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int y = 32;
        int w = 150;
        int h = 20;
        int gap = 22;

        // --- RC Rate / чувствительность (самое важное — сверху) ---
        // While riding, the rate sliders edit THIS drone (synced entity data), not the
        // global config; the save-default button below copies them into the config.
        DroneEntity drone = Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.getVehicle() instanceof DroneEntity d ? d : null;

        if (drone != null) {
            addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.rc_pitch_rate",
                drone.getRatePitch(), 0.1, 3.0, v -> setDroneRate(drone, 0, (float) v)));
            addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.rc_roll_rate",
                drone.getRateRoll(), 0.1, 3.0, v -> setDroneRate(drone, 1, (float) v)));
            y += gap;
            addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.rc_yaw_rate",
                drone.getRateYaw(), 0.1, 3.0, v -> setDroneRate(drone, 2, (float) v)));
        } else {
            addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.rc_pitch_rate",
                Config.RC_PITCH_RATE, 0.1, 3.0));
            addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.rc_roll_rate",
                Config.RC_ROLL_RATE, 0.1, 3.0));
            y += gap;
            addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.rc_yaw_rate",
                Config.RC_YAW_RATE, 0.1, 3.0));
        }
        addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.rc_expo",
            Config.RC_EXPO, 0.0, 0.8));
        y += gap;

        if (drone != null) {
            DroneEntity d2 = drone;
            addRenderableWidget(Button.builder(Component.translatable("settings.fpv.save_default"), b -> {
                Config.RC_PITCH_RATE.set((double) d2.getRatePitch());
                Config.RC_ROLL_RATE.set((double) d2.getRateRoll());
                Config.RC_YAW_RATE.set((double) d2.getRateYaw());
                Config.save();
            }).bounds(left, y, w + 160, h).build());
            y += gap;
        }

        // --- Оси пульта ---
        addRenderableWidget(axisButton(left, y, w, h, "settings.fpv.axis_throttle", Config.RC_AXIS_THROTTLE));
        addRenderableWidget(invertButton(left + 160, y, w, h, "settings.fpv.invert_throttle", Config.RC_INVERT_THROTTLE));
        y += gap;

        addRenderableWidget(axisButton(left, y, w, h, "settings.fpv.axis_yaw", Config.RC_AXIS_YAW));
        addRenderableWidget(invertButton(left + 160, y, w, h, "settings.fpv.invert_yaw", Config.RC_INVERT_YAW));
        y += gap;

        addRenderableWidget(axisButton(left, y, w, h, "settings.fpv.axis_pitch", Config.RC_AXIS_PITCH));
        addRenderableWidget(invertButton(left + 160, y, w, h, "settings.fpv.invert_pitch", Config.RC_INVERT_PITCH));
        y += gap;

        addRenderableWidget(axisButton(left, y, w, h, "settings.fpv.axis_roll", Config.RC_AXIS_ROLL));
        addRenderableWidget(invertButton(left + 160, y, w, h, "settings.fpv.invert_roll", Config.RC_INVERT_ROLL));
        y += gap + 4;

        // --- Deadband ---
        addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.rc_deadband",
            Config.RC_DEADBAND, 0.0, 0.25));
        addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.mouse_deadband",
            Config.MOUSE_DEADBAND, 0.0, 0.2));
        y += gap;

        // --- Мышь ---
        addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.mouse_pitch",
            Config.MOUSE_PITCH_SENSITIVITY, 0.005, 0.2));
        addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.mouse_roll",
            Config.MOUSE_ROLL_SENSITIVITY, 0.005, 0.2));
        y += gap;

        addRenderableWidget(new DoubleSlider(left, y, w, h, "settings.fpv.mouse_smoothing",
            Config.MOUSE_SMOOTHING, 0.0, 0.85));
        addRenderableWidget(new DoubleSlider(left + 160, y, w, h, "settings.fpv.keyboard_yaw",
            Config.KEYBOARD_YAW_SENSITIVITY, 0.05, 1.0));
        y += gap + 4;

        addRenderableWidget(Button.builder(Component.translatable("settings.fpv.hud_button"),
                b -> minecraft.setScreen(new FPVHudSettingsScreen(this)))
            .bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("settings.fpv.dev_button"),
                b -> minecraft.setScreen(new FPVDevSettingsScreen(this)))
            .bounds(width / 2 + 5, y, 150, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 75, y, 150, 20).build());
    }

    // Applies a rate change to the ridden drone: locally for instant slider read-back,
    // and to the server (which owns the value and re-syncs it).
    private static void setDroneRate(DroneEntity drone, int axis, float value) {
        drone.setRateMultiplier(axis, value);
        ModNetworking.CHANNEL.sendToServer(new DroneRatesPacket(drone.getId(), axis, value));
    }

    private Button axisButton(int x, int y, int w, int h, String label, net.minecraftforge.common.ForgeConfigSpec.IntValue value) {
        return Button.builder(axisMessage(label, value.get()), button -> {
            value.set(value.get() >= 7 ? -1 : value.get() + 1);
            Config.save();
            button.setMessage(axisMessage(label, value.get()));
        }).bounds(x, y, w, h).build();
    }

    private Button invertButton(int x, int y, int w, int h, String label, net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        return Button.builder(invertMessage(label, value.get()), button -> {
            value.set(!value.get());
            Config.save();
            button.setMessage(invertMessage(label, value.get()));
        }).bounds(x, y, w, h).build();
    }

    private Component axisMessage(String label, int axis) {
        return Component.translatable(label).append(": ").append(Integer.toString(axis));
    }

    private Component invertMessage(String label, boolean inverted) {
        return Component.translatable(label).append(": ")
            .append(Component.translatable(inverted ? "options.on" : "options.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.settings_hint"), width / 2, 24, 0xAAAAAA);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.keys_hint",
                ModKeyBindings.OPEN_SETTINGS.getTranslatedKeyMessage(),
                ModKeyBindings.TOGGLE_HUD.getTranslatedKeyMessage()),
            width / 2, height - 14, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    private static class DoubleSlider extends AbstractSliderButton {
        private final String label;
        private final java.util.function.DoubleConsumer setter;
        private final double min;
        private final double max;

        DoubleSlider(int x, int y, int w, int h, String label,
                     double current, double min, double max, java.util.function.DoubleConsumer setter) {
            super(x, y, w, h, Component.empty(), toSlider(current, min, max));
            this.label = label;
            this.setter = setter;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        DoubleSlider(int x, int y, int w, int h, String label,
                     net.minecraftforge.common.ForgeConfigSpec.DoubleValue config, double min, double max) {
            this(x, y, w, h, label, config.get(), min, max, v -> {
                config.set(v);
                Config.save();
            });
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(label).append(": ").append(String.format("%.3f", currentValue())));
        }

        @Override
        protected void applyValue() {
            setter.accept(currentValue());
        }

        private double currentValue() {
            return min + value * (max - min);
        }

        private static double toSlider(double value, double min, double max) {
            return (value - min) / (max - min);
        }
    }
}
