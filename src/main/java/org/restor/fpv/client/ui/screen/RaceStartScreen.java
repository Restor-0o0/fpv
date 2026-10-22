package org.restor.fpv.client.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;
import org.restor.fpv.network.RaceStartConfigPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Race course config: settings column on the left, leaderboard column on the right.
public class RaceStartScreen extends Screen {
    private static final int COL_W = 164;
    private static final int PANEL_W = COL_W * 2 + 36;
    private static final int PANEL_H = 252;

    private final BlockPos pos;
    private final List<StepperRow> rows = new ArrayList<>();
    private int laps = 3;
    private int color = RaceStartBlockEntity.DEFAULT_START_COLOR;
    private int size = 6;
    private int yaw = 0;
    private int pitch = 0;
    private int offset = 0;
    private int left;
    private int top;

    public RaceStartScreen(BlockPos pos) {
        super(Component.translatable("screen.fpv.race_start"));
        this.pos = pos;
    }

    private RaceStartBlockEntity be() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof RaceStartBlockEntity b) return b;
        return null;
    }

    @Override
    protected void init() {
        RaceStartBlockEntity be = be();
        if (be != null) {
            laps = be.getLaps();
            color = be.getColor();
            size = be.getSize();
            yaw = be.getYaw();
            pitch = be.getPitch();
            offset = be.getOffset();
        }

        rows.clear();
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - PANEL_H) / 2);

        int lx = left + 12;
        int rx = left + 24 + COL_W;
        int y = top + 46;

        addRow(lx, y, "screen.fpv.race_laps", () -> String.valueOf(laps),
            () -> changeLaps(-1), () -> changeLaps(1), "-", "+");
        y += 24;
        addRow(lx, y, "screen.fpv.race_size", () -> String.valueOf(size),
            () -> changeSize(size - 1), () -> changeSize(size + 1), "-", "+");
        y += 24;
        addRow(lx, y, "screen.fpv.race_yaw", () -> yaw + "\u00B0",
            () -> changeYaw(-15), () -> changeYaw(15), "<", ">");
        y += 24;
        addRow(lx, y, "screen.fpv.race_pitch", () -> pitch + "\u00B0",
            () -> changePitch(-15), () -> changePitch(15), "-", "+");
        y += 24;
        addRow(lx, y, "screen.fpv.race_offset", () -> String.valueOf(offset),
            () -> changeOffset(offset - 1), () -> changeOffset(offset + 1), "-", "+");
        y += 28;

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_start_color"),
            b -> changeColor()).bounds(lx, y, COL_W, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_flip"),
            b -> flipNormal()).bounds(lx, y, COL_W, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_mark"), b -> {
            org.restor.fpv.client.RaceMarkingClient.startMarking(pos);
            onClose();
        }).bounds(lx, y, COL_W, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_clear"),
            b -> PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, laps, true)))
            .bounds(rx, top + PANEL_H - 30, COL_W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(lx, top + PANEL_H - 30, COL_W, 20).build());
    }

    private void addRow(int x, int y, String key, Supplier<String> value,
                        Runnable minus, Runnable plus, String minusLabel, String plusLabel) {
        StepperRow row = new StepperRow(x, y, COL_W, Component.translatable(key), value);
        rows.add(row);
        addRenderableWidget(Button.builder(Component.literal(minusLabel), b -> minus.run())
            .bounds(row.minusX(), y, StepperRow.BUTTON, StepperRow.BUTTON).build());
        addRenderableWidget(Button.builder(Component.literal(plusLabel), b -> plus.run())
            .bounds(row.plusX(), y, StepperRow.BUTTON, StepperRow.BUTTON).build());
    }

    private void changeLaps(int d) {
        laps = Math.max(1, Math.min(50, laps + d));
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, laps, false));
    }

    private void changeColor() {
        RaceStartBlockEntity be = be();
        if (be != null) be.cycleColor();
        int idx = 0;
        for (int i = 0; i < RaceGateBlockEntity.PALETTE.length; i++) {
            if (RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
        }
        color = RaceGateBlockEntity.PALETTE[(idx + 1) % RaceGateBlockEntity.PALETTE.length];
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, laps, false, true));
    }

    private void changeSize(int value) {
        size = Math.max(2, Math.min(15, value));
        RaceStartBlockEntity be = be();
        if (be != null) be.setSize(size);
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.SET_SIZE, size));
    }

    private void changeYaw(int delta) {
        yaw = Math.floorMod(yaw + delta, 360);
        RaceStartBlockEntity be = be();
        if (be != null) be.adjustYaw(delta);
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.ADJUST_YAW, delta));
    }

    private void changePitch(int delta) {
        pitch = Math.max(-90, Math.min(90, pitch + delta));
        RaceStartBlockEntity be = be();
        if (be != null) be.adjustPitch(delta);
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.ADJUST_PITCH, delta));
    }

    private void changeOffset(int value) {
        offset = Math.max(0, Math.min(12, value));
        RaceStartBlockEntity be = be();
        if (be != null) be.setOffset(offset);
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.SET_OFFSET, offset));
    }

    private void flipNormal() {
        yaw = Math.floorMod(yaw + 180, 360);
        pitch = -pitch;
        RaceStartBlockEntity be = be();
        if (be != null) be.flipNormal();
        PacketDistributor.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.FLIP_NORMAL, 0));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int rx = left + 24 + COL_W;
        GuiPanels.panel(g, left, top, PANEL_W, PANEL_H);
        GuiPanels.inset(g, left + 12, top + 26, COL_W, 14, 0xFF000000 | color);
        GuiPanels.inset(g, rx, top + 26, COL_W, PANEL_H - 62, 0xFF1B1B22);
        for (StepperRow row : rows) row.drawPlate(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int rx = left + 24 + COL_W;
        g.drawCenteredString(font, title, left + PANEL_W / 2, top + 10, GuiPanels.TEXT);
        for (StepperRow row : rows) row.drawText(g, font);

        RaceStartBlockEntity be = be();
        int gates = be != null ? be.getRouteSize() : 0;
        g.drawString(font, Component.translatable("screen.fpv.race_results"), rx + 6, top + 32, 0xFFFFD040, false);
        g.drawString(font, Component.translatable("screen.fpv.race_route_size", gates),
            rx + 6, top + 44, GuiPanels.LABEL, false);
        g.fill(rx + 6, top + 56, rx + COL_W - 6, top + 57, 0xFF3C3C46);

        List<RaceStartBlockEntity.Result> results = be != null ? be.getResults() : List.of();
        int y = top + 62;
        int bottom = top + PANEL_H - 38;
        if (results.isEmpty()) {
            g.drawString(font, Component.translatable("screen.fpv.race_no_results"), rx + 6, y, 0xFF888888, false);
        } else {
            int rank = 1;
            for (RaceStartBlockEntity.Result r : results) {
                if (y + 10 > bottom) break;
                int col = rank == 1 ? 0xFFFFE860 : 0xFFCCCCCC;
                String name = r.player();
                if (font.width(name) > COL_W - 78) name = font.plainSubstrByWidth(name, COL_W - 84) + "..";
                g.drawString(font, rank + ".", rx + 6, y, col, false);
                g.drawString(font, name, rx + 22, y, col, false);
                String time = RaceStartBlockEntity.formatTime(r.timeTicks());
                g.drawString(font, time, rx + COL_W - 6 - font.width(time), y, col, false);
                rank++;
                y += 11;
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
