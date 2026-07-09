package org.restor.fpv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.restor.fpv.block.entity.RaceStartBlockEntity;
import org.restor.fpv.network.ModNetworking;
import org.restor.fpv.network.RaceStartConfigPacket;

import java.util.List;

// Race course config: lap count + leaderboard of completed runs.
public class RaceStartScreen extends Screen {
    private final BlockPos pos;
    private int laps = 3;
    private int color = RaceStartBlockEntity.DEFAULT_START_COLOR;
    private int size = 6;
    private int yaw = 0;
    private int pitch = 0;
    private int offset = 0;
    private Button lapsButton;

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

        int cx = width / 2;
        int y = 44;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeLaps(-1))
            .bounds(cx - 90, y, 24, 20).build());
        lapsButton = addRenderableWidget(Button.builder(lapsLabel(), b -> {})
            .bounds(cx - 62, y, 124, 20).build());
        lapsButton.active = false;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeLaps(1))
            .bounds(cx + 66, y, 24, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_mark"), b -> {
            org.restor.fpv.client.RaceMarkingClient.startMarking(pos);
            onClose();
        }).bounds(cx - 90, y + 24, 180, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_start_color"), b -> changeColor())
            .bounds(cx - 90, y + 48, 180, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeSize(size - 1))
            .bounds(cx - 90, y + 72, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeSize(size + 1))
            .bounds(cx + 66, y + 72, 24, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> changeYaw(-15))
            .bounds(cx - 90, y + 96, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changeYaw(15))
            .bounds(cx + 66, y + 96, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changePitch(-15))
            .bounds(cx - 90, y + 120, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changePitch(15))
            .bounds(cx + 66, y + 120, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeOffset(offset - 1))
            .bounds(cx - 90, y + 144, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeOffset(offset + 1))
            .bounds(cx + 66, y + 144, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_flip"), b -> flipNormal())
            .bounds(cx - 90, y + 168, 180, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.race_clear"), b -> {
            ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, laps, true));
        }).bounds(cx - 90, height - 52, 180, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 90, height - 28, 180, 20).build());
    }

    private Component lapsLabel() {
        return Component.translatable("screen.fpv.race_laps").append(": " + laps);
    }

    private void changeLaps(int d) {
        laps = Math.max(1, Math.min(50, laps + d));
        lapsButton.setMessage(lapsLabel());
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, laps, false));
    }

    private void changeColor() {
        RaceStartBlockEntity be = be();
        if (be != null) be.cycleColor();
        int idx = 0;
        for (int i = 0; i < org.restor.fpv.block.entity.RaceGateBlockEntity.PALETTE.length; i++) {
            if (org.restor.fpv.block.entity.RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
        }
        color = org.restor.fpv.block.entity.RaceGateBlockEntity.PALETTE[(idx + 1) % org.restor.fpv.block.entity.RaceGateBlockEntity.PALETTE.length];
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, laps, false, true));
    }

    private void changeSize(int value) {
        size = Math.max(2, Math.min(15, value));
        RaceStartBlockEntity be = be();
        if (be != null) be.setSize(size);
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.SET_SIZE, size));
    }

    private void changeYaw(int delta) {
        yaw = Math.floorMod(yaw + delta, 360);
        RaceStartBlockEntity be = be();
        if (be != null) be.adjustYaw(delta);
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.ADJUST_YAW, delta));
    }

    private void changePitch(int delta) {
        pitch = Math.max(-90, Math.min(90, pitch + delta));
        RaceStartBlockEntity be = be();
        if (be != null) be.adjustPitch(delta);
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.ADJUST_PITCH, delta));
    }

    private void changeOffset(int value) {
        offset = Math.max(0, Math.min(12, value));
        RaceStartBlockEntity be = be();
        if (be != null) be.setOffset(offset);
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.SET_OFFSET, offset));
    }

    private void flipNormal() {
        yaw = Math.floorMod(yaw + 180, 360);
        pitch = -pitch;
        RaceStartBlockEntity be = be();
        if (be != null) be.flipNormal();
        ModNetworking.CHANNEL.sendToServer(new RaceStartConfigPacket(pos, RaceStartConfigPacket.FLIP_NORMAL, 0));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.fill(width / 2 - 20, 28, width / 2 + 20, 40, 0xFF000000 | color);

        RaceStartBlockEntity be = be();
        int gates = be != null ? be.getRouteSize() : 0;
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_yaw").append(": " + yaw + "°"),
            width / 2, 146, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_pitch").append(": " + pitch + "°"),
            width / 2, 170, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_offset").append(": " + offset),
            width / 2, 194, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_size").append(": " + size),
            width / 2, 122, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_route_size", gates),
            width / 2, 214, 0xAAAAAA);

        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_results"),
            width / 2, 228, 0xFFD040);

        List<RaceStartBlockEntity.Result> results = be != null ? be.getResults() : List.of();
        int y = 242;
        if (results.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_no_results"),
                width / 2, y, 0x888888);
        } else {
            int rank = 1;
            for (RaceStartBlockEntity.Result r : results) {
                if (y > height - 60) break;
                String line = String.format("%2d. %-16s %s", rank++, r.player(),
                    RaceStartBlockEntity.formatTime(r.timeTicks()));
                int col = rank == 2 ? 0xFFE860 : 0xCCCCCC;
                graphics.drawString(font, line, width / 2 - 90, y, col);
                y += 11;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
