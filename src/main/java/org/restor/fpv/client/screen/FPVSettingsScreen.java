package org.restor.fpv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.restor.fpv.compat.PacketDistributor;
import org.restor.fpv.Config;
import org.restor.fpv.client.ModKeyBindings;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.network.DroneRatesPacket;

public class FPVSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 28;
    private static final int CONTENT_TOP = 66;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    private final Screen parent;
    private Tab selectedTab = Tab.CONTROLS;
    private int scrollOffset;
    private int maxScroll;

    public FPVSettingsScreen(Screen parent) {
        super(Component.translatable("screen.fpv.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentBottom = contentBottom();
        int tabWidth = Math.max(58, Math.min(96, (width - 32) / 3));
        int tabY = 36;
        int tabLeft = width / 2 - (tabWidth * 3 + 8) / 2;

        addTabButton(Tab.CONTROLS, tabLeft, tabY, tabWidth);
        addTabButton(Tab.HUD, tabLeft + tabWidth + 4, tabY, tabWidth);
        addTabButton(Tab.DEV, tabLeft + (tabWidth + 4) * 2, tabY, tabWidth);

        int[] layout = contentLayout();
        int left = layout[0];
        int w = layout[1];
        int colGap = layout[2];
        boolean twoColumns = layout[3] == 1;
        int y = CONTENT_TOP - scrollOffset;

        if (selectedTab == Tab.CONTROLS) {
            y = addControls(left, y, w, colGap, twoColumns);
        } else if (selectedTab == Tab.HUD) {
            y = addHud(left, y, w, colGap, twoColumns);
        } else {
            y = addDev(left, y, w, colGap, twoColumns);
        }

        maxScroll = Math.max(0, y + scrollOffset - CONTENT_TOP - (contentBottom - CONTENT_TOP));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            refreshWidgets();
            return;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 75, height - 26, 150, ROW_HEIGHT).build());
    }

    private int addControls(int left, int y, int w, int colGap, boolean twoColumns) {
        int right = twoColumns ? left + w + colGap : left;
        DroneEntity drone = Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.getVehicle() instanceof DroneEntity d ? d : null;

        if (drone != null) {
            addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_pitch_rate",
                drone.getRatePitch(), 0.1, 3.0, v -> setDroneRate(drone, 0, (float) v)));
            if (twoColumns) {
                addContentWidget(new DoubleSlider(right, y, w, ROW_HEIGHT, "settings.fpv.rc_roll_rate",
                drone.getRateRoll(), 0.1, 3.0, v -> setDroneRate(drone, 1, (float) v)));
            } else {
                y += ROW_GAP;
                addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_roll_rate",
                    drone.getRateRoll(), 0.1, 3.0, v -> setDroneRate(drone, 1, (float) v)));
            }
            y += ROW_GAP;
            addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_yaw_rate",
                drone.getRateYaw(), 0.1, 3.0, v -> setDroneRate(drone, 2, (float) v)));
        } else {
            addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_pitch_rate",
                Config.RC_PITCH_RATE, 0.1, 3.0));
            if (twoColumns) {
                addContentWidget(new DoubleSlider(right, y, w, ROW_HEIGHT, "settings.fpv.rc_roll_rate",
                Config.RC_ROLL_RATE, 0.1, 3.0));
            } else {
                y += ROW_GAP;
                addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_roll_rate",
                    Config.RC_ROLL_RATE, 0.1, 3.0));
            }
            y += ROW_GAP;
            addContentWidget(new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_yaw_rate",
                Config.RC_YAW_RATE, 0.1, 3.0));
        }
        addContentWidget(new DoubleSlider(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.rc_expo",
            Config.RC_EXPO, 0.0, 0.8));
        y += twoColumns ? ROW_GAP : ROW_GAP * 2;

        if (drone != null) {
            DroneEntity d2 = drone;
            addContentWidget(Button.builder(Component.translatable("settings.fpv.save_default"), b -> {
                Config.RC_PITCH_RATE.set((double) d2.getRatePitch());
                Config.RC_ROLL_RATE.set((double) d2.getRateRoll());
                Config.RC_YAW_RATE.set((double) d2.getRateYaw());
                Config.save();
            }).bounds(left, y, twoColumns ? w * 2 + colGap : w, ROW_HEIGHT).build());
            y += ROW_GAP;
        }

        y = addPair(left, y, w, colGap, twoColumns,
            axisButton(left, y, w, ROW_HEIGHT, "settings.fpv.axis_throttle", Config.RC_AXIS_THROTTLE),
            invertButton(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.invert_throttle", Config.RC_INVERT_THROTTLE));
        y = addPair(left, y, w, colGap, twoColumns,
            axisButton(left, y, w, ROW_HEIGHT, "settings.fpv.axis_yaw", Config.RC_AXIS_YAW),
            invertButton(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.invert_yaw", Config.RC_INVERT_YAW));
        y = addPair(left, y, w, colGap, twoColumns,
            axisButton(left, y, w, ROW_HEIGHT, "settings.fpv.axis_pitch", Config.RC_AXIS_PITCH),
            invertButton(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.invert_pitch", Config.RC_INVERT_PITCH));
        y = addPair(left, y, w, colGap, twoColumns,
            axisButton(left, y, w, ROW_HEIGHT, "settings.fpv.axis_roll", Config.RC_AXIS_ROLL),
            invertButton(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.invert_roll", Config.RC_INVERT_ROLL));
        y = addPair(left, y, w, colGap, twoColumns,
            axisButton(left, y, w, ROW_HEIGHT, "settings.fpv.axis_arm", Config.RC_AXIS_ARM),
            invertButton(twoColumns ? right : left, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, "settings.fpv.invert_arm", Config.RC_INVERT_ARM));
        y += 6;

        y = addPair(left, y, w, colGap, twoColumns,
            new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.rc_deadband",
            Config.RC_DEADBAND, 0.0, 0.25));
        addContentWidget(new DoubleSlider(twoColumns ? right : left, twoColumns ? y - ROW_GAP : y, w, ROW_HEIGHT, "settings.fpv.mouse_deadband",
                Config.MOUSE_DEADBAND, 0.0, 0.2));

        y = addPair(left, y, w, colGap, twoColumns,
            new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.mouse_pitch",
            Config.MOUSE_PITCH_SENSITIVITY, 0.005, 0.2));
        addContentWidget(new DoubleSlider(twoColumns ? right : left, twoColumns ? y - ROW_GAP : y, w, ROW_HEIGHT, "settings.fpv.mouse_roll",
                Config.MOUSE_ROLL_SENSITIVITY, 0.005, 0.2));

        y = addPair(left, y, w, colGap, twoColumns,
            new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.mouse_smoothing",
            Config.MOUSE_SMOOTHING, 0.0, 0.85));
        addContentWidget(new DoubleSlider(twoColumns ? right : left, twoColumns ? y - ROW_GAP : y, w, ROW_HEIGHT, "settings.fpv.keyboard_yaw",
                Config.KEYBOARD_YAW_SENSITIVITY, 0.05, 1.0));

        y = addPair(left, y, w, colGap, twoColumns,
            new DoubleSlider(left, y, w, ROW_HEIGHT, "settings.fpv.mouse_return",
            Config.MOUSE_RETURN, 0.0, 1.0));
        return y;
    }

    private int addHud(int left, int y, int w, int colGap, boolean twoColumns) {
        int fullWidth = twoColumns ? w * 2 + colGap : w;
        addContentWidget(toggleButton(left, y, fullWidth, ROW_HEIGHT, "settings.fpv.hud_hints", Config.HUD_HINTS));
        y += ROW_GAP;
        y = addTogglePair(left, y, w, colGap, twoColumns, "settings.fpv.hud_telemetry", Config.HUD_TELEMETRY, "settings.fpv.hud_sticks", Config.HUD_STICKS);
        y = addTogglePair(left, y, w, colGap, twoColumns, "settings.fpv.hud_roll_line", Config.HUD_ROLL_LINE, "settings.fpv.hud_pitch_ladder", Config.HUD_PITCH_LADDER);
        y = addTogglePair(left, y, w, colGap, twoColumns, "settings.fpv.hud_throttle_bar", Config.HUD_THROTTLE_BAR, "settings.fpv.hud_crosshair", Config.HUD_CROSSHAIR);
        y = addTogglePair(left, y, w, colGap, twoColumns, "settings.fpv.hud_heading", Config.HUD_HEADING, "settings.fpv.hud_damage", Config.HUD_DAMAGE);
        return y;
    }

    private int addDev(int left, int y, int w, int colGap, boolean twoColumns) {
        addContentWidget(Button.builder(Component.translatable("settings.fpv.dev_drone_open"),
                b -> minecraft.setScreen(new FPVDevDroneScreen(this)))
            .bounds(left, y, twoColumns ? w * 2 + colGap : w, ROW_HEIGHT).build());
        y += ROW_GAP;
        y = addTogglePair(left, y, w, colGap, twoColumns,
            "settings.fpv.realistic_battery", Config.REALISTIC_BATTERY,
            "settings.fpv.realistic_radio", Config.REALISTIC_RADIO);
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_power", Config.DEV_POWER_MULT.get(), 0.2, 5.0, "%.2fx", v -> { Config.DEV_POWER_MULT.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_drag", Config.DEV_DRAG_MULT.get(), 0.1, 6.0, "%.2fx", v -> { Config.DEV_DRAG_MULT.set(v); Config.save(); }));
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_maxspeed", Config.DEV_MAX_SPEED.get(), 10.0, 150.0, "%.0f m/s", v -> { Config.DEV_MAX_SPEED.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_explosion", Config.DEV_EXPLOSION_POWER.get(), 1.0, 100.0, "%.1f", v -> { Config.DEV_EXPLOSION_POWER.set(v); Config.save(); }));
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_explosion_break", Config.DEV_EXPLOSION_BLOCK_BREAK_PERCENT.get(), 0.0, 100.0, "%.0f%%", v -> { Config.DEV_EXPLOSION_BLOCK_BREAK_PERCENT.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_explosion_fling", Config.DEV_EXPLOSION_BLOCK_FLING_PERCENT.get(), 0.0, 200.0, "%.0f%%", v -> { Config.DEV_EXPLOSION_BLOCK_FLING_PERCENT.set(v); Config.save(); }));
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_explosion_damage", Config.DEV_EXPLOSION_MOB_DAMAGE_MULT.get(), 0.0, 5.0, "%.2fx", v -> { Config.DEV_EXPLOSION_MOB_DAMAGE_MULT.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_explosion_cam", Config.DEV_EXPLOSION_CAMERA_BACKSTEP.get(), 0.0, 40.0, "%.0f m", v -> { Config.DEV_EXPLOSION_CAMERA_BACKSTEP.set(v); Config.save(); }));
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_explosion_cam_time", Config.DEV_EXPLOSION_CAMERA_SECONDS.get(), 0.0, 6.0, "%.1f s", v -> { Config.DEV_EXPLOSION_CAMERA_SECONDS.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_chunks", Config.DEV_CHUNK_RADIUS.get(), 0, 12, "%.0f", v -> { Config.DEV_CHUNK_RADIUS.set((int) Math.round(v)); Config.save(); }));
        y = addDevPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_hitbox", Config.DEV_HITBOX_SCALE.get(), 0.2, 3.0, "%.2fx", v -> { Config.DEV_HITBOX_SCALE.set(v); Config.save(); }),
            new DevSlider(0, 0, w, ROW_HEIGHT, "settings.fpv.dev_hitbox_y", Config.DEV_HITBOX_Y_OFFSET.get(), -1.0, 1.0, "%.2f", v -> { Config.DEV_HITBOX_Y_OFFSET.set(v); Config.save(); }));
        y = addPair(left, y, w, colGap, twoColumns,
            new DevSlider(left, y, w, ROW_HEIGHT, "settings.fpv.dev_cam_height", Config.FPV_CAMERA_HEIGHT.get(), 0.0, 1.5, "%.2f", v -> { Config.FPV_CAMERA_HEIGHT.set(v); Config.save(); }));
        return y + ROW_GAP;
    }

    private int addPair(int left, int y, int w, int colGap, boolean twoColumns, AbstractWidget first) {
        addContentWidget(first);
        return y + ROW_GAP;
    }

    private int addPair(int left, int y, int w, int colGap, boolean twoColumns, AbstractWidget first, AbstractWidget second) {
        addContentWidget(first);
        addContentWidget(second);
        return y + (twoColumns ? ROW_GAP : ROW_GAP * 2);
    }

    private int addTogglePair(int left, int y, int w, int colGap, boolean twoColumns, String labelA, ForgeConfigSpec.BooleanValue valueA, String labelB, ForgeConfigSpec.BooleanValue valueB) {
        int right = twoColumns ? left + w + colGap : left;
        addContentWidget(toggleButton(left, y, w, ROW_HEIGHT, labelA, valueA));
        addContentWidget(toggleButton(right, twoColumns ? y : y + ROW_GAP, w, ROW_HEIGHT, labelB, valueB));
        return y + (twoColumns ? ROW_GAP : ROW_GAP * 2);
    }

    private int addDevPair(int left, int y, int w, int colGap, boolean twoColumns, AbstractWidget first, AbstractWidget second) {
        int right = twoColumns ? left + w + colGap : left;
        first.setX(left);
        first.setY(y);
        second.setX(right);
        second.setY(twoColumns ? y : y + ROW_GAP);
        addContentWidget(first);
        addContentWidget(second);
        return y + (twoColumns ? ROW_GAP : ROW_GAP * 2);
    }

    private void addTabButton(Tab tab, int x, int y, int w) {
        Component label = Component.translatable(tab.labelKey);
        if (tab == selectedTab) {
            label = Component.literal("[ ").append(label).append(" ]");
        }
        addRenderableWidget(Button.builder(label, b -> {
            selectedTab = tab;
            scrollOffset = 0;
            refreshWidgets();
        }).bounds(x, y, w, ROW_HEIGHT).build());
    }

    private Button toggleButton(int x, int y, int w, int h, String label, ForgeConfigSpec.BooleanValue value) {
        return Button.builder(toggleMessage(label, value.get()), button -> {
            value.set(!value.get());
            Config.save();
            button.setMessage(toggleMessage(label, value.get()));
        }).bounds(x, y, w, h).build();
    }

    private Component toggleMessage(String label, boolean on) {
        return Component.translatable(label).append(": ")
            .append(Component.translatable(on ? "options.on" : "options.off"));
    }

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        widget.visible = widget.getY() >= CONTENT_TOP && widget.getY() + widget.getHeight() <= contentBottom();
        widget.active = widget.visible;
        return addRenderableWidget(widget);
    }

    private int[] contentLayout() {
        int colGap = 22;
        int margin = 24;
        int available = width - margin * 2 - 10;
        boolean twoColumns = available >= 330;
        int w = twoColumns ? Math.min(170, (available - colGap) / 2) : Math.min(260, available);
        int left = twoColumns ? width / 2 - (w * 2 + colGap) / 2 : width / 2 - w / 2;
        return new int[] { left, w, colGap, twoColumns ? 1 : 0 };
    }

    private int contentBottom() {
        return height - CONTENT_BOTTOM_MARGIN;
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private enum Tab {
        CONTROLS("settings.fpv.tab_controls"),
        HUD("settings.fpv.tab_hud"),
        DEV("settings.fpv.tab_dev");

        private final String labelKey;

        Tab(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    // Applies a rate change to the ridden drone: locally for instant slider read-back,
    // and to the server (which owns the value and re-syncs it).
    private static void setDroneRate(DroneEntity drone, int axis, float value) {
        drone.setRateMultiplier(axis, value);
        PacketDistributor.sendToServer(new DroneRatesPacket(drone.getId(), axis, value));
    }

    private Button axisButton(int x, int y, int w, int h, String label, ForgeConfigSpec.IntValue value) {
        return Button.builder(axisMessage(label, value.get()), button -> {
            value.set(value.get() >= 7 ? -1 : value.get() + 1);
            Config.save();
            button.setMessage(axisMessage(label, value.get()));
        }).bounds(x, y, w, h).build();
    }

    private Button invertButton(int x, int y, int w, int h, String label, ForgeConfigSpec.BooleanValue value) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollY);
        }
        int old = scrollOffset;
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 22));
        if (scrollOffset != old) {
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, selectedTab == Tab.DEV
            ? Component.translatable("screen.fpv.dev_hint")
            : Component.translatable("screen.fpv.settings_hint"), width / 2, 24, 0xAAAAAA);
        renderScrollbar(graphics);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.keys_hint",
                ModKeyBindings.OPEN_SETTINGS.getTranslatedKeyMessage(),
                ModKeyBindings.TOGGLE_HUD.getTranslatedKeyMessage()),
            width / 2, height - 14, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }
        int top = CONTENT_TOP;
        int bottom = contentBottom();
        int x = width - 12;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbY = top + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x, top, x + 4, bottom, 0x66000000);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xCCAAAAAA);
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
                     ForgeConfigSpec.DoubleValue config, double min, double max) {
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
