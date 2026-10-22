package org.restor.fpv.client.ui.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
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

    public static void hideVanillaOverlays(RenderGuiLayerEvent.Pre event) {
        if (!PilotView.isFpvActive()) return;

        String id = event.getName().getPath();
        if (id.equals("tab_list") || id.equals("chat")
            || id.equals("debug_overlay") || id.equals("subtitle_overlay")) {
            return;
        }
        event.setCanceled(true);
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
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
