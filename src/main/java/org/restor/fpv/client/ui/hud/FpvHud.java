package org.restor.fpv.client.ui.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.restor.fpv.client.LineOfSightSession;
import org.restor.fpv.client.PilotView;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

public final class FpvHud {
    private static boolean visible = true;

    private FpvHud() {}

    public static void toggle() {
        visible = !visible;
    }

    public static void hideVanillaOverlays(RenderGuiOverlayEvent.Pre event) {
        if (!PilotView.isFpvActive()) return;
        if (event.getOverlay() == null) return;

        String id = event.getOverlay().id().toString();
        if (id.contains("player_list") || id.contains("chat_panel")
            || id.contains("debug_overlay") || id.contains("subtitles")) {
            return;
        }
        event.setCanceled(true);
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!visible) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        DroneEntity los = LineOfSightSession.drone();
        if (los != null) {
            DroneOsd.render(graphics, width, height, los, partialTick);
            return;
        }
        if (!PilotView.isFpvActive()) return;
        DroneEntity drone = PilotView.currentDrone();
        if (drone != null) {
            DroneOsd.render(graphics, width, height, drone, partialTick);
            return;
        }
        PlaneEntity plane = PilotView.currentPlane();
        if (plane != null) {
            PlaneOsd.render(graphics, width, height, plane, partialTick);
        }
    }
}
