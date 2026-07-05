package org.restor.fpv.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;
import org.restor.fpv.client.screen.FPVSettingsScreen;
import org.restor.fpv.controller.RCController;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.network.CameraAnglePacket;
import org.restor.fpv.network.ControllerPacket;
import org.restor.fpv.network.CycleModePacket;
import org.restor.fpv.network.ModNetworking;
import org.restor.fpv.network.PlaneControllerPacket;
import org.restor.fpv.render.FPVOverlay;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static int scanCooldown;
    private static boolean prevCycleKey;
    private static int keyboardControlTicks;
    private static boolean keyboardInitialized;
    private static float keyboardThrottle;
    private static float lastPlayerYaw;
    private static float lastPlayerPitch;
    private static float filteredPitch;
    private static float filteredRoll;
    private static float filteredYaw;
    private static float filteredCtrlPitch;
    private static float filteredCtrlRoll;
    private static float filteredCtrlYaw;
    private static float lastLogThrottle, lastLogPitch, lastLogRoll, lastLogYaw;
    private static boolean lastLogArm, lastLogMode;
    private static int rcLogTick;
    private static boolean lastConnectedState;
    private static int rcIdleTicks;
    private static int ticksSinceKeyboard = 1000;

    // Plane input state (kept separate from the drone's so switching vehicles mid-session
    // doesn't carry stale filter/throttle state between the two).
    private static boolean planeInitialized;
    private static boolean planeKeyboardInitialized;
    private static float planeThrottle;
    private static float lastPlaneYaw;
    private static float lastPlanePitch;
    private static float filteredPlaneRoll;
    private static float filteredPlanePitch;
    private static float filteredPlaneCtrlPitch;
    private static float filteredPlaneCtrlRoll;
    private static float filteredPlaneCtrlYaw;
    private static boolean planeMouseLookMode;
    private static float planeGimbalYaw;
    private static float planeGimbalPitch;
    private static int planeRcLogTick;
    // Belly-camera target lock: aim at a block + LMB to lock the gimbal onto it.
    private static net.minecraft.world.phys.Vec3 planeLockTarget;
    private static boolean prevAttackKey;
    // Orbit autopilot around the locked/crosshair point.
    private static boolean planeOrbitActive;
    // FPV camera zoom (mouse wheel), applied as an FOV divisor.
    private static float fpvZoom = 1f;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (MC.player == null || MC.level == null) return;

        // Scan for controller periodically (always, even while riding)
        if (scanCooldown <= 0) {
            RCController.scan();
            scanCooldown = 40;
        }
        if (scanCooldown > 0) scanCooldown--;

        // Log controller connection/disconnection events
        boolean nowConnected = RCController.isConnected();
        if (nowConnected != lastConnectedState) {
            lastConnectedState = nowConnected;
            if (nowConnected) {
                // One-time migration v1: fix invertYaw for RC transmitters.
                if (RCController.isRCTransmitter() && !Config.RC_TX_DEFAULTS_APPLIED.get()) {
                    Config.RC_INVERT_YAW.set(true);
                    Config.RC_TX_DEFAULTS_APPLIED.set(true);
                    Config.save();
                    LOGGER.info("[RC-MIGRATE] v1: invertYaw=true");
                }
                // One-time migration v2: v1 incorrectly set invertPitch=true; revert it.
                if (RCController.isRCTransmitter() && !Config.RC_TX_DEFAULTS_V2.get()) {
                    Config.RC_INVERT_PITCH.set(false);
                    Config.RC_TX_DEFAULTS_V2.set(true);
                    Config.save();
                    LOGGER.info("[RC-MIGRATE] v2: invertPitch=false");
                }
                // One-time migration v3: throttleMid was shipped as 0.4 which made hover range
                // MORE sensitive. Reset to 0.5 (linear) which matches pre-curve behavior.
                if (!Config.RC_TX_DEFAULTS_V3.get()) {
                    Config.RC_THROTTLE_MID.set(0.5);
                    Config.RC_TX_DEFAULTS_V3.set(true);
                    Config.save();
                    LOGGER.info("[RC-MIGRATE] v3: throttleMid=0.5 (linear)");
                }
                // One-time migration v4: chunkRadius now drives client chunk streaming;
                // the old default of 2 is far too small to see ahead while flying.
                if (!Config.RC_TX_DEFAULTS_V4.get()) {
                    Config.DEV_CHUNK_RADIUS.set(8);
                    Config.RC_TX_DEFAULTS_V4.set(true);
                    Config.save();
                    LOGGER.info("[RC-MIGRATE] v4: chunkRadius=8 (streaming)");
                }
                LOGGER.info("[RC-CONNECT] Controller: \"{}\" transmitter={} axes: thr={} pitch={} roll={} yaw={}",
                    RCController.getControllerName(),
                    RCController.isRCTransmitter(),
                    Config.RC_AXIS_THROTTLE.get(),
                    Config.RC_AXIS_PITCH.get(),
                    Config.RC_AXIS_ROLL.get(),
                    Config.RC_AXIS_YAW.get());
                LOGGER.info("[RC-CONNECT] invertPitch={} invertRoll={} invertYaw={} invertThrottle={}",
                    Config.RC_INVERT_PITCH.get(),
                    Config.RC_INVERT_ROLL.get(),
                    Config.RC_INVERT_YAW.get(),
                    Config.RC_INVERT_THROTTLE.get());
            } else {
                LOGGER.info("[RC-CONNECT] Controller disconnected");
            }
        }

        // Riding a drone, or flying one remotely from a control station (the server
        // set our camera entity to the drone) — same input pipeline for both.
        DroneEntity drone = null;
        if (MC.player.getVehicle() instanceof DroneEntity d) drone = d;
        else if (MC.getCameraEntity() instanceof DroneEntity d) drone = d;

        if (drone != null) {
            // Remote piloting (camera-only, not riding): LocalPlayer.tick() never sends
            // movement packets while camera != self, so Minecraft.player's own position
            // never changes. LevelRenderer.setupRender() re-centers the render-chunk view
            // area off minecraft.player's position (not the camera entity's) — without
            // this the view area stays frozen at the station and new terrain never appears
            // as the drone flies away, even though chunk data is loaded server-side.
            if (MC.player.getVehicle() == null) {
                MC.player.setPos(drone.getX(), drone.getY(), drone.getZ());
            }

            if (MC.options.getCameraType() != CameraType.FIRST_PERSON) {
                MC.options.setCameraType(CameraType.FIRST_PERSON);
            }

            if (ModKeyBindings.OPEN_SETTINGS.consumeClick()) {
                MC.setScreen(new FPVSettingsScreen(null));
                return;
            }

            if (ModKeyBindings.TOGGLE_HUD.consumeClick()) {
                FPVOverlay.toggleHud();
            }

            if (ModKeyBindings.CAMERA_UP.consumeClick()) {
                ModNetworking.CHANNEL.sendToServer(new CameraAnglePacket(drone.getId(), 5f));
            }
            if (ModKeyBindings.CAMERA_DOWN.consumeClick()) {
                ModNetworking.CHANNEL.sendToServer(new CameraAnglePacket(drone.getId(), -5f));
            }

            if (hasKeyboardKeyInput()) {
                keyboardControlTicks = 20;
                ticksSinceKeyboard = 0;
            } else if (ticksSinceKeyboard < 1000) {
                ticksSinceKeyboard++;
            }

            if (keyboardControlTicks > 0) {
                sendKeyboardMouseInput(drone);
                keyboardControlTicks--;
            } else if (!sendControllerInput(drone)) {
                sendKeyboardMouseInput(drone);
            } else {
                keyboardInitialized = false;
            }

            boolean cycleDown = ModKeyBindings.CYCLE_MODE.isDown();
            if (cycleDown && !prevCycleKey) {
                ModNetworking.CHANNEL.sendToServer(new CycleModePacket(drone.getId()));
            }
            prevCycleKey = cycleDown;
            return;
        }

        // Planes are riding-only (no remote control station).
        PlaneEntity plane = MC.player.getVehicle() instanceof PlaneEntity p ? p : null;
        if (plane == null) {
            planeInitialized = false;
            planeLockTarget = null;
            planeOrbitActive = false;
            return;
        }

        if (MC.options.getCameraType() != CameraType.FIRST_PERSON) {
            MC.options.setCameraType(CameraType.FIRST_PERSON);
        }

        if (ModKeyBindings.OPEN_SETTINGS.consumeClick()) {
            MC.setScreen(new FPVSettingsScreen(null));
            return;
        }
        if (ModKeyBindings.TOGGLE_HUD.consumeClick()) {
            FPVOverlay.toggleHud();
        }

        boolean rcConnected = RCController.isConnected();
        if (!rcConnected && ModKeyBindings.PLANE_MOUSE_MODE.consumeClick()) {
            planeMouseLookMode = !planeMouseLookMode;
        }
        if (ModKeyBindings.PLANE_RESET_CAM.consumeClick()) {
            planeGimbalYaw = 0f;
            planeGimbalPitch = 0f;
            planeLockTarget = null;
            plane.setGimbal(0f, 0f);
        }

        // LMB toggles a camera lock onto the block exactly under the crosshair.
        boolean atk = MC.options.keyAttack.isDown();
        if (atk && !prevAttackKey) {
            planeLockTarget = (planeLockTarget != null) ? null
                : raycastFromCrosshair(plane, planeGimbalYaw, planeGimbalPitch);
        }
        prevAttackKey = atk;

        // Orbit autopilot: circle the locked point (or the crosshair block) at the current radius.
        if (ModKeyBindings.PLANE_ORBIT.consumeClick()) {
            if (planeOrbitActive) {
                planeOrbitActive = false;
                ModNetworking.CHANNEL.sendToServer(new org.restor.fpv.network.PlaneOrbitPacket(
                    plane.getId(), false, 0, 0, 0, 0));
            } else {
                net.minecraft.world.phys.Vec3 center = planeLockTarget != null ? planeLockTarget
                    : raycastFromCrosshair(plane, planeGimbalYaw, planeGimbalPitch);
                if (center != null) {
                    planeLockTarget = center; // keep the camera on the orbit centre
                    double dx = plane.getX() - center.x, dz = plane.getZ() - center.z;
                    double radius = Math.max(12.0, Math.sqrt(dx * dx + dz * dz));
                    planeOrbitActive = true;
                    ModNetworking.CHANNEL.sendToServer(new org.restor.fpv.network.PlaneOrbitPacket(
                        plane.getId(), true, center.x, center.y, center.z, radius));
                } else {
                    MC.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("msg.fpv.orbit_no_target"), true);
                }
            }
        }
        // RC connected: mouse always drives the gimbal. RC disconnected: mouse flies
        // by default, and "C" swaps it over to the gimbal (flight moves to keyboard).
        boolean gimbalMode = rcConnected || planeMouseLookMode;

        if (!planeInitialized) {
            lastPlaneYaw = MC.player.getYRot();
            lastPlanePitch = MC.player.getXRot();
            planeInitialized = true;
        }
        float yawDeltaP = normalizeDegrees(MC.player.getYRot() - lastPlaneYaw);
        float pitchDeltaP = MC.player.getXRot() - lastPlanePitch;
        lastPlaneYaw = MC.player.getYRot();
        lastPlanePitch = MC.player.getXRot();

        if (planeLockTarget != null) {
            float[] g = gimbalForTarget(plane, planeLockTarget);
            planeGimbalYaw = normalizeDegrees(g[0]);
            planeGimbalPitch = clamp(g[1], PlaneEntity.GIMBAL_PITCH_MIN, PlaneEntity.GIMBAL_PITCH_MAX);
        } else if (gimbalMode) {
            // Full 360° pan (yaw wraps), free look-down; only up is capped (belly mount).
            planeGimbalYaw = normalizeDegrees(planeGimbalYaw + yawDeltaP);
            planeGimbalPitch = clamp(planeGimbalPitch - pitchDeltaP,
                PlaneEntity.GIMBAL_PITCH_MIN, PlaneEntity.GIMBAL_PITCH_MAX);
        } else {
            planeGimbalYaw = 0f;
            planeGimbalPitch = 0f;
        }
        plane.setGimbal(planeGimbalYaw, planeGimbalPitch);

        if (planeOrbitActive) {
            // Autopilot flies the plane server-side; don't send stick inputs (the client
            // just follows the synced attitude/position while the camera stays locked).
            planeKeyboardInitialized = false;
        } else if (!rcConnected || !sendPlaneControllerInput(plane)) {
            sendPlaneKeyboardInput(plane, gimbalMode, yawDeltaP, pitchDeltaP);
        } else {
            planeKeyboardInitialized = false;
        }
    }

    private static boolean sendControllerInput(DroneEntity drone) {
        if (!RCController.isConnected()) return false;

        float[] axes = RCController.readAxes();
        boolean[] buttons = RCController.readButtons();

        if (axes == null) return false;

        float throttle = axes[RCController.AXIS_THROTTLE];
        float pitch = axes[RCController.AXIS_PITCH];
        float roll = axes[RCController.AXIS_ROLL];
        float yaw = axes[RCController.AXIS_YAW];

        // alpha=0.85: fast response (~8ms lag at 20Hz), removes single-tick RC noise spikes.
        filteredCtrlPitch += (pitch - filteredCtrlPitch) * 0.85f;
        filteredCtrlRoll  += (roll  - filteredCtrlRoll)  * 0.85f;
        filteredCtrlYaw   += (yaw   - filteredCtrlYaw)   * 0.75f;
        pitch = applyDeadband(filteredCtrlPitch, 0.03f);
        roll  = applyDeadband(filteredCtrlRoll,  0.03f);
        yaw   = applyDeadband(filteredCtrlYaw,   0.06f);

        // A connected but untouched radio (all sticks idle) must not hijack control from
        // the keyboard — its zero throttle would cut the motors 1s after the last keypress.
        boolean sticksIdle = throttle < 0.02f && Math.abs(pitch) < 0.02f
            && Math.abs(roll) < 0.02f && Math.abs(yaw) < 0.02f;
        rcIdleTicks = sticksIdle ? rcIdleTicks + 1 : 0;
        if (rcIdleTicks > 40 && ticksSinceKeyboard < 200) return false;

        boolean arm = false;
        boolean mode = false;
        if (buttons != null) {
            arm = buttons[0] || throttle > 0.10f;
            // buttons[1] is ignored for mode-switching: on many RCs it is a latching switch
            // that stays pressed, which would immediately switch ACRO→ANGLE on mount.
            // Use the keyboard cycle key (default: M) to change flight mode instead.
        } else {
            arm = throttle > 0.10f;
        }

        // Periodic log: once per second — always shows raw axes and mapped values.
        // Also logs on significant change so fast movements are captured.
        boolean logChanged = Math.abs(throttle - lastLogThrottle) > 0.05f
            || Math.abs(pitch - lastLogPitch) > 0.05f
            || Math.abs(roll - lastLogRoll) > 0.05f
            || Math.abs(yaw - lastLogYaw) > 0.05f
            || arm != lastLogArm;
        rcLogTick++;
        if (logChanged || rcLogTick >= 20) {
            rcLogTick = 0;
            float[] rawAxes = RCController.readRawAxes();
            String rawStr = rawAxes != null ? java.util.Arrays.toString(rawAxes) : "null";
            LOGGER.info("[RC-RAW] all_axes={}", rawStr);
            LOGGER.info("[RC-MAPPED] thr={} pitch={} roll={} yaw={} arm={} transmitter={}",
                String.format("%.3f", throttle),
                String.format("%.3f", pitch),
                String.format("%.3f", roll),
                String.format("%.3f", yaw),
                arm,
                RCController.isRCTransmitter());
            lastLogThrottle = throttle;
            lastLogPitch = pitch;
            lastLogRoll = roll;
            lastLogYaw = yaw;
            lastLogArm = arm;
            lastLogMode = mode;
        }

        // Always send controller input to maintain controller mode
        // (prevents fallback to keyboard input which causes yaw feedback loops)
        drone.setClientPredictedInputs(throttle, pitch, roll, yaw);
        ModNetworking.CHANNEL.sendToServer(new ControllerPacket(
            drone.getId(), throttle, pitch, roll, yaw, arm, mode
        ));
        return true;
    }

    private static void sendKeyboardMouseInput(DroneEntity drone) {
        if (!keyboardInitialized) {
            keyboardThrottle = drone.getVisualThrottle();
            lastPlayerYaw = MC.player.getYRot();
            lastPlayerPitch = MC.player.getXRot();
            filteredPitch = 0f;
            filteredRoll = 0f;
            filteredYaw = 0f;
            keyboardInitialized = true;
        }

        float yawDelta = normalizeDegrees(MC.player.getYRot() - lastPlayerYaw);
        float pitchDelta = MC.player.getXRot() - lastPlayerPitch;
        lastPlayerYaw = MC.player.getYRot();
        lastPlayerPitch = MC.player.getXRot();

        // 0.15/tick = 3.0/sec → full throttle range in ~7 ticks (0.35s), real FPV feel.
        // Passive decay is near-zero so releasing W holds the throttle position.
        if (MC.options.keyUp.isDown()) {
            keyboardThrottle = Math.min(1.0f, keyboardThrottle + 0.15f);
        } else if (MC.options.keyDown.isDown()) {
            keyboardThrottle = Math.max(0.0f, keyboardThrottle - 0.15f);
        } else {
            keyboardThrottle = Math.max(0.0f, keyboardThrottle - 0.003f);
        }

        // Mouse X → yaw (turn like normal Minecraft look — this is what players expect;
        // mapping mouse X to roll made the view bank without turning, which read as
        // "camera turns but the drone doesn't"). Mouse Y → pitch. A/D → roll (bank).
        float rollTarget = 0f;
        float rollSensitivity = Config.KEYBOARD_YAW_SENSITIVITY.get().floatValue();
        if (MC.options.keyLeft.isDown()) rollTarget -= rollSensitivity;
        if (MC.options.keyRight.isDown()) rollTarget += rollSensitivity;
        filteredRoll += (rollTarget - filteredRoll) * 0.18f;
        float roll = applyDeadband(filteredRoll, 0.01f);

        float rawPitch = clamp(-pitchDelta * Config.MOUSE_PITCH_SENSITIVITY.get().floatValue(), -1f, 1f);
        float rawYaw = clamp(yawDelta * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue(), -1f, 1f);
        float smoothing = Config.MOUSE_SMOOTHING.get().floatValue();
        filteredPitch = filteredPitch * smoothing + rawPitch * (1f - smoothing);
        filteredYaw = filteredYaw * smoothing + rawYaw * (1f - smoothing);
        float deadband = Config.MOUSE_DEADBAND.get().floatValue();
        float pitch = applyDeadband(filteredPitch, deadband);
        float yaw = applyDeadband(filteredYaw, deadband);
        boolean arm = keyboardThrottle > 0.05f;

        drone.setClientPredictedInputs(keyboardThrottle, pitch, roll, yaw);
        ModNetworking.CHANNEL.sendToServer(new ControllerPacket(
            drone.getId(), keyboardThrottle, pitch, roll, yaw, arm, false
        ));
    }

    private static boolean sendPlaneControllerInput(PlaneEntity plane) {
        float[] axes = RCController.readAxes();
        boolean[] buttons = RCController.readButtons();
        if (axes == null) return false;

        float throttle = axes[RCController.AXIS_THROTTLE];
        float pitch = axes[RCController.AXIS_PITCH];
        float roll = axes[RCController.AXIS_ROLL];
        float yaw = axes[RCController.AXIS_YAW];

        filteredPlaneCtrlPitch += (pitch - filteredPlaneCtrlPitch) * 0.85f;
        filteredPlaneCtrlRoll  += (roll  - filteredPlaneCtrlRoll)  * 0.85f;
        filteredPlaneCtrlYaw   += (yaw   - filteredPlaneCtrlYaw)   * 0.75f;
        pitch = applyDeadband(filteredPlaneCtrlPitch, 0.03f);
        roll  = applyDeadband(filteredPlaneCtrlRoll,  0.03f);
        yaw   = applyDeadband(filteredPlaneCtrlYaw,   0.06f);

        boolean arm = buttons != null ? (buttons[0] || throttle > 0.10f) : throttle > 0.10f;

        planeRcLogTick++;
        if (planeRcLogTick >= 20) {
            planeRcLogTick = 0;
            LOGGER.info("[PLANE-RC] id={} thr={} pitch={} roll={} yaw={} arm={} buttons={}",
                plane.getId(),
                String.format("%.3f", throttle),
                String.format("%.3f", pitch),
                String.format("%.3f", roll),
                String.format("%.3f", yaw),
                arm,
                buttons != null ? java.util.Arrays.toString(buttons) : "null");
        }

        plane.setClientPredictedInputs(throttle, pitch, roll, yaw);
        ModNetworking.CHANNEL.sendToServer(new PlaneControllerPacket(
            plane.getId(), throttle, pitch, roll, yaw, arm
        ));
        return true;
    }

    private static void sendPlaneKeyboardInput(PlaneEntity plane, boolean gimbalMode,
                                               float yawDeltaP, float pitchDeltaP) {
        if (!planeKeyboardInitialized) {
            planeThrottle = plane.getVisualThrottle();
            filteredPlaneRoll = 0f;
            filteredPlanePitch = 0f;
            planeKeyboardInitialized = true;
        }

        if (MC.options.keyUp.isDown()) {
            planeThrottle = Math.min(1.0f, planeThrottle + 0.15f);
        } else if (MC.options.keyDown.isDown()) {
            planeThrottle = Math.max(0.0f, planeThrottle - 0.15f);
        } else {
            planeThrottle = Math.max(0.0f, planeThrottle - 0.003f);
        }

        float roll, pitch;
        if (gimbalMode) {
            // Mouse is busy panning the camera — roll/pitch fall back to keyboard.
            float rollTarget = 0f;
            if (MC.options.keyLeft.isDown()) rollTarget -= 1f;
            if (MC.options.keyRight.isDown()) rollTarget += 1f;
            filteredPlaneRoll += (rollTarget - filteredPlaneRoll) * 0.18f;
            roll = filteredPlaneRoll;

            float pitchTarget = 0f;
            if (MC.options.keyJump.isDown()) pitchTarget -= 1f;
            if (MC.options.keyShift.isDown()) pitchTarget += 1f;
            filteredPlanePitch += (pitchTarget - filteredPlanePitch) * 0.18f;
            pitch = filteredPlanePitch;
        } else {
            // Mouse flies directly: X → roll, Y → pitch.
            float rawRoll = clamp(yawDeltaP * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue(), -1f, 1f);
            float rawPitch = clamp(-pitchDeltaP * Config.MOUSE_PITCH_SENSITIVITY.get().floatValue(), -1f, 1f);
            float smoothing = Config.MOUSE_SMOOTHING.get().floatValue();
            filteredPlaneRoll = filteredPlaneRoll * smoothing + rawRoll * (1f - smoothing);
            filteredPlanePitch = filteredPlanePitch * smoothing + rawPitch * (1f - smoothing);
            float deadband = Config.MOUSE_DEADBAND.get().floatValue();
            roll = applyDeadband(filteredPlaneRoll, deadband);
            pitch = applyDeadband(filteredPlanePitch, deadband);
        }

        boolean arm = planeThrottle > 0.05f;
        plane.setClientPredictedInputs(planeThrottle, pitch, roll, 0f);
        ModNetworking.CHANNEL.sendToServer(new PlaneControllerPacket(
            plane.getId(), planeThrottle, pitch, roll, 0f, arm
        ));
    }

    private static boolean hasKeyboardKeyInput() {
        return MC.options.keyUp.isDown() || MC.options.keyDown.isDown()
            || MC.options.keyLeft.isDown() || MC.options.keyRight.isDown();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float applyDeadband(float value, float deadband) {
        return Math.abs(value) < deadband ? 0f : value;
    }

    private static float normalizeDegrees(float value) {
        while (value > 180f) value -= 360f;
        while (value < -180f) value += 360f;
        return value;
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        FPVOverlay.onCameraSetup(event);
    }

    // Whether the local player is currently in an FPV feed (riding a plane/drone or
    // remote-controlling one) — used to gate camera zoom.
    private static boolean fpvActive() {
        if (MC.player == null) return false;
        return MC.player.getVehicle() instanceof PlaneEntity
            || MC.player.getVehicle() instanceof DroneEntity
            || MC.getCameraEntity() instanceof DroneEntity;
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (MC.screen != null || !fpvActive()) return;
        fpvZoom = (float) Math.max(1.0, Math.min(8.0, fpvZoom + event.getScrollDelta() * 0.5));
        event.setCanceled(true); // don't also scroll the hotbar
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!fpvActive()) { fpvZoom = 1f; return; }
        event.setFOV(event.getFOV() / fpvZoom);
    }

    // Raycast along the belly camera's exact crosshair direction (derived from the plane
    // attitude + current gimbal, so it matches what the pilot sees) to the block it's on.
    // Long range so distant ground can be locked from altitude. Null if it hits nothing.
    private static net.minecraft.world.phys.Vec3 raycastFromCrosshair(PlaneEntity plane, float gy, float gp) {
        org.joml.Vector3f f = DroneEntity.attitudeQuat(plane.getYRot(), plane.getXRot(), plane.getRoll())
            .rotateY((float) Math.toRadians(-gy))
            .rotateX((float) Math.toRadians(-gp))
            .transform(new org.joml.Vector3f(0, 0, 1));
        net.minecraft.world.phys.Vec3 start = plane.position().add(0, plane.getBbHeight() / 2.0, 0);
        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(f.x, f.y, f.z);
        net.minecraft.world.phys.Vec3 end = start.add(dir.scale(256));
        net.minecraft.world.phys.BlockHitResult hit = MC.level.clip(new net.minecraft.world.level.ClipContext(
            start, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE, MC.player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var bp = hit.getBlockPos();
            return new net.minecraft.world.phys.Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        }
        return null;
    }

    // Gimbal yaw/pitch (degrees) that aims the belly camera at a world point, given the
    // plane's current attitude. Matches FPVOverlay's Ry(-yaw)Rx(-pitch) gimbal composition.
    private static float[] gimbalForTarget(PlaneEntity plane, net.minecraft.world.phys.Vec3 target) {
        net.minecraft.world.phys.Vec3 camPos = plane.position().add(0, plane.getBbHeight() / 2.0, 0);
        net.minecraft.world.phys.Vec3 d = target.subtract(camPos);
        if (d.lengthSqr() < 1e-6) return new float[]{planeGimbalYaw, planeGimbalPitch};
        d = d.normalize();
        org.joml.Quaternionf inv = new org.joml.Quaternionf(
            DroneEntity.attitudeQuat(plane.getYRot(), plane.getXRot(), plane.getRoll())).conjugate();
        org.joml.Vector3f dl = inv.transform(new org.joml.Vector3f((float) d.x, (float) d.y, (float) d.z));
        float gp = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, dl.y))));
        float gy = (float) Math.toDegrees(Math.atan2(-dl.x, dl.z));
        return new float[]{gy, gp};
    }

    @SubscribeEvent
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        // While remote-controlling, the pilot's body is teleported along with the
        // drone (chunk streaming) — never draw it in front of the drone camera.
        if (MC.player != null && event.getEntity() == MC.player
            && !(MC.player.getVehicle() instanceof DroneEntity)
            && MC.getCameraEntity() instanceof DroneEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        // Piloting a drone (riding or remote) shows the drone's own FPV camera feed —
        // the player's held item/arm should never appear in front of it.
        if (MC.player != null
            && (MC.player.getVehicle() instanceof DroneEntity || MC.getCameraEntity() instanceof DroneEntity
                || MC.player.getVehicle() instanceof PlaneEntity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        // While remote-controlling (camera on a drone, not riding), the player's body
        // stands at the station — WASD must fly the drone, not walk the body.
        // shiftKeyDown is deliberately kept: vanilla uses it to exit the camera.
        if (MC.player != null && !(MC.player.getVehicle() instanceof DroneEntity)
            && MC.getCameraEntity() instanceof DroneEntity) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        FPVOverlay.onRenderGuiOverlay(event);
    }

    @SubscribeEvent
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        // Render FPV OSD once per frame after the whole GUI pass. This event fires
        // unconditionally — hanging the OSD on RenderGuiOverlayEvent.Post(HOTBAR) never
        // fired because our own Pre handler cancels the hotbar overlay (canceled Pre
        // suppresses Post), so the OSD was invisible.
        FPVOverlay.renderOverlay(event.getGuiGraphics(), event.getPartialTick());
    }
}
