package org.restor.fpv;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue KEYBOARD_YAW_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_PITCH_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_ROLL_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOUSE_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue MOUSE_RETURN;
    public static final ForgeConfigSpec.DoubleValue MOUSE_DEADBAND;

    public static final ForgeConfigSpec.DoubleValue RC_DEADBAND;
    public static final ForgeConfigSpec.DoubleValue RC_EXPO;
    public static final ForgeConfigSpec.DoubleValue RC_PITCH_RATE = CommonConfig.RC_PITCH_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_ROLL_RATE = CommonConfig.RC_ROLL_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_YAW_RATE = CommonConfig.RC_YAW_RATE;
    public static final ForgeConfigSpec.DoubleValue RC_THROTTLE_MID;
    public static final ForgeConfigSpec.IntValue RC_AXIS_THROTTLE;
    public static final ForgeConfigSpec.IntValue RC_AXIS_YAW;
    public static final ForgeConfigSpec.IntValue RC_AXIS_PITCH;
    public static final ForgeConfigSpec.IntValue RC_AXIS_ROLL;
    public static final ForgeConfigSpec.IntValue RC_AXIS_ARM;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_THROTTLE;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_YAW;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_PITCH;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_ROLL;
    public static final ForgeConfigSpec.BooleanValue RC_INVERT_ARM;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_APPLIED;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V2;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V3;
    public static final ForgeConfigSpec.BooleanValue RC_TX_DEFAULTS_V4;
    public static final ForgeConfigSpec.BooleanValue REALISTIC_BATTERY = CommonConfig.REALISTIC_BATTERY;
    public static final ForgeConfigSpec.BooleanValue REALISTIC_RADIO = CommonConfig.REALISTIC_RADIO;

    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LINK_CODE = CommonConfig.DEFAULT_LINK_CODE;

    public static final ForgeConfigSpec.DoubleValue DEV_POWER_MULT = CommonConfig.DEV_POWER_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_DRAG_MULT = CommonConfig.DEV_DRAG_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_MAX_SPEED = CommonConfig.DEV_MAX_SPEED;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_POWER = CommonConfig.DEV_EXPLOSION_POWER;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_BREAK_PERCENT = CommonConfig.DEV_EXPLOSION_BLOCK_BREAK_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_FLING_PERCENT = CommonConfig.DEV_EXPLOSION_BLOCK_FLING_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_MOB_DAMAGE_MULT = CommonConfig.DEV_EXPLOSION_MOB_DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_BACKSTEP = CommonConfig.DEV_EXPLOSION_CAMERA_BACKSTEP;
    public static final ForgeConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_SECONDS = CommonConfig.DEV_EXPLOSION_CAMERA_SECONDS;
    public static final ForgeConfigSpec.IntValue DEV_CHUNK_RADIUS = CommonConfig.DEV_CHUNK_RADIUS;
    public static final ForgeConfigSpec.DoubleValue DEV_HITBOX_SCALE = CommonConfig.DEV_HITBOX_SCALE;
    public static final ForgeConfigSpec.DoubleValue DEV_HITBOX_Y_OFFSET = CommonConfig.DEV_HITBOX_Y_OFFSET;
    public static final ForgeConfigSpec.BooleanValue DEV_NO_DAMAGE = CommonConfig.DEV_NO_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue DEV_VERBOSE_LOG = CommonConfig.DEV_VERBOSE_LOG;

    public static final ForgeConfigSpec.DoubleValue FPV_CAMERA_HEIGHT = CommonConfig.FPV_CAMERA_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue FPV_PROPS_IN_VIEW;
    public static final ForgeConfigSpec.DoubleValue FPV_PROPS_FORWARD;
    public static final ForgeConfigSpec.DoubleValue FPV_PROPS_DOWN;
    public static final ForgeConfigSpec.DoubleValue FPV_PROPS_SCALE;

    public static final ForgeConfigSpec.BooleanValue HUD_HINTS;
    public static final ForgeConfigSpec.BooleanValue HUD_TELEMETRY;
    public static final ForgeConfigSpec.BooleanValue HUD_STICKS;
    public static final ForgeConfigSpec.BooleanValue HUD_ROLL_LINE;
    public static final ForgeConfigSpec.BooleanValue HUD_PITCH_LADDER;
    public static final ForgeConfigSpec.BooleanValue HUD_THROTTLE_BAR;
    public static final ForgeConfigSpec.BooleanValue HUD_CROSSHAIR;
    public static final ForgeConfigSpec.BooleanValue HUD_HEADING;
    public static final ForgeConfigSpec.BooleanValue HUD_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue HUD_NETSTATS;

    public static final ForgeConfigSpec.DoubleValue SOUND_MOTOR_VOLUME;
    public static final ForgeConfigSpec.DoubleValue SOUND_EFFECTS_VOLUME;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("keyboard_mouse");
        KEYBOARD_YAW_SENSITIVITY = BUILDER.comment("Yaw rate for A/D keys. 1.0 = full drone yaw rate.")
            .defineInRange("keyboardYawSensitivity", 0.45, 0.05, 1.0);
        MOUSE_PITCH_SENSITIVITY = BUILDER.comment("Mouse vertical sensitivity for pitch.")
            .defineInRange("mousePitchSensitivity", 0.035, 0.005, 0.2);
        MOUSE_ROLL_SENSITIVITY = BUILDER.comment("Mouse horizontal sensitivity for roll.")
            .defineInRange("mouseRollSensitivity", 0.035, 0.005, 0.2);
        MOUSE_SMOOTHING = BUILDER.comment("Mouse input smoothing. 0 = raw, 0.85 = very smooth.")
            .defineInRange("mouseSmoothing", 0.35, 0.0, 0.85);
        MOUSE_RETURN = BUILDER.comment("How fast the mouse sticks spring back to center. 0 = they hold their position (old behaviour).")
            .defineInRange("mouseReturn", 0.25, 0.0, 1.0);
        MOUSE_DEADBAND = BUILDER.comment("Ignore tiny mouse input to prevent camera/attitude jitter.")
            .defineInRange("mouseDeadband", 0.025, 0.0, 0.2);
        BUILDER.pop();

        BUILDER.push("controller");
        RC_DEADBAND = BUILDER.comment("RC stick deadband.")
            .defineInRange("deadband", 0.08, 0.0, 0.25);
        RC_EXPO = BUILDER.comment("RC expo. 0 = linear, 0.7 = softer center.")
            .defineInRange("expo", 0.25, 0.0, 0.8);
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
        RC_AXIS_ARM = BUILDER.comment("Arm AUX channel index on RC transmitters (switch axis). -1 = auto-detect on first switch flip. Arm when the axis reads above center.")
            .defineInRange("axisArm", -1, -1, 15);
        RC_INVERT_THROTTLE = BUILDER.comment("Invert throttle axis before mapping to 0..1.")
            .define("invertThrottle", false);
        RC_INVERT_YAW = BUILDER.comment("Invert yaw axis. Default true: RadioMaster Pocket left stick horizontal reports inverted sign in HID mode.")
            .define("invertYaw", true);
        RC_INVERT_PITCH = BUILDER.comment("Invert pitch axis.")
            .define("invertPitch", false);
        RC_INVERT_ROLL = BUILDER.comment("Invert roll axis.")
            .define("invertRoll", false);
        RC_INVERT_ARM = BUILDER.comment("Invert the arm AUX channel (use when your arm switch reads high while disarmed).")
            .define("invertArm", false);
        RC_TX_DEFAULTS_APPLIED = BUILDER.comment("Internal flag: set to true after RC transmitter axis defaults are applied once on first connect.")
            .define("rcTxDefaultsApplied", false);
        RC_TX_DEFAULTS_V2 = BUILDER.comment("Internal flag: v2 migration that corrects invertPitch back to false.")
            .define("rcTxDefaultsV2", false);
        RC_TX_DEFAULTS_V3 = BUILDER.comment("Internal flag: v3 migration that resets throttleMid to 0.5 (linear).")
            .define("rcTxDefaultsV3", false);
        RC_TX_DEFAULTS_V4 = BUILDER.comment("Internal flag: v4 migration that raises chunkRadius to 8 for chunk streaming.")
            .define("rcTxDefaultsV4", false);
        BUILDER.pop();

        BUILDER.push("dev");
        FPV_PROPS_IN_VIEW = BUILDER.comment("Show the front motors and propellers in the FPV view.")
            .define("fpvPropsInView", false);
        FPV_PROPS_FORWARD = BUILDER.comment("How far ahead of the camera the drone model sits (blocks). Bigger = further into the screen / smaller on screen.")
            .defineInRange("fpvPropsForward", 0.80, 0.0, 2.5);
        FPV_PROPS_DOWN = BUILDER.comment("Vertical offset of the drone model from the camera line (blocks). Negative = lower into the bottom of the view.")
            .defineInRange("fpvPropsDown", -0.45, -1.5, 1.0);
        FPV_PROPS_SCALE = BUILDER.comment("Size multiplier for the in-view drone model.")
            .defineInRange("fpvPropsScale", 1.0, 0.2, 3.0);
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
        HUD_DAMAGE = BUILDER.comment("Show bottom-right damage badges for worn props, motors and frame.")
            .define("showDamage", true);
        HUD_NETSTATS = BUILDER.comment("Show LINK (control round-trip) and SRV (server tick time) on the telemetry column.")
            .define("showNetStats", true);
        BUILDER.pop();

        BUILDER.push("sound");
        SOUND_MOTOR_VOLUME = BUILDER.comment("Loudness of drone and plane motors, on top of the vanilla sound sliders.")
            .defineInRange("motorVolume", 0.5, 0.0, 1.0);
        SOUND_EFFECTS_VOLUME = BUILDER.comment("Loudness of every other FPV sound: arming, explosions, jammers, stations.")
            .defineInRange("effectsVolume", 1.0, 0.0, 1.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void save() {
        SPEC.save();
        CommonConfig.SPEC.save();
    }
}
