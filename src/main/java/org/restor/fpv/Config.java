package org.restor.fpv;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue KEYBOARD_YAW_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_PITCH_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_ROLL_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue MOUSE_DEADBAND;

    public static final ForgeConfigSpec.DoubleValue RC_DEADBAND;
    public static final ForgeConfigSpec.DoubleValue RC_EXPO;
    public static final ForgeConfigSpec.DoubleValue RC_PITCH_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_ROLL_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_YAW_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_THROTTLE_MID;
    public static final ForgeConfigSpec.IntValue RC_AXIS_THROTTLE;
    public static final ForgeConfigSpec.IntValue RC_AXIS_YAW;
    public static final ForgeConfigSpec.IntValue RC_AXIS_PITCH;
    public static final ForgeConfigSpec.IntValue RC_AXIS_ROLL;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_THROTTLE;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_YAW;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_PITCH;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_ROLL;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_APPLIED;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V2;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V3;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V4;

    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LINK_CODE;

    public static final ForgeConfigSpec.DoubleValue DEV_MASS_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_POWER_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_DRAG_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_MAX_SPEED;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_POWER;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_BREAK_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_FLING_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_MOB_DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_BACKSTEP;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_SECONDS;
    public static final ForgeConfigSpec.IntValue DEV_CHUNK_RADIUS;
    public static final ForgeConfigSpec.DoubleValue DEV_HITBOX_SCALE;
    public static final ForgeConfigSpec.DoubleValue DEV_HITBOX_Y_OFFSET;

    public static final ForgeConfigSpec.BooleanValue HUD_HINTS;
    public static final ForgeConfigSpec.BooleanValue HUD_TELEMETRY;
    public static final ForgeConfigSpec.BooleanValue HUD_STICKS;
    public static final ForgeConfigSpec.BooleanValue HUD_ROLL_LINE;
    public static final ForgeConfigSpec.BooleanValue HUD_PITCH_LADDER;
    public static final ForgeConfigSpec.BooleanValue HUD_THROTTLE_BAR;
    public static final ForgeConfigSpec.BooleanValue HUD_CROSSHAIR;
    public static final ForgeConfigSpec.BooleanValue HUD_HEADING;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("keyboard_mouse");
        KEYBOARD_YAW_SENSITIVITY = BUILDER.comment("Yaw rate for A/D keys. 1.0 = full drone yaw rate.")
            .defineInRange("keyboardYawSensitivity", 0.45, 0.05, 1.0);
        MOUSE_PITCH_SENSITIVITY = BUILDER.comment("Mouse vertical sensitivity for pitch.")
            .defineInRange("mousePitchSensitivity", 0.045, 0.005, 0.2);
        MOUSE_ROLL_SENSITIVITY = BUILDER.comment("Mouse horizontal sensitivity for roll.")
            .defineInRange("mouseRollSensitivity", 0.045, 0.005, 0.2);
        MOUSE_SMOOTHING = BUILDER.comment("Mouse input smoothing. 0 = raw, 0.85 = very smooth.")
            .defineInRange("mouseSmoothing", 0.55, 0.0, 0.85);
        MOUSE_DEADBAND = BUILDER.comment("Ignore tiny mouse input to prevent camera/attitude jitter.")
            .defineInRange("mouseDeadband", 0.025, 0.0, 0.2);
        BUILDER.pop();

        BUILDER.push("controller");
        RC_DEADBAND = BUILDER.comment("RC stick deadband.")
            .defineInRange("deadband", 0.08, 0.0, 0.25);
        RC_EXPO = BUILDER.comment("RC expo. 0 = linear, 0.7 = softer center.")
            .defineInRange("expo", 0.25, 0.0, 0.8);
        RC_PITCH_RATE = BUILDER.comment("Pitch rate multiplier. 1.0 = drone default, 0.5 = half, 2.0 = double. Applied to right stick vertical.")
            .defineInRange("pitchRate", 1.0, 0.1, 3.0);
        RC_ROLL_RATE = BUILDER.comment("Roll rate multiplier. 1.0 = drone default, 0.5 = half, 2.0 = double. Applied to right stick horizontal.")
            .defineInRange("rollRate", 1.0, 0.1, 3.0);
        RC_YAW_RATE = BUILDER.comment("Yaw rate multiplier. 1.0 = drone default, 0.5 = half, 2.0 = double. Applied to left stick horizontal.")
            .defineInRange("yawRate", 1.0, 0.1, 3.0);
        RC_THROTTLE_MID = BUILDER.comment("Throttle output at 50% stick. 0.5 = linear (default). >0.5 = more precision around hover, hover needs less stick. <0.5 = more sensitivity above 50% stick (not recommended).")
            .defineInRange("throttleMid", 0.5, 0.1, 0.9);
        RC_AXIS_THROTTLE = BUILDER.comment("Throttle axis index. -1 disables this channel.")
            .defineInRange("axisThrottle", 2, -1, 15);
        RC_AXIS_YAW = BUILDER.comment("Yaw axis index. -1 disables this channel.")
            .defineInRange("axisYaw", 3, -1, 15);
        RC_AXIS_PITCH = BUILDER.comment("Pitch axis index. -1 disables this channel.")
            .defineInRange("axisPitch", 1, -1, 15);
        RC_AXIS_ROLL = BUILDER.comment("Roll axis index. -1 disables this channel.")
            .defineInRange("axisRoll", 0, -1, 15);
        RC_INVERT_THROTTLE = BUILDER.comment("Invert throttle axis before mapping to 0..1.")
            .define("invertThrottle", false);
        RC_INVERT_YAW = BUILDER.comment("Invert yaw axis. Default true: RadioMaster Pocket left stick horizontal reports inverted sign in HID mode.")
            .define("invertYaw", true);
        RC_INVERT_PITCH = BUILDER.comment("Invert pitch axis.")
            .define("invertPitch", false);
        RC_INVERT_ROLL = BUILDER.comment("Invert roll axis.")
            .define("invertRoll", false);
        RC_TX_DEFAULTS_APPLIED = BUILDER.comment("Internal flag: set to true after RC transmitter axis defaults are applied once on first connect.")
            .define("rcTxDefaultsApplied", false);
        RC_TX_DEFAULTS_V2 = BUILDER.comment("Internal flag: v2 migration that corrects invertPitch back to false.")
            .define("rcTxDefaultsV2", false);
        RC_TX_DEFAULTS_V3 = BUILDER.comment("Internal flag: v3 migration that resets throttleMid to 0.5 (linear).")
            .define("rcTxDefaultsV3", false);
        RC_TX_DEFAULTS_V4 = BUILDER.comment("Internal flag: v4 migration that raises chunkRadius to 8 for chunk streaming.")
            .define("rcTxDefaultsV4", false);
        BUILDER.pop();

        BUILDER.push("remote");
        DEFAULT_LINK_CODE = BUILDER.comment("Remembered 6-digit link code, auto-assigned to newly placed remote drones. Empty = off.")
            .define("defaultLinkCode", "");
        BUILDER.pop();

        BUILDER.push("dev");
        DEV_MASS_MULT = BUILDER.comment("Drone weight multiplier. Affects acceleration, braking, terminal velocity, rotation inertia and battery drain (heavier hovers at higher throttle).")
            .defineInRange("massMultiplier", 1.0, 0.2, 5.0);
        DEV_POWER_MULT = BUILDER.comment("Motor power multiplier. Affects thrust (punch), top speed and battery drain.")
            .defineInRange("powerMultiplier", 1.0, 0.2, 5.0);
        DEV_DRAG_MULT = BUILDER.comment("Air-drag multiplier. Higher = the drone dumps its current momentum faster, so it brakes and reverses direction (flip 180 + full throttle) more sharply; lower = more glide/inertia.")
            .defineInRange("dragMultiplier", 1.0, 0.1, 6.0);
        DEV_MAX_SPEED = BUILDER.comment("Top-speed cap in m/s. Terminal velocity is normally set by drag, this is the hard upper clamp so the drone can't accelerate forever.")
            .defineInRange("maxSpeed", 60.0, 10.0, 150.0);
        DEV_EXPLOSION_POWER = BUILDER.comment("Kamikaze explosion power. 4.0 = TNT.")
            .defineInRange("explosionPower", 4.0, 1.0, 100.0);
        DEV_EXPLOSION_BLOCK_BREAK_PERCENT = BUILDER.comment("Percent of vanilla kamikaze block destruction to keep. 0 = no block breaking, 100 = normal explosion crater.")
            .defineInRange("explosionBlockBreakPercent", 100.0, 0.0, 100.0);
        DEV_EXPLOSION_BLOCK_FLING_PERCENT = BUILDER.comment("Percent of nearby blocks copied into flying debris before the kamikaze explosion.")
            .defineInRange("explosionBlockFlingPercent", 100.0, 0.0, 200.0);
        DEV_EXPLOSION_MOB_DAMAGE_MULT = BUILDER.comment("Extra kamikaze mob damage multiplier. 1.0 = default tuned damage.")
            .defineInRange("explosionMobDamageMultiplier", 1.0, 0.0, 5.0);
        DEV_EXPLOSION_CAMERA_BACKSTEP = BUILDER.comment("Before a kamikaze detonates, move its FPV camera this many blocks backward along the flight path so the blast is visible. 0 = off.")
            .defineInRange("explosionCameraBackstep", 8.0, 0.0, 40.0);
        DEV_EXPLOSION_CAMERA_SECONDS = BUILDER.comment("How long to keep the FPV camera watching the kamikaze blast after detonation.")
            .defineInRange("explosionCameraSeconds", 2.0, 0.0, 6.0);
        DEV_CHUNK_RADIUS = BUILDER.comment("Chunk streaming radius around a piloted drone (chunks are pushed to the pilot's client). 0 = off.")
            .defineInRange("chunkRadius", 8, 0, 12);
        DEV_HITBOX_SCALE = BUILDER.comment("Drone hitbox size multiplier.")
            .defineInRange("hitboxScale", 1.0, 0.2, 3.0);
        DEV_HITBOX_Y_OFFSET = BUILDER.comment("Drone hitbox vertical offset in blocks.")
            .defineInRange("hitboxYOffset", 0.0, -1.0, 1.0);
        BUILDER.pop();

        BUILDER.push("hud");
        HUD_HINTS = BUILDER.comment("Show the top-left control-key hint list.")
            .define("showHints", true);
        HUD_TELEMETRY = BUILDER.comment("Show telemetry text (mode, arm, battery, RSSI, altitude, TX, speed).")
            .define("showTelemetry", true);
        HUD_STICKS = BUILDER.comment("Show stick position boxes.")
            .define("showSticks", true);
        HUD_ROLL_LINE = BUILDER.comment("Show the tilting dashed roll line.")
            .define("showRollLine", true);
        HUD_PITCH_LADDER = BUILDER.comment("Show the pitch ladder.")
            .define("showPitchLadder", true);
        HUD_THROTTLE_BAR = BUILDER.comment("Show the throttle bar and percentage.")
            .define("showThrottleBar", true);
        HUD_CROSSHAIR = BUILDER.comment("Show the center crosshair.")
            .define("showCrosshair", true);
        HUD_HEADING = BUILDER.comment("Show the compass heading.")
            .define("showHeading", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void save() {
        SPEC.save();
    }
}
