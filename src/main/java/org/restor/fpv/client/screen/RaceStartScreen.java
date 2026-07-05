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
        if (be != null) laps = be.getLaps();

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);

        RaceStartBlockEntity be = be();
        int gates = be != null ? be.getRouteSize() : 0;
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_route_size", gates),
            width / 2, 96, 0xAAAAAA);

        graphics.drawCenteredString(font, Component.translatable("screen.fpv.race_results"),
            width / 2, 110, 0xFFD040);

        List<RaceStartBlockEntity.Result> results = be != null ? be.getResults() : List.of();
        int y = 124;
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
