package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.restor.fpv.Config;

public class FPVHudSettingsScreen extends Screen {
    private final Screen parent;

    public FPVHudSettingsScreen(Screen parent) {
        super(Component.translatable("screen.fpv.hud_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int y = 36;
        int w = 150;
        int h = 20;
        int gap = 22;

        // Hint list toggle first and full-width so it's the most prominent parameter.
        addToggle(left,       y, w + 160, h, "settings.fpv.hud_hints",  Config.HUD_HINTS);
        y += gap;
        addToggle(left,       y, w, h, "settings.fpv.hud_telemetry",    Config.HUD_TELEMETRY);
        addToggle(left + 160, y, w, h, "settings.fpv.hud_sticks",       Config.HUD_STICKS);
        y += gap;
        addToggle(left,       y, w, h, "settings.fpv.hud_roll_line",    Config.HUD_ROLL_LINE);
        addToggle(left + 160, y, w, h, "settings.fpv.hud_pitch_ladder", Config.HUD_PITCH_LADDER);
        y += gap;
        addToggle(left,       y, w, h, "settings.fpv.hud_throttle_bar", Config.HUD_THROTTLE_BAR);
        addToggle(left + 160, y, w, h, "settings.fpv.hud_crosshair",    Config.HUD_CROSSHAIR);
        y += gap;
        addToggle(left,       y, w, h, "settings.fpv.hud_heading",      Config.HUD_HEADING);
        y += gap + 8;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 75, y, 150, 20).build());
    }

    private void addToggle(int x, int y, int w, int h, String label, ModConfigSpec.BooleanValue value) {
        addRenderableWidget(Button.builder(toggleMessage(label, value.get()), button -> {
            value.set(!value.get());
            Config.save();
            button.setMessage(toggleMessage(label, value.get()));
        }).bounds(x, y, w, h).build());
    }

    private Component toggleMessage(String label, boolean on) {
        return Component.translatable(label).append(": ")
            .append(Component.translatable(on ? "options.on" : "options.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }
}
