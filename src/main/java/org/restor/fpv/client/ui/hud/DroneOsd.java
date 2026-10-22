package org.restor.fpv.client.ui.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.Config;
import org.restor.fpv.client.ClientEventHandler;
import org.restor.fpv.client.ModKeyBindings;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.flight.RadioFailsafe;

import java.util.ArrayList;
import java.util.List;

import static org.restor.fpv.client.ui.hud.HudWidgets.*;

final class DroneOsd {
    private DroneOsd() {}

    static void render(GuiGraphics graphics, int width, int height, DroneEntity drone, float partialTick) {
        Font font = MC.font;
        int lh = lineHeight();
        int y = MARGIN;
        boolean heavy = drone.isHeavyLift();

        if (Config.HUD_HINTS.get()) {
            List<KeyMapping> hints = new ArrayList<>(List.of(
                ModKeyBindings.CYCLE_MODE,
                ModKeyBindings.ARM_SWITCH,
                ModKeyBindings.CAMERA_UP,
                ModKeyBindings.CAMERA_DOWN,
                ModKeyBindings.FIRE_PAYLOAD,
                ModKeyBindings.FIRE_WEAPON,
                ModKeyBindings.OPEN_SETTINGS,
                ModKeyBindings.TOGGLE_HUD));
            if (heavy) {
                hints.add(ModKeyBindings.MOUSE_MODE);
                hints.add(ModKeyBindings.PLANE_RESET_CAM);
            }
            y = keyHints(graphics, y, hints.toArray(new KeyMapping[0]),
                bindHint(MC.options.keyShift, "hud.fpv.hint_exit"));
        }

        if (Config.HUD_TELEMETRY.get()) {
            org.restor.fpv.flight.FlightMode flightMode = drone.getFlightMode();
            int modeColor = flightMode == org.restor.fpv.flight.FlightMode.TURTLE ? 0xFFFFC040 : GREEN;
            graphics.drawString(font, Component.translatable("hud.fpv.mode",
                Component.translatable(flightMode.translationKey())), MARGIN, y, modeColor);
            y += lh;

            if (heavy) {
                String cam = String.format("CAM: PAN %+.0f  TILT %+.0f", drone.getGimbalYaw(), drone.getGimbalPitch());
                graphics.drawString(font, cam, MARGIN, y, GREEN);
                y += lh;
            }

            RadioFailsafe.State radio = drone.getRadioState();
            int radioColor = radio == RadioFailsafe.State.ARMED ? GREEN : radio.isFailsafe() ? 0xFFFFA000 : RED;
            graphics.drawString(font, "RADIO: " + radio.name(), MARGIN, y, radioColor);
            y += lh;

            float battery = drone.getBatteryPercent();
            String batt = String.format("BAT: %.1fV %.0f%%", 3.7f + (battery * 0.5f), battery * 100);
            graphics.drawString(font, batt, MARGIN, y, battery > 0.3f ? GREEN : RED);
            y += lh;

            float rssi = drone.getRssi();
            graphics.drawString(font, String.format("RSSI: %.0f%%", rssi * 100), MARGIN, y, rssi > 0.3f ? GREEN : RED);
            y += lh;

            if (Config.HUD_NETSTATS.get()) {
                int lagTicks = Math.max(0, ClientEventHandler.getControlSeq() - drone.getAckSeq());
                float mspt = drone.getServerMspt();
                String net = String.format("LINK: %dms  SRV: %.0fms", lagTicks * 50, mspt);
                graphics.drawString(font, net, MARGIN, y, lagTicks > 4 || mspt > 50f ? 0xFFFF3030 : GREEN);
                y += lh;
            }

            int payload = drone.getPayloadAmmo();
            if (payload > 0) {
                graphics.drawString(font, "PAYLOAD: " + payload, MARGIN, y, 0xFFFFC040);
                y += lh;
            }

            if (drone.rocketCapacity() > 0) {
                graphics.drawString(font, "ROCKETS: " + drone.getRocketAmmo() + "/" + drone.rocketCapacity(),
                    MARGIN, y, drone.getRocketAmmo() > 0 ? 0xFFFF8040 : 0xFF808080);
                y += lh;
            }

            graphics.drawString(font, String.format("ALT: %.0f m", altitude(drone)), MARGIN, y, GREEN);
            y += lh;
            graphics.drawString(font, String.format("CAM: %.0f°", drone.getCameraAngle()), MARGIN, y, GREEN);

            Vec3 v = drone.getDeltaMovement();
            controllerAndSpeed(graphics, height, Math.sqrt(v.x * v.x + v.z * v.z) * 20);
        }

        if (!drone.getRadioState().motorsArmed()) armWarning(graphics, width, height);

        float[] att = attitude(drone, drone.getPrevRoll(), drone.getRoll(), partialTick);

        if (!heavy) {
            if (Config.HUD_ROLL_LINE.get()) rollLine(graphics, width, height, att[2]);
            if (Config.HUD_PITCH_LADDER.get()) pitchLadder(graphics, width, height, att[1]);
        }
        if (Config.HUD_HEADING.get()) heading(graphics, width, height, att[0]);

        if (Config.HUD_THROTTLE_BAR.get()) {
            if (heavy) verticalSpeedBar(graphics, width, height, drone.getPredThrottle(), (float) drone.getDeltaMovement().y * 20f);
            else throttleBar(graphics, width, height, drone.getPredThrottle());
        }

        if (Config.HUD_CROSSHAIR.get()) crosshair(graphics, width, height);
        if (Config.HUD_STICKS.get() && !heavy)
            stickBoxes(graphics, width, height, drone.getPredYaw(),
                drone.getPredThrottle(), drone.getPredRoll(), drone.getPredPitch());
        if (Config.HUD_DAMAGE.get()) DamageHud.render(graphics, width, height, drone);

        RaceHud.render(graphics, width, height);
    }

    private static void armWarning(GuiGraphics graphics, int width, int height) {
        String key = ModKeyBindings.ARM_SWITCH.getTranslatedKeyMessage().getString();
        String message = Component.translatable("hud.fpv.arm_warning", key).getString();
        boolean bright = MC.level == null || (MC.level.getGameTime() / 8L & 1L) == 0L;
        int textWidth = MC.font.width(message);
        int x = width / 2;
        int y = height / 2 - 34;
        graphics.fill(x - textWidth / 2 - 6, y - 4, x + textWidth / 2 + 6,
            y + MC.font.lineHeight + 4, bright ? 0xB0000000 : 0x60000000);
        graphics.drawCenteredString(MC.font, message, x, y, bright ? 0xFFFF3030 : 0x88FF3030);
    }
}
