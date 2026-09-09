package org.restor.fpv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.controller.RCController;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.flight.FlightMode;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class FPVOverlay {
    private static final Minecraft MC = Minecraft.getInstance();
    // Runtime HUD visibility, toggled by the H key (per-element visibility is in Config).
    private static boolean hudVisible = true;

    // Race HUD state, pushed from the server via RaceTimerPacket. lap == 0 = not racing;
    // finishTicks >= 0 = a run just finished (show the final time briefly).
    private static int raceLap, raceLaps, raceGatesHit, raceGatesTotal;
    private static long raceStartTime;
    private static int raceFinishTicks = -1;
    private static long raceFinishAtMs;

    public static void setRaceState(int lap, int laps, long startTime, int gatesHit, int gatesTotal, int finishTicks) {
        raceLap = lap;
        raceLaps = laps;
        raceStartTime = startTime;
        raceGatesHit = gatesHit;
        raceGatesTotal = gatesTotal;
        raceFinishTicks = finishTicks;
        if (finishTicks >= 0) raceFinishAtMs = System.currentTimeMillis();
    }

    public static int raceGatesHit() {
        return raceLap > 0 ? raceGatesHit : 0;
    }

    public static boolean raceActive() {
        return raceLap > 0;
    }

    private static void renderRaceHud(GuiGraphics graphics, int width, int height) {
        Font font = MC.font;
        if (raceFinishTicks >= 0) {
            if (System.currentTimeMillis() - raceFinishAtMs > 5000) { raceFinishTicks = -1; return; }
            String s = "FINISH  " + org.restor.fpv.block.entity.RaceStartBlockEntity.formatTime(raceFinishTicks);
            graphics.drawCenteredString(font, s, width / 2, (int) (height * 0.16f), 0xFFFFD040);
            return;
        }
        if (raceLap <= 0) return;
        long now = MC.level != null ? MC.level.getGameTime() : raceStartTime;
        int elapsed = (int) Math.max(0, now - raceStartTime);
        int x = width - 10;
        int y = (int) (height * 0.12f);
        String t = "TIME " + org.restor.fpv.block.entity.RaceStartBlockEntity.formatTime(elapsed);
        graphics.drawString(font, t, x - font.width(t), y, 0xFF40FF40);
        String lap = "LAP " + raceLap + "/" + raceLaps;
        graphics.drawString(font, lap, x - font.width(lap), y + 11, 0xFFFFFFFF);
        if (raceGatesTotal > 0) {
            String gate = "GATE " + raceGatesHit + "/" + raceGatesTotal;
            int gc = raceGatesHit >= raceGatesTotal ? 0xFF40FF40 : 0xFFFFC040;
            graphics.drawString(font, gate, x - font.width(gate), y + 22, gc);
        }
    }

    // The drone this client is flying: as a passenger, or remotely via a control
    // station (server put our camera on the drone).
    private static DroneEntity currentDrone() {
        if (MC.player == null) return null;
        if (MC.player.getVehicle() instanceof DroneEntity d) return d;
        if (MC.getCameraEntity() instanceof DroneEntity d) return d;
        return null;
    }

    // Planes are riding-only (no remote camera station like the drones).
    private static PlaneEntity currentPlane() {
        if (MC.player == null) return null;
        if (MC.player.getVehicle() instanceof PlaneEntity p) return p;
        return null;
    }

    private static boolean isFPVActive() {
        return (currentDrone() != null || currentPlane() != null)
            && MC.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!isFPVActive()) return;

        DroneEntity drone = currentDrone();
        if (drone != null) {
            // Interpolate between the previous-tick snapshot and the current-tick snapshot.
            // yRotO/xRotO are saved by super.tick() before clientTick() updates yRot/xRot;
            // rollO/displayRoll are maintained the same way in DroneEntity.tick().
            // Interpolate the whole ORIENTATION as a quaternion, not per-axis Euler lerps.
            // Near pitch ±90 (nose almost straight down) the Euler decomposition flips
            // yaw/roll by 180° — per-axis lerps jerk violently there even though the
            // actual rotation is continuous; slerp follows the true arc.
            float pt = (float) event.getPartialTick();
            // Heavy rigs carry the camera on a stabilised belly gimbal: it follows the
            // airframe's heading only, so the horizon stays level and straight down is a
            // hard stop instead of a pole the view spins around.
            if (drone.isHeavyLift()) {
                float heading = Mth.rotLerp(pt, drone.yRotO, drone.getYRot());
                event.setYaw(heading + drone.getGimbalYaw());
                event.setPitch(-drone.getGimbalPitch());
                event.setRoll(0f);
                return;
            }
            org.joml.Quaternionf q = new org.joml.Quaternionf(
                    DroneEntity.attitudeQuat(drone.yRotO, drone.xRotO, drone.getPrevRoll()))
                .slerp(DroneEntity.attitudeQuat(drone.getYRot(), drone.getXRot(), drone.getRoll()), pt);
            // Camera uptilt applied in the CAMERA's local frame (correct at any attitude).
            q.rotateX((float) Math.toRadians(-drone.getCameraAngle()));
            float[] a = DroneEntity.decomposeAttitude(q);

            event.setYaw(a[0]);
            event.setPitch(a[1]);
            event.setRoll(a[2]);
            return;
        }

        PlaneEntity plane = currentPlane();
        if (plane == null) return;

        float pt = (float) event.getPartialTick();
        org.joml.Quaternionf q = new org.joml.Quaternionf(
                DroneEntity.attitudeQuat(plane.yRotO, plane.xRotO, plane.getPrevRoll()))
            .slerp(DroneEntity.attitudeQuat(plane.getYRot(), plane.getXRot(), plane.getRoll()), pt)
            // Belly-camera gimbal pan/tilt, applied in the camera's local frame.
            .rotateY((float) Math.toRadians(-plane.getGimbalYaw()))
            .rotateX((float) Math.toRadians(-plane.getGimbalPitch()));
        if (plane.isStabilizedGimbalView()) {
            org.joml.Vector3f fwd = q.transform(new org.joml.Vector3f(0, 0, 1));
            float yaw = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
            float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(fwd.y, -1f, 1f)));

            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setRoll(0f);
        } else {
            float[] a = DroneEntity.decomposeAttitude(q);
            event.setYaw(a[0]);
            event.setPitch(a[1]);
            event.setRoll(a[2]);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Pre event) {
        if (!isFPVActive()) return;
        if (event.getName() == null) return;

        String overlayPath = event.getName().toString();
        if (overlayPath.contains("player_list") || overlayPath.contains("chat_panel")
            || overlayPath.contains("debug_overlay") || overlayPath.contains("subtitles")) {
            return;
        }

        event.setCanceled(true);
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    public static void renderOverlay(GuiGraphics graphics, float partialTick) {
        if (!hudVisible) return;
        DroneEntity los = org.restor.fpv.client.LineOfSightSession.drone();
        if (los != null) {
            renderOSD(graphics, graphics.guiWidth(), graphics.guiHeight(), los, partialTick);
            return;
        }
        if (!isFPVActive()) return;
        DroneEntity drone = currentDrone();
        if (drone != null) {
            renderOSD(graphics, graphics.guiWidth(), graphics.guiHeight(), drone, partialTick);
            return;
        }
        PlaneEntity plane = currentPlane();
        if (plane != null) {
            renderPlaneOSD(graphics, graphics.guiWidth(), graphics.guiHeight(), plane, partialTick);
        }
    }

    private static void fillRect(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    // Control-key hint list (top-left). Built from the live keybindings so the labels
    // localize and reflect any rebind the player made. Returns the Y just below the block
    // so telemetry can stack underneath it.
    private static int renderKeyHints(GuiGraphics graphics, int margin, int startY,
                                      net.minecraft.client.KeyMapping[] keys, String... extra) {
        Font font = MC.font;
        int lineHeight = font.lineHeight + 2;
        int color = 0xC0FFFF55; // dim yellow, distinct from the green telemetry
        int y = startY;
        for (net.minecraft.client.KeyMapping k : keys) {
            String key = k.getTranslatedKeyMessage().getString();
            String name = net.minecraft.network.chat.Component.translatable(k.getName()).getString();
            graphics.drawString(font, "[" + key + "] " + name, margin, y, color);
            y += lineHeight;
        }
        for (String line : extra) {
            graphics.drawString(font, line, margin, y, color);
            y += lineHeight;
        }
        return y + 4; // small gap before telemetry
    }

    private static String bindHint(net.minecraft.client.KeyMapping key, String labelKey) {
        return "[" + key.getTranslatedKeyMessage().getString() + "] "
            + net.minecraft.network.chat.Component.translatable(labelKey).getString();
    }

    private static void renderOSD(GuiGraphics graphics, int width, int height, DroneEntity drone, float partialTick) {
        Font font = MC.font;
        String nl = "\n";
        int color = 0xFF00FF00;
        int lineHeight = font.lineHeight + 2;
        int margin = 10;

        int y = margin;
        boolean heavy = drone.isHeavyLift();

        if (org.restor.fpv.Config.HUD_HINTS.get()) {
            java.util.List<net.minecraft.client.KeyMapping> hints = new java.util.ArrayList<>(java.util.List.of(
                org.restor.fpv.client.ModKeyBindings.CYCLE_MODE,
                org.restor.fpv.client.ModKeyBindings.ARM_SWITCH,
                org.restor.fpv.client.ModKeyBindings.CAMERA_UP,
                org.restor.fpv.client.ModKeyBindings.CAMERA_DOWN,
                org.restor.fpv.client.ModKeyBindings.FIRE_PAYLOAD,
                org.restor.fpv.client.ModKeyBindings.OPEN_SETTINGS,
                org.restor.fpv.client.ModKeyBindings.TOGGLE_HUD));
            if (heavy) {
                hints.add(org.restor.fpv.client.ModKeyBindings.MOUSE_MODE);
                hints.add(org.restor.fpv.client.ModKeyBindings.PLANE_RESET_CAM);
            }
            y = renderKeyHints(graphics, margin, y,
                hints.toArray(new net.minecraft.client.KeyMapping[0]),
                bindHint(MC.options.keyShift, "hud.fpv.hint_exit"));
        }

        if (org.restor.fpv.Config.HUD_TELEMETRY.get()) {
            // Flight mode
            String mode = "MODE: " + drone.getFlightMode().name();
            graphics.drawString(font, mode, margin, y, color);
            y += lineHeight;

            if (heavy) {
                String cam = String.format("CAM: PAN %+.0f  TILT %+.0f",
                    drone.getGimbalYaw(), drone.getGimbalPitch());
                graphics.drawString(font, cam, margin, y, color);
                y += lineHeight;
            }

            // Armed status
            org.restor.fpv.flight.RadioFailsafe.State radioState = drone.getRadioState();
            String armed = "RADIO: " + radioState.name();
            int armedColor = radioState == org.restor.fpv.flight.RadioFailsafe.State.ARMED
                ? 0xFF00FF00 : radioState.isFailsafe() ? 0xFFFFA000 : 0xFFFF0000;
            graphics.drawString(font, armed, margin, y, armedColor);
            y += lineHeight;

            // Battery
            float battery = drone.getBatteryPercent();
            String battStr = String.format("BAT: %.1fV %.0f%%", 3.7f + (battery * 0.5f), battery * 100);
            graphics.drawString(font, battStr, margin, y, battery > 0.3f ? color : 0xFFFF0000);
            y += lineHeight;

            // RSSI
            float rssi = drone.getRssi();
            String rssiStr = String.format("RSSI: %.0f%%", rssi * 100);
            graphics.drawString(font, rssiStr, margin, y, rssi > 0.3f ? color : 0xFFFF0000);
            y += lineHeight;

            if (org.restor.fpv.Config.HUD_NETSTATS.get()) {
                int lagTicks = Math.max(0,
                    org.restor.fpv.client.ClientEventHandler.getControlSeq() - drone.getAckSeq());
                float mspt = drone.getServerMspt();
                String net = String.format("LINK: %dms  SRV: %.0fms", lagTicks * 50, mspt);
                graphics.drawString(font, net, margin, y,
                    lagTicks > 4 || mspt > 50f ? 0xFFFF3030 : color);
                y += lineHeight;
            }

            // Payload charges (only shown when carrying)
            int payload = drone.getPayloadAmmo();
            if (payload > 0) {
                graphics.drawString(font, "PAYLOAD: " + payload, margin, y, 0xFFFFC040);
                y += lineHeight;
            }

            // Altitude above terrain
            int surfaceY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                drone.blockPosition().getX(), drone.blockPosition().getZ());
            String altStr = String.format("ALT: %.0f m", Math.max(0, drone.getY() - surfaceY));
            graphics.drawString(font, altStr, margin, y, color);
            y += lineHeight;

            String camStr = String.format("CAM: %.0f°", drone.getCameraAngle());
            graphics.drawString(font, camStr, margin, y, color);

            // Controller status + horizontal speed (bottom left). Vertical climb is useful,
            // but ground speed is what shows whether tilt is converting thrust into forward motion.
            String ctrlStr = RCController.isConnected()
                ? "TX: " + RCController.getControllerName()
                : "TX: NONE";
            graphics.drawString(font, ctrlStr, margin, height - margin - lineHeight * 2, color);
            net.minecraft.world.phys.Vec3 v = drone.getDeltaMovement();
            double speed = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
            String speedStr = String.format("SPD: %.1f m/s", speed);
            graphics.drawString(font, speedStr, margin, height - margin - lineHeight, color);
        }

        if (!drone.getRadioState().motorsArmed()) {
            renderArmWarning(graphics, width, height);
        }

        // Attitude via quaternion slerp (same path as the camera) — per-axis Euler
        // lerps jerk near pitch ±90 where yaw/roll flip by 180°.
        org.joml.Quaternionf q = new org.joml.Quaternionf(
                DroneEntity.attitudeQuat(drone.yRotO, drone.xRotO, drone.getPrevRoll()))
            .slerp(DroneEntity.attitudeQuat(drone.getYRot(), drone.getXRot(), drone.getRoll()), partialTick);
        float[] att = DroneEntity.decomposeAttitude(q);
        float yaw   = att[0];
        float pitch = att[1];
        float roll  = att[2];

        if (!heavy) {
            if (org.restor.fpv.Config.HUD_ROLL_LINE.get())    renderRollLine(graphics, width, height, roll);
            if (org.restor.fpv.Config.HUD_PITCH_LADDER.get()) renderPitchLadder(graphics, width, height, pitch);
        }

        // Heading (0 = north)
        if (org.restor.fpv.Config.HUD_HEADING.get()) {
            int hdg = Math.floorMod(Math.round(yaw) + 180, 360);
            String hdgStr = String.format("HDG %03d", hdg);
            graphics.drawString(font, hdgStr, width / 2 - font.width(hdgStr) / 2,
                (int) (height * 0.06f), color);
        }

        // Throttle bar (right side) — predicted input: instant, no server round-trip
        if (org.restor.fpv.Config.HUD_THROTTLE_BAR.get()) {
            float throttle = drone.getPredThrottle();
            int barWidth = 6;
            int barHeight = (int) (height * 0.3);
            int barX = width - margin - barWidth;
            int barY = height / 2 - barHeight / 2;

            fillRect(graphics, barX, barY, barWidth, barHeight, 0x88000000);
            if (heavy) {
                int mid = barY + barHeight / 2;
                float cmd = throttle * 2f - 1f;
                int h = Math.round(Math.abs(cmd) * (barHeight / 2f));
                fillRect(graphics, barX, cmd >= 0 ? mid - h : mid, barWidth, Math.max(1, h), 0xFF00FF00);
                fillRect(graphics, barX - 2, mid, barWidth + 4, 1, color);

                graphics.drawString(font, "V/S", barX - 2, barY - lineHeight, color);
                float vs = (float) drone.getDeltaMovement().y * 20f;
                String vsStr = String.format("%+.1f", vs);
                graphics.drawString(font, vsStr, barX + barWidth - font.width(vsStr), barY + barHeight + 3, color);
            } else {
                int fillH = (int) (throttle * barHeight);
                int fillColor = throttle > 0.9f ? 0xFFFF0000 : 0xFF00FF00;
                fillRect(graphics, barX, barY + barHeight - fillH, barWidth, fillH, fillColor);

                graphics.drawString(font, "THR", barX - 2, barY - lineHeight, color);
                String thrPct = String.format("%.0f%%", throttle * 100);
                graphics.drawString(font, thrPct, barX + barWidth - font.width(thrPct), barY + barHeight + 3, color);
            }
        }

        if (org.restor.fpv.Config.HUD_CROSSHAIR.get()) renderCrosshair(graphics, width, height);
        if (org.restor.fpv.Config.HUD_STICKS.get() && !heavy)
            renderStickBoxes(graphics, width, height, drone.getPredYaw(),
                drone.getPredThrottle(), drone.getPredRoll(), drone.getPredPitch());
        if (org.restor.fpv.Config.HUD_DAMAGE.get()) renderDamageBadges(graphics, width, height, drone);

        renderRaceHud(graphics, width, height);
    }

    // ------------------------------------------------------------------
    //  Component damage badges (bottom right)
    // ------------------------------------------------------------------

    private static final int DAMAGE_OK = 0xFFB0B060;
    private static final int DAMAGE_WARN = 0xFFFFAA20;
    private static final int DAMAGE_CRIT = 0xFFFF3030;

    private static final int ICON_PROP = 0;
    private static final int ICON_MOTOR = 1;
    private static final int ICON_FRAME = 2;
    private static final int ICON_CAMERA = 3;
    private static final int ICON_BATTERY = 4;

    // Worn parts show up as icon + remaining condition, stacked upward from the bottom
    // right so they never collide with the telemetry column or the throttle bar.
    private static void renderDamageBadges(GuiGraphics graphics, int width, int height, DroneEntity drone) {
        if (!(drone instanceof org.restor.fpv.assembly.ModularDroneEntity modular)) return;
        org.restor.fpv.assembly.DroneAssembly assembly = modular.getAssembly();
        if (assembly == null) return;
        org.restor.fpv.assembly.FrameSpec spec = assembly.frameSpec();
        int motorCount = spec != null ? spec.motorCount() : 0;

        float propWorst = 1f, motorWorst = 1f;
        int propCount = 0, motorCount2 = 0;
        for (int i = 0; i < motorCount; i++) {
            if (!assembly.props[i].isEmpty()) {
                float r = org.restor.fpv.assembly.ComponentCondition.ratio(assembly.props[i]);
                propWorst = Math.min(propWorst, r);
                if (r < 0.95f) propCount++;
            }
            if (!assembly.motors[i].isEmpty()) {
                float r = org.restor.fpv.assembly.ComponentCondition.ratio(assembly.motors[i]);
                motorWorst = Math.min(motorWorst, r);
                if (r < 0.95f) motorCount2++;
            }
        }

        int margin = 10;
        int icon = 12;
        int gap = 4;
        int x = width - margin - icon;
        int y = height - margin - icon;
        boolean blink = MC.level == null || (MC.level.getGameTime() % 12) < 7;

        y = damageBadge(graphics, x, y, icon, gap, blink, ICON_BATTERY, "BATT",
            ratioOf(assembly.battery), 0);
        y = damageBadge(graphics, x, y, icon, gap, blink, ICON_CAMERA, "CAM",
            ratioOf(assembly.camera), 0);
        y = damageBadge(graphics, x, y, icon, gap, blink, ICON_FRAME, "FRAME",
            ratioOf(assembly.frame), 0);
        y = damageBadge(graphics, x, y, icon, gap, blink, ICON_MOTOR, "MOTOR",
            motorWorst, motorCount2);
        damageBadge(graphics, x, y, icon, gap, blink, ICON_PROP, "PROP",
            propWorst, propCount);
    }

    private static float ratioOf(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? 1f : org.restor.fpv.assembly.ComponentCondition.ratio(stack);
    }

    // Returns the Y for the next badge above this one (unchanged when nothing is drawn).
    private static int damageBadge(GuiGraphics graphics, int x, int y, int size, int gap,
                                   boolean blink, int iconId, String label, float ratio, int count) {
        if (ratio >= 0.95f) return y;
        int color = ratio < 0.30f ? DAMAGE_CRIT : ratio < 0.60f ? DAMAGE_WARN : DAMAGE_OK;
        if (ratio < 0.30f && !blink) color = 0x60FF3030;

        drawDamageIcon(graphics, x, y, iconId, color);

        Font font = MC.font;
        String text = count > 1
            ? String.format("%s x%d %d%%", label, count, Math.round(ratio * 100f))
            : String.format("%s %d%%", label, Math.round(ratio * 100f));
        graphics.drawString(font, text, x - 4 - font.width(text),
            y + (size - font.lineHeight) / 2, color);
        return y - size - gap;
    }

    private static void drawDamageIcon(GuiGraphics graphics, int x, int y, int iconId, int color) {
        switch (iconId) {
            case ICON_PROP -> {
                fillRect(graphics, x, y + 5, 12, 2, color);
                fillRect(graphics, x + 1, y + 4, 3, 1, color);
                fillRect(graphics, x + 8, y + 7, 3, 1, color);
                fillRect(graphics, x + 5, y + 4, 2, 4, color);
            }
            case ICON_MOTOR -> {
                fillRect(graphics, x + 5, y, 2, 3, color);
                fillRect(graphics, x + 3, y + 3, 6, 8, color);
                fillRect(graphics, x + 1, y + 9, 10, 2, color);
            }
            case ICON_FRAME -> {
                fillRect(graphics, x + 3, y + 3, 6, 6, color);
                fillRect(graphics, x, y, 3, 2, color);
                fillRect(graphics, x + 9, y, 3, 2, color);
                fillRect(graphics, x, y + 10, 3, 2, color);
                fillRect(graphics, x + 9, y + 10, 3, 2, color);
            }
            case ICON_CAMERA -> {
                fillRect(graphics, x, y + 3, 9, 7, color);
                fillRect(graphics, x + 9, y + 5, 3, 3, color);
                fillRect(graphics, x + 2, y + 1, 4, 2, color);
            }
            case ICON_BATTERY -> {
                fillRect(graphics, x, y + 3, 10, 2, color);
                fillRect(graphics, x, y + 9, 10, 2, color);
                fillRect(graphics, x, y + 3, 2, 8, color);
                fillRect(graphics, x + 8, y + 3, 2, 8, color);
                fillRect(graphics, x + 10, y + 5, 2, 4, color);
            }
            default -> fillRect(graphics, x, y, 12, 12, color);
        }
    }

    private static void renderArmWarning(GuiGraphics graphics, int width, int height) {
        String key = org.restor.fpv.client.ModKeyBindings.ARM_SWITCH
            .getTranslatedKeyMessage().getString();
        String message = net.minecraft.network.chat.Component
            .translatable("hud.fpv.arm_warning", key).getString();
        boolean bright = MC.level == null || (MC.level.getGameTime() / 8L & 1L) == 0L;
        int color = bright ? 0xFFFF3030 : 0x88FF3030;
        int textWidth = MC.font.width(message);
        int x = width / 2;
        int y = height / 2 - 34;
        graphics.fill(x - textWidth / 2 - 6, y - 4, x + textWidth / 2 + 6,
            y + MC.font.lineHeight + 4, bright ? 0xB0000000 : 0x60000000);
        graphics.drawCenteredString(MC.font, message, x, y, color);
    }

    private static void renderPlaneOSD(GuiGraphics graphics, int width, int height, PlaneEntity plane, float partialTick) {
        Font font = MC.font;
        int color = 0xFF00FF00;
        int lineHeight = font.lineHeight + 2;
        int margin = 10;

        int topY = margin;
        if (org.restor.fpv.Config.HUD_HINTS.get()) {
            topY = renderKeyHints(graphics, margin, topY, new net.minecraft.client.KeyMapping[]{
                org.restor.fpv.client.ModKeyBindings.MOUSE_MODE,
                org.restor.fpv.client.ModKeyBindings.PLANE_RESET_CAM,
                org.restor.fpv.client.ModKeyBindings.PLANE_ORBIT,
                org.restor.fpv.client.ModKeyBindings.PLANE_CALL_STRIKE,
                org.restor.fpv.client.ModKeyBindings.OPEN_SETTINGS,
                org.restor.fpv.client.ModKeyBindings.TOGGLE_HUD,
            }, bindHint(MC.options.keyAttack, "hud.fpv.hint_lock"),
               bindHint(MC.options.keyShift, "hud.fpv.hint_exit"));
        }

        if (org.restor.fpv.Config.HUD_TELEMETRY.get()) {
            String armed = "ARM: " + (plane.isArmed() ? "ARMED" : "DISARMED");
            graphics.drawString(font, armed, margin, topY, plane.isArmed() ? 0xFF00FF00 : 0xFFFF0000);

            int surfaceY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                plane.blockPosition().getX(), plane.blockPosition().getZ());
            String altStr = String.format("ALT: %.0f m", Math.max(0, plane.getY() - surfaceY));
            graphics.drawString(font, altStr, margin, topY + lineHeight, color);

            String camStr = String.format("CAM: %.0f°", plane.getGimbalPitch());
            graphics.drawString(font, camStr, margin, topY + lineHeight * 2, color);

            boolean locked = org.restor.fpv.client.ClientEventHandler.hasPlaneLock();
            String trkStr = plane.isOrbiting() ? "TRK: ORBIT" : locked ? "TRK: LOCK" : "TRK: FREE";
            graphics.drawString(font, trkStr, margin, topY + lineHeight * 3,
                plane.isOrbiting() ? 0xFF55FFFF : locked ? 0xFFFFFF55 : color);

            String ctrlStr = RCController.isConnected()
                ? "TX: " + RCController.getControllerName()
                : "TX: NONE";
            graphics.drawString(font, ctrlStr, margin, height - margin - lineHeight * 2, color);
            double speed = plane.getDeltaMovement().length() * 20;
            String speedStr = String.format("SPD: %.1f m/s", speed);
            graphics.drawString(font, speedStr, margin, height - margin - lineHeight, color);
        }

        org.joml.Quaternionf q = new org.joml.Quaternionf(
                DroneEntity.attitudeQuat(plane.yRotO, plane.xRotO, plane.getPrevRoll()))
            .slerp(DroneEntity.attitudeQuat(plane.getYRot(), plane.getXRot(), plane.getRoll()), partialTick);
        float[] att = DroneEntity.decomposeAttitude(q);
        float yaw = att[0];
        float pitch = att[1];
        float roll = att[2];

        if (org.restor.fpv.Config.HUD_ROLL_LINE.get())    renderRollLine(graphics, width, height, roll);
        if (org.restor.fpv.Config.HUD_PITCH_LADDER.get()) renderPitchLadder(graphics, width, height, pitch);

        if (org.restor.fpv.Config.HUD_HEADING.get()) {
            int hdg = Math.floorMod(Math.round(yaw) + 180, 360);
            String hdgStr = String.format("HDG %03d", hdg);
            graphics.drawString(MC.font, hdgStr, width / 2 - MC.font.width(hdgStr) / 2,
                (int) (height * 0.06f), color);
        }

        if (org.restor.fpv.Config.HUD_THROTTLE_BAR.get()) {
            float throttle = plane.getPredThrottle();
            int barWidth = 6;
            int barHeight = (int) (height * 0.3);
            int barX = width - margin - barWidth;
            int barY = height / 2 - barHeight / 2;

            fillRect(graphics, barX, barY, barWidth, barHeight, 0x88000000);
            int fillH = (int) (throttle * barHeight);
            int fillColor = throttle > 0.9f ? 0xFFFF0000 : 0xFF00FF00;
            fillRect(graphics, barX, barY + barHeight - fillH, barWidth, fillH, fillColor);

            graphics.drawString(font, "THR", barX - 2, barY - lineHeight, color);
            String thrPct = String.format("%.0f%%", throttle * 100);
            graphics.drawString(font, thrPct, barX + barWidth - font.width(thrPct), barY + barHeight + 3, color);
        }

        if (org.restor.fpv.Config.HUD_CROSSHAIR.get()) renderCrosshair(graphics, width, height);
        if (org.restor.fpv.Config.HUD_STICKS.get())
            renderStickBoxes(graphics, width, height, plane.getPredYaw(),
                plane.getPredThrottle(), plane.getPredRoll(), plane.getPredPitch());

        renderRaceHud(graphics, width, height);
    }

    // Two stick squares, Mode 2 layout: left = yaw (x) / throttle (y), right = roll (x) / pitch (y).
    private static void renderStickBoxes(GuiGraphics graphics, int width, int height,
                                         float predYaw, float predThrottle, float predRoll, float predPitch) {
        int size = 34;
        int gap = 16;
        int boxY = height - size - 8;
        int cx = width / 2;
        drawStickBox(graphics, cx - size - gap / 2, boxY, size,
            predYaw, predThrottle * 2f - 1f);
        drawStickBox(graphics, cx + gap / 2, boxY, size,
            predRoll, predPitch);
    }

    private static void drawStickBox(GuiGraphics graphics, int x, int y, int size, float sx, float sy) {
        int border = 0x88FFFFFF;
        graphics.fill(x, y, x + size, y + size, 0x55000000);
        graphics.hLine(x, x + size - 1, y, border);
        graphics.hLine(x, x + size - 1, y + size - 1, border);
        graphics.vLine(x, y, y + size - 1, border);
        graphics.vLine(x + size - 1, y, y + size - 1, border);
        graphics.hLine(x + 2, x + size - 3, y + size / 2, 0x33FFFFFF);
        graphics.vLine(x + size / 2, y + 2, y + size - 3, 0x33FFFFFF);
        int half = size / 2 - 3;
        int px = x + size / 2 + Math.round(Mth.clamp(sx, -1f, 1f) * half);
        int py = y + size / 2 - Math.round(Mth.clamp(sy, -1f, 1f) * half);
        graphics.fill(px - 2, py - 2, px + 2, py + 2, 0xFF00FF00);
    }

    // Roll indicator: a dashed horizontal line through screen center that tilts with
    // bank — dashes trace the world horizon, so it counter-rotates against the rolled
    // FPV camera and stays level with the actual horizon.
    private static void renderRollLine(GuiGraphics graphics, int width, int height, float roll) {
        int cx = width / 2;
        int cy = height / 2;
        // Negated: the FPV camera itself is rolled by +roll (event.setRoll), so the real
        // horizon appears in the image at -roll. The indicator must match the horizon, so
        // it counter-rotates against the camera roll instead of following it.
        float rad = (float) Math.toRadians(-roll);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        int half = (int) (width * 0.10f);
        int centerGap = 12; // keep the crosshair clear
        for (int t = -half; t <= half; t += 6) {
            if (Math.abs(t) < centerGap) continue;
            int x = cx + Math.round(t * cos);
            int ly = cy + Math.round(t * sin);
            graphics.fill(x - 1, ly, x + 2, ly + 1, 0xCCFFFFFF);
        }
    }

    // Vertical pitch ladder right of the crosshair: tick + label every 10°, window ±32°.
    // Displayed value is -xRot so positive = nose up.
    private static void renderPitchLadder(GuiGraphics graphics, int width, int height, float pitch) {
        Font font = MC.font;
        int cx = width / 2;
        int cy = height / 2;
        int x = cx + (int) (width * 0.13f);
        float pxPerDeg = 1.4f;
        float disp = -pitch;
        for (int a = -90; a <= 90; a += 10) {
            int ty = cy - Math.round((a - disp) * pxPerDeg);
            if (Math.abs(ty - cy) > 45) continue;
            boolean zero = a == 0;
            graphics.hLine(x, x + (zero ? 14 : 8), ty, zero ? 0xFFFFFFFF : 0x99FFFFFF);
            graphics.drawString(font, String.valueOf(a), x + 18, ty - 4, 0x99FFFFFF);
        }
        // Fixed reference arrow at screen center height
        graphics.hLine(x - 7, x - 3, cy, 0xFF00FF00);
        graphics.fill(x - 3, cy - 1, x - 1, cy + 2, 0xFF00FF00);
    }

    private static void renderCrosshair(GuiGraphics graphics, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;
        int size = 8;
        int gap = 3;
        int color = 0xAAFFFFFF;

        // Top
        graphics.vLine(cx, cy - gap - size, cy - gap, color);
        // Bottom
        graphics.vLine(cx, cy + gap, cy + gap + size, color);
        // Left
        graphics.hLine(cx - gap - size, cx - gap, cy, color);
        // Right
        graphics.hLine(cx + gap, cx + gap + size, cy, color);

        // Center dot
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xAAFFFFFF);
    }
}
