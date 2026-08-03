package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.client.DevDroneBuild;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.network.DevDroneGivePacket;
import org.restor.fpv.network.ModNetworking;

/**
 * Dev tool: assemble a drone from modules and see its derived flight stats live, then "Give"
 * it (a modular_drone item carrying the assembly) to fly. Everything is computed by
 * {@link DroneAssembly} — the same path the real modular drone uses — so what you read here is
 * what you fly.
 */
public class FPVDevDroneScreen extends Screen {
    private static final float G = 9.81f;
    private static final float THROTTLE_EXP = 2.5f; // must match DroneEntity.THROTTLE_EXP

    private final Screen parent;
    private int rowsTop;
    private int rowH = 24;

    public FPVDevDroneScreen(Screen parent) {
        super(Component.translatable("screen.fpv.dev_drone"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        rowsTop = 44;
        int nameW = 150;
        int arrow = 20;
        // One row per slot: ◀ [name drawn in render] ▶
        for (int slot = 0; slot < DevDroneBuild.SLOTS; slot++) {
            int y = rowsTop + slot * rowH;
            int leftX = cx - nameW / 2 - arrow - 2;
            int rightX = cx + nameW / 2 + 2;
            final int s = slot;
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> DevDroneBuild.cycle(s, -1))
                .bounds(leftX, y, arrow, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> DevDroneBuild.cycle(s, 1))
                .bounds(rightX, y, arrow, 20).build());
        }

        // Preset templates.
        int py = rowsTop + DevDroneBuild.SLOTS * rowH + 6;
        int bw = 70, gap = 4;
        int startX = cx - (bw * 4 + gap * 3) / 2;
        addRenderableWidget(Button.builder(Component.literal("Tiny"), b -> DevDroneBuild.applyPreset("tiny"))
            .bounds(startX, py, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cinewhoop"), b -> DevDroneBuild.applyPreset("cine"))
            .bounds(startX + (bw + gap), py, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("5\" Race"), b -> DevDroneBuild.applyPreset("racing"))
            .bounds(startX + (bw + gap) * 2, py, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("7\""), b -> DevDroneBuild.applyPreset("seven"))
            .bounds(startX + (bw + gap) * 3, py, bw, 20).build());

        // Give + back.
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.dev_drone.give"),
                b -> ModNetworking.CHANNEL.sendToServer(new DevDroneGivePacket(DevDroneBuild.buildAssembly().save())))
            .bounds(cx - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(cx + 4, height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, 18, 0xFFFFFF);

        // Slot names between the arrows.
        for (int slot = 0; slot < DevDroneBuild.SLOTS; slot++) {
            int y = rowsTop + slot * rowH + 6;
            g.drawString(font, DevDroneBuild.slotLabel(slot), cx - 150, y, 0xA0A0A0);
            String name = DevDroneBuild.currentName(slot);
            g.drawCenteredString(font, name, cx, y, 0xFFFFFF);
        }

        // ---- live derived stats ----
        DroneAssembly a = DevDroneBuild.buildAssembly();
        DroneData d = a.buildDroneData();
        float mass = d.mass;
        float thrust = d.motorPower;                 // Newtons
        float twr = thrust / (mass * G);
        float hoverFrac = G / Math.max(0.01f, d.maxThrottle * (thrust / mass));
        String hover = hoverFrac >= 1f ? "не взлетит"
            : String.format("%.0f%%", Math.pow(hoverFrac, 1f / THROTTLE_EXP) * 100f);
        // v_max ≈ sqrt(maxThrottle*thrust / (k*ρ*CdA)), k≈3 (mixed axial/planar), ρ=1.225
        double vmax = Math.sqrt(d.maxThrottle * thrust / (3.0 * 1.225 * d.dragCoefficient));
        float[] trim = a.computeTrim();
        boolean imbalanced = Math.abs(trim[0]) > 1 || Math.abs(trim[1]) > 1 || Math.abs(trim[2]) > 1;

        int sy = rowsTop + DevDroneBuild.SLOTS * rowH + 34;
        int col = 0xC8FFC8;
        line(g, cx, sy, String.format("Масса: %.0f г   Тяга: %.1f Н   TWR: %.1f", mass * 1000f, thrust, twr), col);
        line(g, cx, sy + 12, String.format("Зависание: %s газа   Макс. скорость: ~%.0f км/ч", hover, vmax * 3.6), col);
        line(g, cx, sy + 24, String.format("Rates: R%.0f P%.0f Y%.0f °/s   Отклик: %.1f",
            d.maxRollRate, d.maxPitchRate, d.maxYawRate, d.rotationResponsiveness), col);
        line(g, cx, sy + 36, String.format("Сопротивление CdA: %.3f   Наклон камеры: %.0f°", d.dragCoefficient, d.cameraAngle), col);
        if (twr < 1.05f) line(g, cx, sy + 50, "⚠ TWR < 1 — тяги не хватает, дрон не поднимется", 0xFFFF5555);
        else if (imbalanced) line(g, cx, sy + 50, "⚠ Несбалансированные моторы/пропы — перекос в полёте", 0xFFFFC040);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics g, int cx, int y, String s, int color) {
        g.drawCenteredString(font, s, cx, y, color);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
