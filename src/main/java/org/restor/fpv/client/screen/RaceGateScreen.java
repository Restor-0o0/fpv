package org.restor.fpv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.network.RaceGateConfigPacket;

// Race gate config: frame size, colour, and orientation (yaw / pitch angle).
public class RaceGateScreen extends Screen {
    private final BlockPos pos;
    private int size = 5;
    private int yaw = 0;
    private int pitch = 0;
    private int offset = 0;
    private int color = RaceGateBlockEntity.PALETTE[0];

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

        int cx = width / 2;
        int y = 50;

        // size
        addRenderableWidget(Button.builder(Component.literal("-"),
            b -> updateSize(size - 1)).bounds(cx - 90, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
            b -> updateSize(size + 1)).bounds(cx + 66, y, 24, 20).build());
        y += 24;
        // yaw
        addRenderableWidget(Button.builder(Component.literal("<"),
            b -> updateYaw(-15)).bounds(cx - 90, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
            b -> updateYaw(15)).bounds(cx + 66, y, 24, 20).build());
        y += 24;
        // pitch
        addRenderableWidget(Button.builder(Component.literal("-"),
            b -> updatePitch(-15)).bounds(cx - 90, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
            b -> updatePitch(15)).bounds(cx + 66, y, 24, 20).build());
        y += 24;
        // offset
        addRenderableWidget(Button.builder(Component.literal("-"),
            b -> updateOffset(offset - 1)).bounds(cx - 90, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
            b -> updateOffset(offset + 1)).bounds(cx + 66, y, 24, 20).build());
        y += 24;
        // colour
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_color"),
            b -> updateColor()).bounds(cx - 90, y, 180, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_flip"),
            b -> flipNormal()).bounds(cx - 90, y, 180, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 90, height - 28, 180, 20).build());
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(new RaceGateConfigPacket(pos, action, value));
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
        for (int i = 0; i < RaceGateBlockEntity.PALETTE.length; i++) if (RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);

        int cx = width / 2;

        graphics.fill(cx - 20, 30, cx + 20, 42, 0xFF000000 | color);
        // Value labels sit between each -/+ pair.
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_size").append(": " + size), cx, 56, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_yaw").append(": " + yaw + "°"), cx, 80, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_pitch").append(": " + pitch + "°"), cx, 104, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_offset").append(": " + offset), cx, 128, 0xFFFFFF);

        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_gate_hint"),
            width / 2, height - 46, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
