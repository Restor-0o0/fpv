package org.restor.fpv.client.ui.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.restor.fpv.Config;
import org.restor.fpv.client.ClientEventHandler;
import org.restor.fpv.client.ModKeyBindings;
import org.restor.fpv.entity.PlaneEntity;

import static org.restor.fpv.client.ui.hud.HudWidgets.*;

final class PlaneOsd {
    private PlaneOsd() {}

    static void render(GuiGraphicsExtractor graphics, int width, int height, PlaneEntity plane, float partialTick) {
        Font font = MC.font;
        int lh = lineHeight();
        int y = MARGIN;

        if (Config.HUD_HINTS.get()) {
            y = keyHints(graphics, y, new KeyMapping[]{
                ModKeyBindings.MOUSE_MODE,
                ModKeyBindings.PLANE_RESET_CAM,
                ModKeyBindings.PLANE_ORBIT,
                ModKeyBindings.PLANE_CALL_STRIKE,
                ModKeyBindings.OPEN_SETTINGS,
                ModKeyBindings.TOGGLE_HUD,
            }, bindHint(MC.options.keyAttack, "hud.fpv.hint_lock"),
               bindHint(MC.options.keyShift, "hud.fpv.hint_exit"));
        }

        if (Config.HUD_TELEMETRY.get()) {
            graphics.text(font, "ARM: " + (plane.isArmed() ? "ARMED" : "DISARMED"),
                MARGIN, y, plane.isArmed() ? GREEN : RED);
            graphics.text(font, String.format("ALT: %.0f m", altitude(plane)), MARGIN, y + lh, GREEN);
            graphics.text(font, String.format("CAM: %.0f°", plane.getGimbalPitch()), MARGIN, y + lh * 2, GREEN);

            boolean locked = ClientEventHandler.hasPlaneLock();
            String trk = plane.isOrbiting() ? "TRK: ORBIT" : locked ? "TRK: LOCK" : "TRK: FREE";
            graphics.text(font, trk, MARGIN, y + lh * 3,
                plane.isOrbiting() ? 0xFF55FFFF : locked ? 0xFFFFFF55 : GREEN);

            controllerAndSpeed(graphics, height, plane.getDeltaMovement().length() * 20);
        }

        float[] att = attitude(plane, plane.getPrevRoll(), plane.getRoll(), partialTick);
        if (Config.HUD_ROLL_LINE.get()) rollLine(graphics, width, height, att[2]);
        if (Config.HUD_PITCH_LADDER.get()) pitchLadder(graphics, width, height, att[1]);
        if (Config.HUD_HEADING.get()) heading(graphics, width, height, att[0]);
        if (Config.HUD_THROTTLE_BAR.get()) throttleBar(graphics, width, height, plane.getPredThrottle());
        if (Config.HUD_CROSSHAIR.get()) crosshair(graphics, width, height);
        if (Config.HUD_STICKS.get())
            stickBoxes(graphics, width, height, plane.getPredYaw(),
                plane.getPredThrottle(), plane.getPredRoll(), plane.getPredPitch());

        RaceHud.render(graphics, width, height);
    }
}
