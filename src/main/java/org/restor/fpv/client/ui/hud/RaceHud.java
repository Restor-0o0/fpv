package org.restor.fpv.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.restor.fpv.block.entity.RaceStartBlockEntity;
import org.restor.fpv.client.ClientRaceState;

final class RaceHud {
    private static final Minecraft MC = Minecraft.getInstance();

    private RaceHud() {}

    static void render(GuiGraphicsExtractor graphics, int width, int height) {
        Font font = MC.font;
        int finishTicks = ClientRaceState.finishTicks();
        if (finishTicks >= 0) {
            String s = "FINISH  " + RaceStartBlockEntity.formatTime(finishTicks);
            graphics.centeredText(font, s, width / 2, (int) (height * 0.16f), 0xFFFFD040);
            return;
        }
        if (!ClientRaceState.active()) return;

        long start = ClientRaceState.startTime();
        long now = MC.level != null ? MC.level.getGameTime() : start;
        int elapsed = (int) Math.max(0, now - start);
        int x = width - 10;
        int y = (int) (height * 0.12f);
        String t = "TIME " + RaceStartBlockEntity.formatTime(elapsed);
        graphics.text(font, t, x - font.width(t), y, 0xFF40FF40);
        String lap = "LAP " + ClientRaceState.lap() + "/" + ClientRaceState.laps();
        graphics.text(font, lap, x - font.width(lap), y + 11, 0xFFFFFFFF);
        int hit = ClientRaceState.gatesHit();
        int total = ClientRaceState.gatesTotal();
        if (total > 0) {
            String gate = "GATE " + hit + "/" + total;
            graphics.text(font, gate, x - font.width(gate), y + 22, hit >= total ? 0xFF40FF40 : 0xFFFFC040);
        }
    }
}
