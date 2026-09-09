package org.restor.fpv.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.restor.fpv.compat.PacketDistributor;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;
import org.restor.fpv.client.screen.FPVSettingsScreen;
import org.restor.fpv.controller.RCController;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.network.CameraAnglePacket;
import org.restor.fpv.network.ControllerPacket;
import org.restor.fpv.network.CycleModePacket;
import org.restor.fpv.network.PlaneControllerPacket;
import org.restor.fpv.render.FPVOverlay;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static int scanCooldown;
    private static boolean prevCycleKey;
    private static boolean keyboardInitialized;
    private static boolean keyboardArmSwitch;
    private static float keyboardThrottle;
    private static float keyboardThrottleBias;
    private static float lastPlayerYaw;
    private static float lastPlayerPitch;
    private static float filteredPitch;
    private static float filteredRoll;
    private static float filteredYaw;
    private static float mouseDronePitch;
    private static float mouseDroneRoll;
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
    private static float mousePlaneRoll;
    private static float mousePlanePitch;
    private static float filteredPlaneCtrlPitch;
    private static float filteredPlaneCtrlRoll;
    private static float filteredPlaneCtrlYaw;
    private static boolean planeMouseLookMode;
    private static boolean planeRcWasConnected;
    private static float planeThrottleBias;
    private static int planeRcIdleTicks;
    private static float planeGimbalYaw;
    private static float planeGimbalPitch;
    private static int planeRcLogTick;
    // Belly-camera target lock: aim at a block + LMB to lock the gimbal onto it.
    private static net.minecraft.world.phys.Vec3 planeLockTarget;
    private static net.minecraft.world.phys.Vec3 planeRawLockTarget;
    private static int planeLockManualTicks;
    private static boolean prevAttackKey;
    // Orbit autopilot around the locked/crosshair point.
    private static boolean planeOrbitActive;
    private static net.minecraft.world.phys.Vec3 planeOrbitTarget;
    private static int planeOrbitSyncGrace;
    private static int planeTargetStreamTicks;
    private static boolean planeTargetStreamActive;
    // FPV camera zoom (mouse wheel), applied as an FOV divisor.
    private static float fpvZoom = 1f;

    // Heavy-lift drone state: WASD flies the airframe and the mouse aims the belly camera,
    // so the gimbal angles and the mouse baseline live outside the acro path.
    private static boolean heavyInitialized;
    private static float lastHeavyYaw;
    private static float droneGimbalYaw;
    private static float droneGimbalPitch;
    private static boolean heavyDroneActive;
    // Mouse owns the belly camera by default; the swap key hands it back to the airframe.
    private static boolean droneMouseLookMode = true;
    private static float heavyMouseYaw;

    private static int controlSeq;
    private static boolean prevSneakKey;

    public static int getControlSeq() { return controlSeq; }

    // handleKeybinds() runs later in the same tick, so the yaw keys have to be drained here
    // or Q drops the held item and E opens the inventory every time the drone yaws.
    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!heavyDroneActive || MC.player == null || MC.screen != null) return;
        while (MC.options.keyDrop.consumeClick()) { }
        while (MC.options.keyInventory.consumeClick()) { }
    }

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
        boolean lineOfSight = false;
        if (MC.player.getVehicle() instanceof DroneEntity d) drone = d;
        else if (MC.getCameraEntity() instanceof DroneEntity d) drone = d;
        else if (LineOfSightSession.drone() != null) { drone = LineOfSightSession.drone(); lineOfSight = true; }

        if (drone != null) {
            // Remote piloting (camera-only, not riding): LocalPlayer.tick() never sends
            // movement packets while camera != self, so Minecraft.player's own position
            // never changes. LevelRenderer.setupRender() re-centers the render-chunk view
            // area off minecraft.player's position (not the camera entity's) — without
            // this the view area stays frozen at the station and new terrain never appears
            // as the drone flies away, even though chunk data is loaded server-side.
            if (!lineOfSight) {
                if (MC.player.getVehicle() == null) {
                    MC.player.setPos(drone.getX(), drone.getY(), drone.getZ());
                }

                if (MC.options.getCameraType() != CameraType.FIRST_PERSON) {
                    MC.options.setCameraType(CameraType.FIRST_PERSON);
                }
            }

            boolean sneakDown = MC.options.keyShift.isDown();
            if (sneakDown && !prevSneakKey && (lineOfSight || MC.player.getVehicle() == drone)) {
                prevSneakKey = true;
                PacketDistributor.sendToServer(new org.restor.fpv.network.DroneDismountPacket(drone.getId()));
                if (lineOfSight) LineOfSightSession.clear();
                else MC.player.stopRiding();
                return;
            }
            prevSneakKey = sneakDown;

            if (ModKeyBindings.OPEN_SETTINGS.consumeClick()) {
                MC.setScreen(new FPVSettingsScreen(null));
                return;
            }

            if (ModKeyBindings.TOGGLE_HUD.consumeClick()) {
                FPVOverlay.toggleHud();
            }

            boolean heavy = drone.isHeavyLift();
            if (ModKeyBindings.CAMERA_UP.consumeClick()) {
                if (heavy) nudgeGimbalPitch(drone, 5f);
                else PacketDistributor.sendToServer(new CameraAnglePacket(drone.getId(), 5f));
            }
            if (ModKeyBindings.CAMERA_DOWN.consumeClick()) {
                if (heavy) nudgeGimbalPitch(drone, -5f);
                else PacketDistributor.sendToServer(new CameraAnglePacket(drone.getId(), -5f));
            }
            if (ModKeyBindings.FIRE_PAYLOAD.consumeClick()) {
                PacketDistributor.sendToServer(new org.restor.fpv.network.FirePayloadPacket(drone.getId()));
            }

            // The keyboard arm switch must remain available when an RC transmitter is
            // connected; otherwise only controller button 0 can ever arm the drone.
            if (ModKeyBindings.ARM_SWITCH.consumeClick()) {
                keyboardArmSwitch = !keyboardArmSwitch;
            }

            // Heavy lifters are flown like a camera drone: WASD is movement, throttle sits on
            // jump/sprint, yaw on its own keys, and the mouse aims the belly gimbal instead.
            heavyDroneActive = heavy;
            if (ModKeyBindings.MOUSE_MODE.consumeClick()) {
                droneMouseLookMode = !droneMouseLookMode;
                heavyMouseYaw = 0f;
            }
            if (heavy && !lineOfSight) updateDroneGimbal(drone);
            else clearDroneGimbal(drone);

            if (hasKeyboardKeyInput()) {
                ticksSinceKeyboard = 0;
            } else if (ticksSinceKeyboard < 1000) {
                ticksSinceKeyboard++;
            }

            // The controller path merges the keys it needs per axis, so a keypress never
            // takes the whole frame away from the radio (which used to drop throttle to 0).
            if (sendControllerInput(drone, heavy)) {
                keyboardInitialized = false;
                if (lineOfSight) faceDrone(drone);
            } else {
                keyboardThrottleBias = 0f;
                if (heavy) {
                    if (lineOfSight) faceDrone(drone);
                    sendHeavyKeyboardInput(drone);
                } else {
                    sendKeyboardMouseInput(drone, lineOfSight);
                }
            }

            boolean cycleDown = ModKeyBindings.CYCLE_MODE.isDown();
            if (cycleDown && !prevCycleKey) {
                PacketDistributor.sendToServer(new CycleModePacket(drone.getId()));
            }
            prevCycleKey = cycleDown;
            return;
        }
        keyboardInitialized = false;
        keyboardArmSwitch = false;
        prevSneakKey = false;
        keyboardThrottleBias = 0f;
        heavyDroneActive = false;
        heavyInitialized = false;

        // Planes are riding-only (no remote control station).
        PlaneEntity plane = MC.player.getVehicle() instanceof PlaneEntity p ? p : null;
        if (plane == null) {
            planeInitialized = false;
            planeTargetStreamActive = false;
            planeTargetStreamTicks = 0;
            clearPlaneState(null, false);
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
        if (rcConnected != planeRcWasConnected) {
            planeRcWasConnected = rcConnected;
            planeMouseLookMode = rcConnected;
        }
        if (ModKeyBindings.MOUSE_MODE.consumeClick()) {
            planeMouseLookMode = !planeMouseLookMode;
        }
        if (ModKeyBindings.PLANE_RESET_CAM.consumeClick()) {
            clearPlaneTracking(plane, true);
        }

        // LMB toggles a camera lock onto the block exactly under the crosshair.
        boolean atk = MC.options.keyAttack.isDown();
        if (atk && !prevAttackKey) {
            if (planeLockTarget != null) {
                clearPlaneTracking(plane, false);
                actionBar("msg.fpv.lock_off");
            } else {
                planeLockTarget = raycastFromCrosshair(plane, planeGimbalYaw, planeGimbalPitch);
                planeRawLockTarget = planeLockTarget;
                plane.setStabilizedGimbalView(planeLockTarget != null);
                actionBar(planeLockTarget != null ? "msg.fpv.lock_on" : "msg.fpv.lock_no_target");
            }
        }
        prevAttackKey = atk;

        if (planeOrbitSyncGrace > 0) {
            planeOrbitSyncGrace--;
        } else {
            planeOrbitActive = plane.isOrbiting();
            if (!planeOrbitActive) planeOrbitTarget = null;
        }

        if (ModKeyBindings.PLANE_CALL_STRIKE.consumeClick()
            && (plane instanceof org.restor.fpv.entity.ScoutPlaneEntity
                || plane instanceof org.restor.fpv.entity.CarrierPlaneEntity
                || plane instanceof org.restor.fpv.assembly.ModularPlaneEntity mp && mp.hasStrikeBay())) {
            net.minecraft.world.phys.Vec3 target = planeLockTarget != null ? planeLockTarget
                : raycastFromCrosshair(plane, planeGimbalYaw, planeGimbalPitch);
            if (target != null) {
                planeLockTarget = target;
                planeRawLockTarget = target;
                PacketDistributor.sendToServer(new org.restor.fpv.network.PlaneStrikePacket(
                    plane.getId(), target.x, target.y, target.z));
            } else {
                MC.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.fpv.strike_no_target"), true);
            }
        }
        // Default: mouse drives the gimbal with a radio connected, flies the wing without
        // one. "C" swaps it either way — it is no longer swallowed while a radio is plugged in.
        boolean gimbalMode = planeMouseLookMode;

        if (!planeInitialized) {
            lastPlaneYaw = MC.player.getYRot();
            lastPlanePitch = MC.player.getXRot();
            planeInitialized = true;
        }
        float yawDeltaP = normalizeDegrees(MC.player.getYRot() - lastPlaneYaw);
        float pitchDeltaP = MC.player.getXRot() - lastPlanePitch;
        lastPlaneYaw = MC.player.getYRot();
        lastPlanePitch = MC.player.getXRot();
        float zoomSensitivity = (float) Math.pow(Math.max(1f, fpvZoom), 1.7);
        yawDeltaP /= zoomSensitivity;
        pitchDeltaP /= zoomSensitivity;

        if (planeLockTarget != null) {
            boolean movingAim = Math.abs(yawDeltaP) > 0.001f || Math.abs(pitchDeltaP) > 0.001f;
            if (movingAim) {
                planeLockManualTicks = 5;
                planeGimbalYaw = normalizeDegrees(planeGimbalYaw + yawDeltaP);
                planeGimbalPitch = clamp(planeGimbalPitch - pitchDeltaP,
                    PlaneEntity.GIMBAL_PITCH_MIN, PlaneEntity.GIMBAL_PITCH_MAX);
                net.minecraft.world.phys.Vec3 newTarget = raycastFromCrosshair(plane, planeGimbalYaw, planeGimbalPitch);
                if (newTarget != null) {
                    planeRawLockTarget = newTarget;
                    planeLockTarget = smoothLockTarget(planeLockTarget, planeRawLockTarget, 0.10);
                }
            } else if (planeLockManualTicks > 0) {
                planeLockManualTicks--;
                if (planeRawLockTarget != null) {
                    planeLockTarget = smoothLockTarget(planeLockTarget, planeRawLockTarget, 0.08);
                }
            } else {
                if (planeRawLockTarget != null) {
                    planeLockTarget = smoothLockTarget(planeLockTarget, planeRawLockTarget, 0.08);
                }
                float[] g = gimbalForTarget(plane, planeLockTarget);
                planeGimbalYaw = approachDegrees(planeGimbalYaw, normalizeDegrees(g[0]), 3.5f);
                planeGimbalPitch = approach(planeGimbalPitch,
                    clamp(g[1], PlaneEntity.GIMBAL_PITCH_MIN, PlaneEntity.GIMBAL_PITCH_MAX), 3.5f);
            }
            yawDeltaP = 0f;
            pitchDeltaP = 0f;
            rebasePlaneViewPitch();
        } else if (gimbalMode) {
            // Full 360° pan (yaw wraps), free look-down; only up is capped (belly mount).
            planeGimbalYaw = normalizeDegrees(planeGimbalYaw + yawDeltaP);
            planeGimbalPitch = clamp(planeGimbalPitch - pitchDeltaP,
                PlaneEntity.GIMBAL_PITCH_MIN, PlaneEntity.GIMBAL_PITCH_MAX);
            rebasePlaneViewPitch();
        } else {
            planeGimbalYaw = 0f;
            planeGimbalPitch = 0f;
            rebasePlaneViewPitch();
        }
        plane.setGimbal(planeGimbalYaw, planeGimbalPitch);
        plane.setStabilizedGimbalView(planeLockTarget != null && planeLockManualTicks <= 0);

        // Orbit autopilot is driven by G only while tracking a point. Pressing G on the
        // same tracked point stops orbit; pressing it on a new tracked point retargets orbit.
        if (ModKeyBindings.PLANE_ORBIT.consumeClick()) {
            net.minecraft.world.phys.Vec3 orbitCandidate = planeRawLockTarget != null ? planeRawLockTarget : planeLockTarget;
            if (orbitCandidate == null) {
                if (planeOrbitActive) {
                    stopPlaneOrbit(plane, true);
                    actionBar("msg.fpv.orbit_off");
                } else {
                    actionBar("msg.fpv.orbit_no_target");
                }
            } else if (planeOrbitActive && samePoint(orbitCandidate, planeOrbitTarget)) {
                stopPlaneOrbit(plane, true);
                actionBar("msg.fpv.orbit_off");
            } else {
                planeLockTarget = orbitCandidate;
                startPlaneOrbit(plane, orbitCandidate);
                actionBar("msg.fpv.orbit_on");
            }
        }

        updatePlaneTargetStreaming(plane);

        if (planeOrbitActive) {
            // Autopilot flies the plane server-side; don't send stick inputs (the client
            // just follows the synced attitude/position while the camera stays locked).
            planeKeyboardInitialized = false;
        } else if (!rcConnected || !sendPlaneControllerInput(plane, gimbalMode, yawDeltaP, pitchDeltaP)) {
            planeThrottleBias = 0f;
            sendPlaneKeyboardInput(plane, gimbalMode, yawDeltaP, pitchDeltaP);
        } else {
            planeKeyboardInitialized = false;
        }
    }

    // The mouse owns the belly camera on a heavy rig: it pans and tilts the gimbal inside
    // its mechanical limits and never reaches the airframe's attitude.
    private static void updateDroneGimbal(DroneEntity drone) {
        if (!heavyInitialized) {
            lastHeavyYaw = MC.player.getYRot();
            droneGimbalYaw = 0f;
            droneGimbalPitch = 0f;
            heavyMouseYaw = 0f;
            heavyInitialized = true;
        }
        float zoomSensitivity = (float) Math.pow(Math.max(1f, fpvZoom), 1.7);
        float yawDelta = normalizeDegrees(MC.player.getYRot() - lastHeavyYaw) / zoomSensitivity;
        float pitchDelta = MC.player.getXRot() / zoomSensitivity;
        if (ModKeyBindings.PLANE_RESET_CAM.consumeClick()) {
            droneGimbalYaw = 0f;
            droneGimbalPitch = 0f;
            heavyMouseYaw = 0f;
        } else if (droneMouseLookMode) {
            // Full circle pan (yaw wraps) and free look-down: the whole lower hemisphere.
            droneGimbalYaw = normalizeDegrees(droneGimbalYaw + yawDelta);
            droneGimbalPitch = clamp(droneGimbalPitch - pitchDelta,
                DroneEntity.GIMBAL_PITCH_MIN, DroneEntity.GIMBAL_PITCH_MAX);
        } else {
            // Mouse flies the airframe: X steers the heading, the gimbal holds where it was.
            float ret = Config.MOUSE_RETURN.get().floatValue();
            heavyMouseYaw = clamp(heavyMouseYaw * (1f - ret)
                + yawDelta * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue() * MOUSE_RATE_SCALE, -1f, 1f);
        }
        lastHeavyYaw = MC.player.getYRot();
        MC.player.setXRot(0f);
        drone.setGimbal(droneGimbalYaw, droneGimbalPitch);
    }

    private static void nudgeGimbalPitch(DroneEntity drone, float delta) {
        droneGimbalPitch = clamp(droneGimbalPitch + delta,
            DroneEntity.GIMBAL_PITCH_MIN, DroneEntity.GIMBAL_PITCH_MAX);
        drone.setGimbal(droneGimbalYaw, droneGimbalPitch);
    }

    private static void clearDroneGimbal(DroneEntity drone) {
        heavyInitialized = false;
        droneGimbalYaw = 0f;
        droneGimbalPitch = 0f;
        heavyMouseYaw = 0f;
        drone.setGimbal(0f, 0f);
    }

    /**
     * Heavy-lift keyboard: WASD is movement (a bank angle, which is how these rigs fly),
     * climb/descend rides jump/sprint and yaw its own key pair - the mouse is busy aiming
     * the camera. The vertical stick is centred when neither key is held, so letting go
     * parks the rig at its current altitude instead of dropping it.
     */
    private static void sendHeavyKeyboardInput(DroneEntity drone) {
        if (!keyboardInitialized) {
            keyboardThrottle = HOVER_STICK;
            filteredPitch = 0f;
            filteredRoll = 0f;
            filteredYaw = 0f;
            keyboardInitialized = true;
        }

        float climbTarget = HOVER_STICK;
        if (MC.options.keyJump.isDown()) climbTarget = 1f;
        else if (MC.options.keySprint.isDown()) climbTarget = 0f;
        keyboardThrottle += (climbTarget - keyboardThrottle) * 0.35f;

        float pitchTarget = 0f;
        if (MC.options.keyUp.isDown())   pitchTarget += 1f;
        if (MC.options.keyDown.isDown()) pitchTarget -= 1f;
        float rollTarget = 0f;
        if (MC.options.keyLeft.isDown())  rollTarget -= 1f;
        if (MC.options.keyRight.isDown()) rollTarget += 1f;
        float yawTarget = heavyMouseYaw;
        if (ModKeyBindings.DRONE_YAW_LEFT.isDown())  yawTarget += 1f;
        if (ModKeyBindings.DRONE_YAW_RIGHT.isDown()) yawTarget -= 1f;
        yawTarget = clamp(yawTarget, -1f, 1f);

        filteredPitch += (pitchTarget - filteredPitch) * 0.2f;
        filteredRoll  += (rollTarget  - filteredRoll)  * 0.2f;
        filteredYaw   += (yawTarget   - filteredYaw)   * 0.25f;

        float yaw = clamp(filteredYaw * Config.KEYBOARD_YAW_SENSITIVITY.get().floatValue(), -1f, 1f);
        boolean arm = keyboardArmSwitch;

        drone.setClientPredictedInputs(keyboardThrottle, filteredPitch, filteredRoll, yaw, arm);
        PacketDistributor.sendToServer(new ControllerPacket(
            drone.getId(), keyboardThrottle, filteredPitch, filteredRoll, yaw, arm, false, ++controlSeq
        ));
    }

    private static boolean sendControllerInput(DroneEntity drone, boolean heavy) {
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

        // Keyboard stays live while a transmitter is plugged in: W/S trims throttle
        // around the stick and A/D adds yaw. Only the axes actually pressed are touched.
        // On a heavy rig W/S/A/D are movement, so the trim moves to jump/sprint.
        boolean trimUp = heavy ? MC.options.keyJump.isDown() : MC.options.keyUp.isDown();
        boolean trimDown = heavy ? MC.options.keySprint.isDown() : MC.options.keyDown.isDown();
        if (trimUp) {
            keyboardThrottleBias = Math.min(1f, keyboardThrottleBias + 0.05f);
        } else if (trimDown) {
            keyboardThrottleBias = Math.max(-1f, keyboardThrottleBias - 0.05f);
        } else if (throttle > 0.02f) {
            // The stick is flying: bleed the keyboard trim away so it can't leave a
            // permanent offset. With the stick at rest the trim is the throttle, so it stays.
            keyboardThrottleBias -= Math.signum(keyboardThrottleBias) * Math.min(0.01f, Math.abs(keyboardThrottleBias));
        }
        throttle = clamp(throttle + keyboardThrottleBias, 0f, 1f);

        float kbYaw = 0f;
        if (heavy) {
            if (ModKeyBindings.DRONE_YAW_LEFT.isDown())  kbYaw += 1f;
            if (ModKeyBindings.DRONE_YAW_RIGHT.isDown()) kbYaw -= 1f;

            float kbPitch = 0f;
            if (MC.options.keyUp.isDown())   kbPitch += 1f;
            if (MC.options.keyDown.isDown()) kbPitch -= 1f;
            float kbRoll = 0f;
            if (MC.options.keyLeft.isDown())  kbRoll -= 1f;
            if (MC.options.keyRight.isDown()) kbRoll += 1f;
            filteredPitch += (kbPitch - filteredPitch) * 0.2f;
            filteredRoll  += (kbRoll  - filteredRoll)  * 0.2f;
            pitch = clamp(pitch + filteredPitch, -1f, 1f);
            roll  = clamp(roll  + filteredRoll,  -1f, 1f);
        } else {
            if (MC.options.keyLeft.isDown()) kbYaw -= 1f;
            if (MC.options.keyRight.isDown()) kbYaw += 1f;
        }
        if (kbYaw != 0f) {
            yaw = clamp(yaw + kbYaw * Config.KEYBOARD_YAW_SENSITIVITY.get().floatValue(), -1f, 1f);
        }

        boolean arm = keyboardArmSwitch;
        boolean mode = false;
        if (buttons != null) {
            arm |= buttons.length > 0 && buttons[0];
            // buttons[1] is ignored for mode-switching: on many RCs it is a latching switch
            // that stays pressed, which would immediately switch ACRO→ANGLE on mount.
            // Use the keyboard cycle key (default: M) to change flight mode instead.
        }
        // RC transmitters carry the arm switch on an AUX axis, not a joystick button.
        arm |= RCController.isArmSwitchOn();

        // Once per second, and only when the dev trace is on: formatting + appending a
        // line per tick was a measurable cost of its own while flying.
        rcLogTick++;
        if (Config.DEV_VERBOSE_LOG.get() && rcLogTick >= 20) {
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
        drone.setClientPredictedInputs(throttle, pitch, roll, yaw, arm);
        PacketDistributor.sendToServer(new ControllerPacket(
            drone.getId(), throttle, pitch, roll, yaw, arm, mode, ++controlSeq
        ));
        return true;
    }

    // Line of sight: the pilot's head tracks the drone so it stays on screen, and the
    // stick deltas are re-based on the snapped angles so only mouse movement reaches them.
    private static void faceDrone(DroneEntity drone) {
        net.minecraft.world.phys.Vec3 to = drone.getEyePosition().subtract(MC.player.getEyePosition());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 0.05 || Math.abs(to.y) > 0.05) {
            MC.player.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
            MC.player.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, flat))));
            MC.player.setYHeadRot(MC.player.getYRot());
        }
        lastPlayerYaw = MC.player.getYRot();
        lastPlayerPitch = MC.player.getXRot();
    }

    private static void sendKeyboardMouseInput(DroneEntity drone, boolean lineOfSight) {
        if (!keyboardInitialized) {
            keyboardThrottle = 0f;
            lastPlayerYaw = MC.player.getYRot();
            lastPlayerPitch = MC.player.getXRot();
            filteredPitch = 0f;
            filteredRoll = 0f;
            filteredYaw = 0f;
            mouseDronePitch = 0f;
            mouseDroneRoll = 0f;
            keyboardInitialized = true;
        }

        float yawDelta = normalizeDegrees(MC.player.getYRot() - lastPlayerYaw);
        float pitchDelta = MC.player.getXRot() - lastPlayerPitch;
        if (lineOfSight) {
            faceDrone(drone);
        } else {
            lastPlayerYaw = MC.player.getYRot();
            lastPlayerPitch = MC.player.getXRot();
            MC.player.setXRot(0f);
            lastPlayerPitch = 0f;
        }

        // 0.15/tick = 3.0/sec → full throttle range in ~7 ticks (0.35s), real FPV feel.
        // Passive decay is near-zero so releasing W holds the throttle position.
        if (MC.options.keyUp.isDown() || MC.options.keyJump.isDown()) {
            keyboardThrottle = Math.min(1.0f, keyboardThrottle + 0.15f);
        } else if (MC.options.keyDown.isDown() || MC.options.keySprint.isDown()) {
            keyboardThrottle = Math.max(0.0f, keyboardThrottle - 0.15f);
        }

        // The sticks follow mouse *movement* and spring back to center when it stops, so the
        // attitude no longer stays wherever the pointer was left.
        float ret = Config.MOUSE_RETURN.get().floatValue();
        mouseDroneRoll = clamp(mouseDroneRoll * (1f - ret)
            + yawDelta * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue() * MOUSE_RATE_SCALE, -1f, 1f);
        mouseDronePitch = clamp(mouseDronePitch * (1f - ret)
            - pitchDelta * Config.MOUSE_PITCH_SENSITIVITY.get().floatValue() * MOUSE_RATE_SCALE, -1f, 1f);
        float smoothing = Config.MOUSE_SMOOTHING.get().floatValue();
        filteredRoll = filteredRoll * smoothing + mouseDroneRoll * (1f - smoothing);
        filteredPitch = filteredPitch * smoothing + mouseDronePitch * (1f - smoothing);

        float yawTarget = 0f;
        if (MC.options.keyLeft.isDown()) yawTarget -= 1f;
        if (MC.options.keyRight.isDown()) yawTarget += 1f;
        filteredYaw += (yawTarget - filteredYaw) * 0.35f;
        float deadband = Config.MOUSE_DEADBAND.get().floatValue();
        float pitch = applyDeadband(filteredPitch, deadband);
        float roll = applyDeadband(filteredRoll, deadband);
        float yaw = applyDeadband(filteredYaw, deadband);
        boolean arm = keyboardArmSwitch;

        drone.setClientPredictedInputs(keyboardThrottle, pitch, roll, yaw, arm);
        PacketDistributor.sendToServer(new ControllerPacket(
            drone.getId(), keyboardThrottle, pitch, roll, yaw, arm, false, ++controlSeq
        ));
    }

    private static boolean sendPlaneControllerInput(PlaneEntity plane, boolean gimbalMode,
                                                    float yawDeltaP, float pitchDeltaP) {
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

        // A connected but untouched radio must not own the wing: hand everything back to
        // the keyboard/mouse path once the sticks have sat idle and a key was pressed recently.
        boolean sticksIdle = throttle < 0.02f && Math.abs(pitch) < 0.02f
            && Math.abs(roll) < 0.02f && Math.abs(yaw) < 0.02f;
        planeRcIdleTicks = sticksIdle ? planeRcIdleTicks + 1 : 0;
        if (planeRcIdleTicks > 40 && ticksSinceKeyboard < 200) return false;

        // Keyboard/mouse merge on top of the sticks, per axis. Throttle trim sits on
        // jump/sprint because W/S are the pitch keys while the mouse holds the gimbal.
        if (MC.options.keyJump.isDown()) {
            planeThrottleBias = Math.min(1f, planeThrottleBias + 0.05f);
        } else if (MC.options.keySprint.isDown()) {
            planeThrottleBias = Math.max(-1f, planeThrottleBias - 0.05f);
        } else if (throttle > 0.02f) {
            planeThrottleBias -= Math.signum(planeThrottleBias) * Math.min(0.01f, Math.abs(planeThrottleBias));
        }
        throttle = clamp(throttle + planeThrottleBias, 0f, 1f);

        float kbRollTarget = 0f;
        if (MC.options.keyLeft.isDown())  kbRollTarget -= 1f;
        if (MC.options.keyRight.isDown()) kbRollTarget += 1f;
        float kbPitchTarget = 0f;
        if (MC.options.keyUp.isDown())   kbPitchTarget -= 1f;
        if (MC.options.keyDown.isDown()) kbPitchTarget += 1f;

        if (gimbalMode) {
            filteredPlaneRoll  += (kbRollTarget  - filteredPlaneRoll)  * 0.35f;
            filteredPlanePitch += (kbPitchTarget - filteredPlanePitch) * 0.35f;
        } else {
            float mouseRateScale = 3.5f;
            mousePlaneRoll = clamp(yawDeltaP * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue() * mouseRateScale, -1f, 1f);
            mousePlanePitch = clamp(-pitchDeltaP * Config.MOUSE_PITCH_SENSITIVITY.get().floatValue() * mouseRateScale, -1f, 1f);
            float smoothing = Config.MOUSE_SMOOTHING.get().floatValue();
            filteredPlaneRoll = filteredPlaneRoll * smoothing + mousePlaneRoll * (1f - smoothing);
            filteredPlanePitch = filteredPlanePitch * smoothing + mousePlanePitch * (1f - smoothing);
            yaw = clamp(yaw - kbRollTarget * 0.6f, -1f, 1f);
        }
        float mergeDeadband = Config.MOUSE_DEADBAND.get().floatValue();
        roll  = clamp(roll  + applyDeadband(filteredPlaneRoll,  mergeDeadband), -1f, 1f);
        pitch = clamp(pitch + applyDeadband(filteredPlanePitch, mergeDeadband), -1f, 1f);

        boolean arm = (buttons != null && buttons.length > 0 && buttons[0]) || throttle > 0.10f;

        planeRcLogTick++;
        if (Config.DEV_VERBOSE_LOG.get() && planeRcLogTick >= 20) {
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
        PacketDistributor.sendToServer(new PlaneControllerPacket(
            plane.getId(), throttle, pitch, roll, yaw, arm
        ));
        return true;
    }

    private static void clearPlaneState(PlaneEntity plane, boolean notifyServer) {
        clearPlaneTracking(plane, true);
        stopPlaneOrbit(plane, notifyServer);
    }

    private static void clearPlaneTracking(PlaneEntity plane, boolean resetGimbal) {
        planeLockTarget = null;
        planeRawLockTarget = null;
        planeLockManualTicks = 0;
        if (resetGimbal) {
            planeGimbalYaw = 0f;
            planeGimbalPitch = 0f;
        }
        if (plane != null) {
            plane.setGimbal(planeGimbalYaw, planeGimbalPitch);
            plane.setStabilizedGimbalView(false);
            rebasePlaneMouseInput();
        }
    }

    public static boolean hasPlaneLock() {
        return planeLockTarget != null || planeRawLockTarget != null;
    }

    private static void actionBar(String key) {
        if (MC.player != null) {
            MC.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        }
    }

    private static void startPlaneOrbit(PlaneEntity plane, net.minecraft.world.phys.Vec3 center) {
        double dx = plane.getX() - center.x, dz = plane.getZ() - center.z;
        double radius = Math.max(12.0, Math.sqrt(dx * dx + dz * dz));
        planeOrbitActive = true;
        planeOrbitTarget = center;
        planeOrbitSyncGrace = 10;
        plane.setOrbit(true, center, radius);
        PacketDistributor.sendToServer(new org.restor.fpv.network.PlaneOrbitPacket(
            plane.getId(), true, center.x, center.y, center.z, radius));
    }

    private static void stopPlaneOrbit(PlaneEntity plane, boolean notifyServer) {
        planeOrbitActive = false;
        planeOrbitTarget = null;
        planeOrbitSyncGrace = notifyServer ? 20 : 0;
        if (plane != null) {
            plane.setOrbit(false, net.minecraft.world.phys.Vec3.ZERO, 0);
            if (notifyServer) {
                PacketDistributor.sendToServer(new org.restor.fpv.network.PlaneOrbitPacket(
                    plane.getId(), false, 0, 0, 0, 0));
            }
        }
    }

    private static boolean samePoint(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) < 0.01;
    }

    private static net.minecraft.world.phys.Vec3 smoothLockTarget(net.minecraft.world.phys.Vec3 current,
                                                                 net.minecraft.world.phys.Vec3 target,
                                                                 double alpha) {
        if (current == null) return target;
        double dist = Math.sqrt(current.distanceToSqr(target));
        double maxStep = Math.max(2.0, dist * alpha);
        if (dist <= maxStep) return target;
        return current.add(target.subtract(current).normalize().scale(maxStep));
    }

    private static void updatePlaneTargetStreaming(PlaneEntity plane) {
        net.minecraft.world.phys.Vec3 target = planeRawLockTarget != null ? planeRawLockTarget
            : planeLockTarget != null ? planeLockTarget
            : planeOrbitTarget;
        if (target == null) {
            if (planeTargetStreamActive) {
                PacketDistributor.sendToServer(new org.restor.fpv.network.PlaneTargetStreamPacket(
                    plane.getId(), false, 0, 0, 0));
                planeTargetStreamActive = false;
            }
            planeTargetStreamTicks = 0;
            return;
        }
        if (!planeTargetStreamActive || planeTargetStreamTicks-- <= 0) {
            PacketDistributor.sendToServer(new org.restor.fpv.network.PlaneTargetStreamPacket(
                plane.getId(), true, target.x, target.y, target.z));
            planeTargetStreamActive = true;
            planeTargetStreamTicks = 10;
        }
    }

    private static void rebasePlaneMouseInput() {
        if (MC.player == null) return;
        MC.player.setXRot(0f);
        lastPlaneYaw = MC.player.getYRot();
        lastPlanePitch = 0f;
        filteredPlaneRoll = 0f;
        filteredPlanePitch = 0f;
        mousePlaneRoll = 0f;
        mousePlanePitch = 0f;
        planeKeyboardInitialized = false;
        planeInitialized = true;
    }

    private static void rebasePlaneViewPitch() {
        if (MC.player == null) return;
        MC.player.setXRot(0f);
        lastPlanePitch = 0f;
    }

    private static void sendPlaneKeyboardInput(PlaneEntity plane, boolean gimbalMode,
                                               float yawDeltaP, float pitchDeltaP) {
        if (!planeKeyboardInitialized) {
            planeThrottle = plane.getVisualThrottle();
            filteredPlaneRoll = 0f;
            filteredPlanePitch = 0f;
            mousePlaneRoll = 0f;
            mousePlanePitch = 0f;
            planeKeyboardInitialized = true;
        }

        // W/S become the pitch keys in gimbal mode, so throttle also lives on jump/sprint —
        // otherwise it would be frozen wherever it happened to be when the mode flipped.
        boolean throttleUp = MC.options.keyJump.isDown() || (!gimbalMode && MC.options.keyUp.isDown());
        boolean throttleDown = MC.options.keySprint.isDown() || (!gimbalMode && MC.options.keyDown.isDown());
        if (throttleUp) {
            planeThrottle = Math.min(1.0f, planeThrottle + 0.15f);
        } else if (throttleDown) {
            planeThrottle = Math.max(0.0f, planeThrottle - 0.15f);
        }

        float roll, pitch, yaw;
        if (gimbalMode) {
            // Mouse is busy panning the camera — WASD flies the wing, throttle is held.
            float rollTarget = 0f;
            if (MC.options.keyLeft.isDown()) rollTarget -= 1f;
            if (MC.options.keyRight.isDown()) rollTarget += 1f;
            filteredPlaneRoll += (rollTarget - filteredPlaneRoll) * 0.35f;
            roll = filteredPlaneRoll;

            float pitchTarget = 0f;
            if (MC.options.keyUp.isDown()) pitchTarget -= 1f;
            if (MC.options.keyDown.isDown()) pitchTarget += 1f;
            filteredPlanePitch += (pitchTarget - filteredPlanePitch) * 0.35f;
            pitch = filteredPlanePitch;
            yaw = 0f;
        } else {
            // Mouse rate mode: current mouse movement commands pitch/roll rate, then
            // returns to neutral. This avoids accumulated stick state getting stuck near
            // vertical/Euler edge cases.
            float mouseRateScale = 3.5f;
            mousePlaneRoll = clamp(yawDeltaP * Config.MOUSE_ROLL_SENSITIVITY.get().floatValue() * mouseRateScale, -1f, 1f);
            mousePlanePitch = clamp(-pitchDeltaP * Config.MOUSE_PITCH_SENSITIVITY.get().floatValue() * mouseRateScale, -1f, 1f);
            float smoothing = Config.MOUSE_SMOOTHING.get().floatValue();
            filteredPlaneRoll = filteredPlaneRoll * smoothing + mousePlaneRoll * (1f - smoothing);
            filteredPlanePitch = filteredPlanePitch * smoothing + mousePlanePitch * (1f - smoothing);
            float deadband = Config.MOUSE_DEADBAND.get().floatValue();
            roll = applyDeadband(filteredPlaneRoll, deadband);
            pitch = applyDeadband(filteredPlanePitch, deadband);

            yaw = 0f;
            if (MC.options.keyLeft.isDown()) yaw += 1f;
            if (MC.options.keyRight.isDown()) yaw -= 1f;
        }

        boolean arm = planeThrottle > 0.05f;
        plane.setClientPredictedInputs(planeThrottle, pitch, roll, yaw);
        PacketDistributor.sendToServer(new PlaneControllerPacket(
            plane.getId(), planeThrottle, pitch, roll, yaw, arm
        ));
    }

    private static boolean hasKeyboardKeyInput() {
        return MC.options.keyUp.isDown() || MC.options.keyDown.isDown()
            || MC.options.keyLeft.isDown() || MC.options.keyRight.isDown()
            || MC.options.keyJump.isDown() || MC.options.keySprint.isDown()
            || ModKeyBindings.DRONE_YAW_LEFT.isDown() || ModKeyBindings.DRONE_YAW_RIGHT.isDown();
    }

    private static final float MOUSE_RATE_SCALE = 2.2f;
    private static final float HOVER_STICK = 0.5f;

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

    private static float approach(float current, float target, float maxDelta) {
        float delta = clamp(target - current, -maxDelta, maxDelta);
        return current + delta;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = normalizeDegrees(target - current);
        delta = clamp(delta, -maxDelta, maxDelta);
        return normalizeDegrees(current + delta);
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
        net.minecraft.world.phys.Vec3 end = start.add(dir.scale(1024));
        net.minecraft.world.phys.BlockHitResult hit = MC.level.clip(new net.minecraft.world.level.ClipContext(
            start, end, net.minecraft.world.level.ClipContext.Block.VISUAL,
            net.minecraft.world.level.ClipContext.Fluid.NONE, MC.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            hit = MC.level.clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, MC.player));
        }
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return hit.getLocation();
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
        // Pilots ride inside the FPV entity/camera-follow body; don't render their player
        // model clipped into the drone/plane for any client in multiplayer.
        if (event.getEntity().getVehicle() instanceof org.restor.fpv.entity.Pilotable
            || (MC.player != null && event.getEntity() == MC.player
                && MC.getCameraEntity() != MC.player
                && MC.getCameraEntity() instanceof org.restor.fpv.entity.Pilotable)) {
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
