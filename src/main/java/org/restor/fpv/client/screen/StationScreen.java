package org.restor.fpv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.block.entity.ControlStationBlockEntity;
import org.restor.fpv.network.StationCodesPacket;
import org.restor.fpv.network.StartRemoteControlPacket;

import java.util.List;

// Control station UI: saved code list — pick a drone to fly, add or remove codes.
public class StationScreen extends Screen {
    private final BlockPos pos;
    private EditBox codeBox;

    public StationScreen(BlockPos pos) {
        super(Component.translatable("screen.fpv.station"));
        this.pos = pos;
    }

    private List<String> codes() {
        if (minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
            return be.getCodes();
        }
        return List.of();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = 40;

        for (String code : codes()) {
            String c = code;
            addRenderableWidget(Button.builder(
                    Component.literal(c + "  ").append(Component.translatable("screen.fpv.station_fly")),
                    b -> fly(c))
                .bounds(cx - 90, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> removeCode(c))
                .bounds(cx + 64, y, 20, 20).build());
            y += 24;
        }

        y += 8;
        codeBox = new EditBox(font, cx - 90, y, 120, 20, Component.empty());
        codeBox.setMaxLength(6);
        codeBox.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(codeBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.station_add"), b -> addCode())
            .bounds(cx + 34, y, 50, 20).build());
        y += 28;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 75, y, 150, 20).build());
    }

    private void fly(String code) {
        PacketDistributor.sendToServer(new StartRemoteControlPacket(code));
        onClose();
    }

    private void addCode() {
        String code = codeBox.getValue();
        if (code.length() != 6) return;
        PacketDistributor.sendToServer(new StationCodesPacket(pos, code, true));
        // Optimistic local update so the list refreshes immediately (server re-syncs).
        if (minecraft.level.getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
            be.addCode(code);
        }
        rebuildWidgets();
    }

    private void removeCode(String code) {
        PacketDistributor.sendToServer(new StationCodesPacket(pos, code, false));
        if (minecraft.level.getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
            be.removeCode(code);
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && codeBox.isFocused()) { // Enter
            addCode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.station_hint"), width / 2, 26, 0xAAAAAA);
        if (codes().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.fpv.station_empty"), width / 2, 44, 0x888888);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
