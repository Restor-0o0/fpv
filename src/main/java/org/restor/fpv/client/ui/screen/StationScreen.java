package org.restor.fpv.client.ui.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.block.entity.ControlStationBlockEntity;
import org.restor.fpv.network.StationCodesPacket;
import org.restor.fpv.network.StartRemoteControlPacket;

import java.util.ArrayList;
import java.util.List;

public class StationScreen extends Screen {
    private static final int PANEL_W = 232;
    private static final int ROW_H = 22;

    private final BlockPos pos;
    private EditBox codeBox;
    private int left;
    private int top;
    private int panelH;
    private int listY;
    private final List<Integer> rowY = new ArrayList<>();

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
        List<String> codes = codes();
        int rows = Math.max(codes.size(), 1);
        panelH = 44 + rows * ROW_H + 12 + 26 + 12 + 20 + 12;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        listY = top + 44;

        rowY.clear();
        int y = listY;
        for (String code : codes) {
            String c = code;
            rowY.add(y);
            addRenderableWidget(Button.builder(Component.translatable("screen.fpv.station_fly"), b -> fly(c))
                .bounds(left + PANEL_W - 96, y, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal("\u2715"), b -> removeCode(c))
                .bounds(left + PANEL_W - 32, y, 20, 20).build());
            y += ROW_H;
        }
        if (codes.isEmpty()) y += ROW_H;

        y += 12;
        codeBox = new EditBox(font, left + 12, y, PANEL_W - 88, 20, Component.empty());
        codeBox.setMaxLength(6);
        codeBox.setResponder(s -> { if (!s.matches("\\d*")) codeBox.setValue(s.replaceAll("\\D", "")); });
        codeBox.setHint(Component.translatable("screen.fpv.station_code_hint"));
        addRenderableWidget(codeBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.station_add"), b -> addCode())
            .bounds(left + PANEL_W - 68, y, 56, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + (PANEL_W - 120) / 2, top + panelH - 32, 120, 20).build());
    }

    private void fly(String code) {
        ClientPacketDistributor.sendToServer(new StartRemoteControlPacket(code));
        onClose();
    }

    private void addCode() {
        String code = codeBox.getValue();
        if (code.length() != 6) return;
        ClientPacketDistributor.sendToServer(new StationCodesPacket(pos, code, true));
        if (minecraft.level.getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
            be.addCode(code);
        }
        rebuildWidgets();
    }

    private void removeCode(String code) {
        ClientPacketDistributor.sendToServer(new StationCodesPacket(pos, code, false));
        if (minecraft.level.getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
            be.removeCode(code);
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        if ((keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && codeBox.isFocused()) {
            addCode();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GuiPanels.panel(g, left, top, PANEL_W, panelH);
        List<String> codes = codes();
        int rows = Math.max(codes.size(), 1);
        GuiPanels.inset(g, left + 8, listY - 6, PANEL_W - 16, rows * ROW_H + 6, 0xFF1B1B22);
        for (int y : rowY) {
            GuiPanels.inset(g, left + 12, y, PANEL_W - 108, 20, 0xFF15151B);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        List<String> codes = codes();
        g.centeredText(font, title, left + PANEL_W / 2, top + 12, GuiPanels.TEXT);
        g.centeredText(font, Component.translatable("screen.fpv.station_hint"),
            left + PANEL_W / 2, top + 26, GuiPanels.LABEL);
        for (int i = 0; i < rowY.size(); i++) {
            g.text(font, codes.get(i), left + 20, rowY.get(i) + 6, GuiPanels.ACCENT, false);
        }
        if (codes.isEmpty()) {
            g.centeredText(font, Component.translatable("screen.fpv.station_empty"),
                left + PANEL_W / 2, listY + 6, GuiPanels.LABEL);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
