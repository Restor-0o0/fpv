package org.restor.fpv.client.ui.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.network.RaceGateConfigPacket;

import java.util.ArrayList;
import java.util.List;

// Race gate config: frame size, colour, and orientation (yaw / pitch angle).
public class RaceGateScreen extends Screen {
    private static final int PANEL_W = 216;
    private static final int PANEL_H = 252;

    private final BlockPos pos;
    private final List<StepperRow> rows = new ArrayList<>();
    private int size = 5;
    private int yaw = 0;
    private int pitch = 0;
    private int offset = 0;
    private int color = RaceGateBlockEntity.PALETTE[0];
    private int left;
    private int top;

    public RaceGateScreen(BlockPos pos) {
        super(Component.translatable("screen.fpv.race_gate"));
        this.pos = pos;
    }

    private RaceGateBlockEntity be() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof RaceGateBlockEntity b) return b;
        return null;
    }

    @Override
    protected void init() {
        RaceGateBlockEntity be = be();
        if (be != null) {
            size = be.getSize();
            yaw = be.getYaw();
            pitch = be.getPitch();
            offset = be.getOffset();
            color = be.getColor();
        }

        rows.clear();
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - PANEL_H) / 2);

        int cx = left + 12;
        int cw = PANEL_W - 24;
        int y = top + 46;

        addRow(cx, y, cw, "screen.fpv.race_size", () -> String.valueOf(size),
            () -> updateSize(size - 1), () -> updateSize(size + 1), "-", "+");
        y += 24;
        addRow(cx, y, cw, "screen.fpv.race_yaw", () -> yaw + "\u00B0",
            () -> updateYaw(-15), () -> updateYaw(15), "<", ">");
        y += 24;
        addRow(cx, y, cw, "screen.fpv.race_pitch", () -> pitch + "\u00B0",
            () -> updatePitch(-15), () -> updatePitch(15), "-", "+");
        y += 24;
        addRow(cx, y, cw, "screen.fpv.race_offset", () -> String.valueOf(offset),
            () -> updateOffset(offset - 1), () -> updateOffset(offset + 1), "-", "+");
        y += 28;

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_color"),
            b -> updateColor()).bounds(cx, y, cw, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_flip"),
            b -> flipNormal()).bounds(cx, y, cw, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx, top + PANEL_H - 30, cw, 20).build());
    }

    private void addRow(int x, int y, int w, String key, java.util.function.Supplier<String> value,
                        Runnable minus, Runnable plus, String minusLabel, String plusLabel) {
        StepperRow row = new StepperRow(x, y, w, Component.translatable(key), value);
        rows.add(row);
        addRenderableWidget(Button.builder(Component.literal(minusLabel), b -> minus.run())
            .bounds(row.minusX(), y, StepperRow.BUTTON, StepperRow.BUTTON).build());
        addRenderableWidget(Button.builder(Component.literal(plusLabel), b -> plus.run())
            .bounds(row.plusX(), y, StepperRow.BUTTON, StepperRow.BUTTON).build());
    }

    private void send(int action, int value) {
        ClientPacketDistributor.sendToServer(new RaceGateConfigPacket(pos, action, value));
    }

    private void updateSize(int value) {
        size = Math.max(2, Math.min(15, value));
        RaceGateBlockEntity be = be();
        if (be != null) be.setSize(size);
        send(RaceGateConfigPacket.SET_SIZE, size);
    }

    private void updateYaw(int delta) {
        yaw = Math.floorMod(yaw + delta, 360);
        RaceGateBlockEntity be = be();
        if (be != null) be.adjustYaw(delta);
        send(RaceGateConfigPacket.ADJUST_YAW, delta);
    }

    private void updatePitch(int delta) {
        pitch = Math.max(-90, Math.min(90, pitch + delta));
        RaceGateBlockEntity be = be();
        if (be != null) be.adjustPitch(delta);
        send(RaceGateConfigPacket.ADJUST_PITCH, delta);
    }

    private void updateOffset(int value) {
        offset = Math.max(0, Math.min(12, value));
        RaceGateBlockEntity be = be();
        if (be != null) be.setOffset(offset);
        send(RaceGateConfigPacket.SET_OFFSET, offset);
    }

    private void updateColor() {
        int idx = 0;
        for (int i = 0; i < RaceGateBlockEntity.PALETTE.length; i++) {
            if (RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
        }
        color = RaceGateBlockEntity.PALETTE[(idx + 1) % RaceGateBlockEntity.PALETTE.length];
        RaceGateBlockEntity be = be();
        if (be != null) be.cycleColor();
        send(RaceGateConfigPacket.CYCLE_COLOR, 0);
    }

    private void flipNormal() {
        yaw = Math.floorMod(yaw + 180, 360);
        pitch = -pitch;
        RaceGateBlockEntity be = be();
        if (be != null) be.flipNormal();
        send(RaceGateConfigPacket.FLIP_NORMAL, 0);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiPanels.panel(g, left, top, PANEL_W, PANEL_H);
        GuiPanels.inset(g, left + 12, top + 26, PANEL_W - 24, 14, 0xFF000000 | color);
        for (StepperRow row : rows) row.drawPlate(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, title, left + PANEL_W / 2, top + 10, GuiPanels.TEXT);
        for (StepperRow row : rows) row.drawText(g, font);
        int hintY = top + 194;
        for (var line : font.split(Component.translatable("screen.fpv.race_gate_hint"), PANEL_W - 24)) {
            g.drawCenteredString(font, line, left + PANEL_W / 2, hintY, GuiPanels.LABEL);
            hintY += 10;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
