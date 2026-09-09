package org.restor.fpv.controller;

import com.mojang.logging.LogUtils;
import org.lwjgl.glfw.GLFW;
import org.restor.fpv.Config;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class RCController {
    public static final int AXIS_THROTTLE = 0;
    public static final int AXIS_ROLL = 1;
    public static final int AXIS_PITCH = 2;
    public static final int AXIS_YAW = 3;
    public static final int BTN_ARM = 0;
    public static final int BTN_MODE = 1;

    private static int currentJoystick = -1;
    private static String controllerName = "";
    private static boolean connected = false;
    private static boolean isRCTransmitter = false;
    private static int lastButtonState = 0;

    private static final int[] axisMap = new int[]{-1, -1, -1, -1};
    private static float[] axisCenter;
    private static int armButton = -1;
    private static int modeButton = -1;
    private static float[] lastRawAxes;
    private static float[] lastProcessedAxes;
    private static int logCounter;

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void scan() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) {
                String name = GLFW.glfwGetJoystickName(jid);
                if (name != null && (name.toLowerCase().contains("tx") || name.toLowerCase().contains("radio")
                    || name.toLowerCase().contains("transmitter") || name.toLowerCase().contains("joystick")
                    || name.toLowerCase().contains("gamepad") || name.toLowerCase().contains("controller")
                    || name.toLowerCase().contains("radiomaster") || name.toLowerCase().contains("pocket")
                    || name.toLowerCase().contains("boxer") || name.toLowerCase().contains("zorro")
                    || name.toLowerCase().contains("t-lite") || name.toLowerCase().contains("t-pro")
                    || name.toLowerCase().contains("commander") || name.toLowerCase().contains("elrs")
                    || name.toLowerCase().contains("jumper") || name.toLowerCase().contains("betaflight")
                    || name.toLowerCase().contains("ethos") || name.toLowerCase().contains("frsky"))) {
                    setupController(jid, name);
                    return;
                }
            }
        }

        // Fallback: pick first joystick with 4+ axes
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) {
                FloatBuffer buf = GLFW.glfwGetJoystickAxes(jid);
                if (buf != null && buf.remaining() >= 4) {
                    String name = GLFW.glfwGetJoystickName(jid);
                    setupController(jid, name != null ? name : "Unknown");
                    return;
                }
            }
        }

        if (connected) {
            LOGGER.info("Controller disconnected: \"{}\"", controllerName);
        }
        connected = false;
        isRCTransmitter = false;
        currentJoystick = -1;
        controllerName = "";
        axisCenter = null;
    }

    private static void setupController(int jid, String name) {
        if (jid == currentJoystick && connected) return;
        currentJoystick = jid;
        controllerName = name;
        connected = true;
        LOGGER.info("Connected to controller: \"{}\" (joystick #{})", name, jid);

        // Auto-detect axis mapping and device type based on name
        String lower = name.toLowerCase();
        if (lower.contains("tx") || lower.contains("radio") || lower.contains("transmitter")
            || lower.contains("radiomaster") || lower.contains("pocket") || lower.contains("boxer")
            || lower.contains("zorro") || lower.contains("elrs") || lower.contains("jumper")
            || lower.contains("frsky") || lower.contains("ethos")) {
            // RC transmitter Mode 2: Left Y=Throttle, Left X=Yaw, Right Y=Pitch, Right X=Roll
            isRCTransmitter = true;
        } else {
            isRCTransmitter = false;
        }
        applyConfiguredAxisMap();

        axisCenter = null; // will be calibrated on first readAxes call

        // Auto-detect buttons
        ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
        int btnCount = buttons != null ? buttons.remaining() : 0;
        if (btnCount >= 2) {
            armButton = 0;
            modeButton = 1;
        } else if (btnCount >= 1) {
            armButton = 0;
            modeButton = -1;
        } else {
            armButton = -1;
            modeButton = -1;
        }
    }

    public static float[] readAxes() {
        if (!connected || currentJoystick < 0) return null;
        if (applyConfiguredAxisMap()) axisCenter = null;

        FloatBuffer axes = GLFW.glfwGetJoystickAxes(currentJoystick);
        if (axes == null || axes.remaining() < 4) {
            connected = false;
            return null;
        }

        float[] values = new float[4];
        float[] raw = new float[axes.remaining()];
        for (int i = 0; i < raw.length; i++) raw[i] = axes.get(i);
        lastRawAxes = raw;

        if (isRCTransmitter) {
            // RC transmitter: throttle rests at bottom (-1 raw), top (+1 raw).
            // Map linearly: bottom → 0, top → 1.
            int thrIdx = axisMap[AXIS_THROTTLE];
            float rawThr = thrIdx >= 0 && thrIdx < raw.length ? raw[thrIdx] : -1f;
            if (Config.RC_INVERT_THROTTLE.get()) rawThr = -rawThr;
            float thr01 = thrIdx >= 0 ? Math.max(0f, Math.min(1f, (rawThr + 1f) / 2f)) : 0f;
            values[AXIS_THROTTLE] = applyThrottleMid(thr01, Config.RC_THROTTLE_MID.get().floatValue());

            // Calibrate centers for stick axes (pitch/roll/yaw) on first read
            if (axisCenter == null) {
                axisCenter = new float[4];
                for (int i = 0; i < 4; i++) {
                    if (i == AXIS_THROTTLE) { axisCenter[i] = 0; continue; }
                    int idx = axisMap[i];
                    axisCenter[i] = idx >= 0 && idx < raw.length ? raw[idx] : 0;
                }
                LOGGER.info("RC TX centers: roll={} pitch={} yaw={} (throttle not centered)",
                    String.format("%.3f", axisCenter[AXIS_ROLL]),
                    String.format("%.3f", axisCenter[AXIS_PITCH]),
                    String.format("%.3f", axisCenter[AXIS_YAW]));
            }

            // Stick axes: center-normalized to [-1, 1]
            for (int i = 0; i < 4; i++) {
                if (i == AXIS_THROTTLE) continue;
                if (axisMap[i] < 0 || axisMap[i] == axisMap[AXIS_THROTTLE]) {
                    values[i] = 0f;
                    continue;
                }
                float rawVal = axisMap[i] >= 0 && axisMap[i] < raw.length ? raw[axisMap[i]] : 0;
                rawVal = applyInvert(i, rawVal);
                float center = axisCenter[i];
                float centered = rawVal - center;
                float scale = 1f - Math.abs(center);
                if (scale < 0.01f) scale = 0.01f;
                values[i] = shapeAxis(Math.max(-1f, Math.min(1f, centered / scale)));
            }
        } else {
            // Generic gamepad: auto-center all axes on first read
            if (axisCenter == null) {
                axisCenter = new float[4];
                for (int i = 0; i < 4; i++) {
                    int idx = axisMap[i];
                    axisCenter[i] = idx >= 0 && idx < raw.length ? raw[idx] : 0;
                }
                LOGGER.info("Gamepad centers: thr={} roll={} pitch={} yaw={}",
                    String.format("%.3f", axisCenter[AXIS_THROTTLE]),
                    String.format("%.3f", axisCenter[AXIS_ROLL]),
                    String.format("%.3f", axisCenter[AXIS_PITCH]),
                    String.format("%.3f", axisCenter[AXIS_YAW]));
            }

            for (int i = 0; i < 4; i++) {
                float rawVal = axisMap[i] >= 0 && axisMap[i] < raw.length ? raw[axisMap[i]] : 0;
                if (axisMap[i] < 0 || i != AXIS_THROTTLE && axisMap[i] == axisMap[AXIS_THROTTLE]) {
                    values[i] = 0f;
                    continue;
                }
                rawVal = applyInvert(i, rawVal);
                float center = axisCenter[i];
                float centered = rawVal - center;
                float scale = 1f + Math.abs(center);
                if (scale < 0.01f) scale = 0.01f;
                values[i] = shapeAxis(Math.max(-1f, Math.min(1f, centered / scale)));
            }

            // Gamepad throttle: LY up=-1 is full throttle; invert and remap to [0,1]
            values[AXIS_THROTTLE] = axisMap[AXIS_THROTTLE] >= 0
                ? Math.max(0f, Math.min(1f, (-values[AXIS_THROTTLE] + 1f) / 2f))
                : 0f;
        }

        lastProcessedAxes = values.clone();
        return values;
    }

    public static float[] getLastProcessedAxes() {
        return lastProcessedAxes;
    }

    private static boolean applyConfiguredAxisMap() {
        int throttle = Config.RC_AXIS_THROTTLE.get();
        int yaw = Config.RC_AXIS_YAW.get();
        int pitch = Config.RC_AXIS_PITCH.get();
        int roll = Config.RC_AXIS_ROLL.get();

        if (throttle == 1 && yaw == 0 && pitch == 3 && roll == 2) {
            throttle = 2;
            yaw = 3;
            pitch = 1;
            roll = 0;
            Config.RC_AXIS_THROTTLE.set(throttle);
            Config.RC_AXIS_YAW.set(yaw);
            Config.RC_AXIS_PITCH.set(pitch);
            Config.RC_AXIS_ROLL.set(roll);
            Config.save();
            LOGGER.info("Migrated old RC axis defaults to AETR order: roll=0 pitch=1 throttle=2 yaw=3");
        }

        boolean changed = axisMap[AXIS_THROTTLE] != throttle || axisMap[AXIS_YAW] != yaw
            || axisMap[AXIS_PITCH] != pitch || axisMap[AXIS_ROLL] != roll;
        axisMap[AXIS_THROTTLE] = throttle;
        axisMap[AXIS_YAW] = yaw;
        axisMap[AXIS_PITCH] = pitch;
        axisMap[AXIS_ROLL] = roll;
        return changed;
    }

    private static float applyInvert(int axis, float value) {
        boolean invert = switch (axis) {
            case AXIS_THROTTLE -> Config.RC_INVERT_THROTTLE.get();
            case AXIS_YAW -> Config.RC_INVERT_YAW.get();
            case AXIS_PITCH -> Config.RC_INVERT_PITCH.get();
            case AXIS_ROLL -> Config.RC_INVERT_ROLL.get();
            default -> false;
        };
        return invert ? -value : value;
    }

    // Two-segment throttle curve: at 50% stick the output is `mid`.
    // 0..0.5 stick → 0..mid output; 0.5..1 stick → mid..1 output.
    // mid=0.5 is linear; mid=0.4 gives more precision at idle, raises hover stick position.
    static float applyThrottleMid(float t, float mid) {
        if (t <= 0.5f) return t * mid * 2f;
        return mid + (t - 0.5f) * (1f - mid) * 2f;
    }

    private static float shapeAxis(float value) {
        float deadband = Config.RC_DEADBAND.get().floatValue();
        if (Math.abs(value) < deadband) return 0f;
        float sign = Math.signum(value);
        float normalized = (Math.abs(value) - deadband) / (1f - deadband);
        float expo = Config.RC_EXPO.get().floatValue();
        float shaped = normalized * (1f - expo) + normalized * normalized * normalized * expo;
        return sign * Math.max(0f, Math.min(1f, shaped));
    }

    public static float[] readRawAxes() {
        return lastRawAxes;
    }

    public static boolean[] readButtons() {
        if (!connected || currentJoystick < 0) return null;

        ByteBuffer buttons = GLFW.glfwGetJoystickButtons(currentJoystick);
        if (buttons == null || buttons.remaining() == 0) return null;

        boolean[] state = new boolean[2];
        int btnCount = buttons.remaining();

        if (armButton >= 0 && armButton < btnCount) {
            state[0] = buttons.get(armButton) == GLFW.GLFW_PRESS;
        }
        if (modeButton >= 0 && modeButton < btnCount) {
            state[1] = buttons.get(modeButton) == GLFW.GLFW_PRESS;
        }

        return state;
    }

    // RC transmitters expose their arm switch as an AUX axis (rest ~-1, flipped ~+1),
    // not a GLFW button — so the button-based arm path never fires for them. Read the
    // configured arm axis; when unset (-1), auto-detect the first non-gimbal axis the
    // user flips high and persist it so future reads use it directly.
    public static boolean isArmSwitchOn() {
        if (!connected) return false;
        float[] raw = lastRawAxes;
        if (raw == null) return false;
        boolean invert = Config.RC_INVERT_ARM.get();
        int cfg = Config.RC_AXIS_ARM.get();
        if (cfg >= 0 && cfg < raw.length) {
            float v = invert ? -raw[cfg] : raw[cfg];
            return v > 0f;
        }
        if (cfg < 0) {
            for (int i = 0; i < raw.length; i++) {
                if (i == axisMap[AXIS_THROTTLE] || i == axisMap[AXIS_ROLL]
                    || i == axisMap[AXIS_PITCH] || i == axisMap[AXIS_YAW]) continue;
                float v = invert ? -raw[i] : raw[i];
                if (v > 0.5f) {
                    Config.RC_AXIS_ARM.set(i);
                    Config.save();
                    LOGGER.info("[RC-ARM] auto-detected arm switch on AUX axis {}", i);
                    return true;
                }
            }
        }
        return false;
    }

    public static String getControllerName() {
        return controllerName;
    }

    public static boolean isConnected() {
        return connected;
    }

    public static boolean isRCTransmitter() {
        return isRCTransmitter;
    }

    public static int getCurrentJoystick() {
        return currentJoystick;
    }
}
