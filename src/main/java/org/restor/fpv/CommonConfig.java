package org.restor.fpv;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue RC_PITCH_RATE;
    public static final ModConfigSpec.DoubleValue RC_ROLL_RATE;
    public static final ModConfigSpec.DoubleValue RC_YAW_RATE;
    public static final ModConfigSpec.IntValue RADIO_PACKET_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue RADIO_FAILSAFE_HOLD_TICKS;
    public static final ModConfigSpec.IntValue RADIO_FAILSAFE_NEUTRAL_TICKS;
    public static final ModConfigSpec.IntValue RADIO_RECOVERY_PACKETS;
    public static final ModConfigSpec.DoubleValue RADIO_ARM_THROTTLE_MAXIMUM;
    public static final ModConfigSpec.DoubleValue RADIO_FAILSAFE_THROTTLE;
    public static final ModConfigSpec.DoubleValue RADIO_BASE_RANGE;
    public static final ModConfigSpec.DoubleValue RADIO_LOS_PENALTY;
    public static final ModConfigSpec.DoubleValue RADIO_RSSI_EMA_ALPHA;
    public static final ModConfigSpec.IntValue RADIO_RSSI_UPDATE_TICKS;
    public static final ModConfigSpec.DoubleValue RADIO_DEFAULT_VTX_RANGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue REALISTIC_BATTERY;
    public static final ModConfigSpec.BooleanValue REALISTIC_RADIO;
    public static final ModConfigSpec.DoubleValue DETECTOR_RADIUS;
    public static final ModConfigSpec.IntValue DETECTOR_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue JAMMER_RADIUS;
    public static final ModConfigSpec.IntValue JAMMER_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue JAMMER_MAXIMUM_STRENGTH;
    public static final ModConfigSpec.DoubleValue JAMMER_PACKET_LOSS_RSSI;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LINK_CODE;
    public static final ModConfigSpec.BooleanValue REQUIRE_TRANSMITTER;
    public static final ModConfigSpec.BooleanValue REQUIRE_GOGGLES_FOR_FPV;
    public static final ModConfigSpec.DoubleValue TX_RANGE;
    public static final ModConfigSpec.DoubleValue TX_LR_RANGE;
    public static final ModConfigSpec.DoubleValue DEV_POWER_MULT;
    public static final ModConfigSpec.DoubleValue DEV_DRAG_MULT;
    public static final ModConfigSpec.DoubleValue DEV_MAX_SPEED;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_POWER;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_BREAK_PERCENT;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_BLOCK_FLING_PERCENT;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_MOB_DAMAGE_MULT;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_BACKSTEP;
    public static final ModConfigSpec.DoubleValue DEV_EXPLOSION_CAMERA_SECONDS;
    public static final ModConfigSpec.IntValue DEV_CHUNK_RADIUS;
    public static final ModConfigSpec.DoubleValue DEV_HITBOX_SCALE;
    public static final ModConfigSpec.DoubleValue DEV_HITBOX_Y_OFFSET;
    public static final ModConfigSpec.DoubleValue FPV_CAMERA_HEIGHT;
    public static final ModConfigSpec.BooleanValue DEV_NO_DAMAGE;
    public static final ModConfigSpec.BooleanValue INFINITE_AMMO;
    public static final ModConfigSpec.BooleanValue DEV_VERBOSE_LOG;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> QUIET_LOGGERS;
    public static final ModConfigSpec.BooleanValue CLIENT_PHYSICS;
    public static final ModConfigSpec.IntValue PHYSICS_SUBSTEPS;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("controller");
        RC_PITCH_RATE = BUILDER.defineInRange("pitchRate", 1.0, 0.1, 3.0);
        RC_ROLL_RATE = BUILDER.defineInRange("rollRate", 1.0, 0.1, 3.0);
        RC_YAW_RATE = BUILDER.defineInRange("yawRate", 1.0, 0.1, 3.0);
        RADIO_PACKET_TIMEOUT_TICKS = BUILDER.defineInRange("packetTimeoutTicks", 10, 1, 200);
        RADIO_FAILSAFE_HOLD_TICKS = BUILDER.defineInRange("failsafeHoldTicks", 4, 0, 200);
        RADIO_FAILSAFE_NEUTRAL_TICKS = BUILDER.defineInRange("failsafeNeutralTicks", 10, 0, 400);
        RADIO_RECOVERY_PACKETS = BUILDER.defineInRange("recoveryPackets", 10, 1, 200);
        RADIO_ARM_THROTTLE_MAXIMUM = BUILDER.defineInRange("armThrottleMaximum", 0.05, 0.0, 0.25);
        RADIO_FAILSAFE_THROTTLE = BUILDER.defineInRange("failsafeThrottle", 0.0, 0.0, 1.0);
        RADIO_BASE_RANGE = BUILDER.defineInRange("baseRange", 512.0, 16.0, 8192.0);
        RADIO_LOS_PENALTY = BUILDER.defineInRange("losPenalty", 0.35, 0.0, 1.0);
        RADIO_RSSI_EMA_ALPHA = BUILDER.defineInRange("rssiEmaAlpha", 0.15, 0.01, 1.0);
        RADIO_RSSI_UPDATE_TICKS = BUILDER.defineInRange("rssiUpdateTicks", 5, 1, 100);
        RADIO_DEFAULT_VTX_RANGE_MULTIPLIER = BUILDER.defineInRange("defaultVtxRangeMultiplier", 1.0, 0.1, 10.0);
        REALISTIC_BATTERY = BUILDER.comment("Drain batteries and apply voltage sag. Disable for unlimited flight time.")
            .define("realisticBattery", true);
        REALISTIC_RADIO = BUILDER.comment("Apply radio range, line-of-sight loss and jammer interference.")
            .define("realisticRadio", true);
        BUILDER.pop();

        BUILDER.push("anti_drone");
        DETECTOR_RADIUS = BUILDER.defineInRange("detectorRadius", 32.0, 4.0, 128.0);
        DETECTOR_SCAN_INTERVAL_TICKS = BUILDER.defineInRange("detectorScanIntervalTicks", 10, 5, 200);
        JAMMER_RADIUS = BUILDER.defineInRange("jammerRadius", 24.0, 4.0, 128.0);
        JAMMER_SCAN_INTERVAL_TICKS = BUILDER.defineInRange("jammerScanIntervalTicks", 10, 5, 200);
        JAMMER_MAXIMUM_STRENGTH = BUILDER.defineInRange("jammerMaximumStrength", 1.0, 0.05, 1.0);
        JAMMER_PACKET_LOSS_RSSI = BUILDER.defineInRange("jammerPacketLossRssi", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("remote");
        DEFAULT_LINK_CODE = BUILDER.define("defaultLinkCode", "");
        REQUIRE_TRANSMITTER = BUILDER.comment("Flying a drone or plane requires an RC transmitter in hand.")
            .define("requireTransmitter", true);
        REQUIRE_GOGGLES_FOR_FPV = BUILDER.comment("FPV view requires goggles; without them you fly line-of-sight.")
            .define("requireGogglesForFpv", true);
        TX_RANGE = BUILDER.defineInRange("transmitterRange", 256.0, 16.0, 8192.0);
        TX_LR_RANGE = BUILDER.defineInRange("transmitterLongRange", 1024.0, 16.0, 16384.0);
        BUILDER.pop();

        BUILDER.push("dev");
        DEV_POWER_MULT = BUILDER.defineInRange("powerMultiplier", 1.0, 0.2, 5.0);
        DEV_DRAG_MULT = BUILDER.defineInRange("dragMultiplier", 1.0, 0.1, 6.0);
        DEV_MAX_SPEED = BUILDER.comment("Quadcopter speed ceiling in m/s. Only cuts speed; thrust and drag decide how fast a drone really goes. Planes use their frame limit.")
            .defineInRange("maxSpeed", 60.0, 10.0, 150.0);
        DEV_EXPLOSION_POWER = BUILDER
            .comment("Global warhead scale. 4.0 is 1x; every family keeps its own blast/crater ratio.")
            .defineInRange("explosionPower", 4.0, 0.4, 40.0);
        DEV_EXPLOSION_BLOCK_BREAK_PERCENT = BUILDER.defineInRange("explosionBlockBreakPercent", 100.0, 0.0, 100.0);
        DEV_EXPLOSION_BLOCK_FLING_PERCENT = BUILDER.defineInRange("explosionBlockFlingPercent", 100.0, 0.0, 200.0);
        DEV_EXPLOSION_MOB_DAMAGE_MULT = BUILDER.defineInRange("explosionMobDamageMultiplier", 1.0, 0.0, 5.0);
        DEV_EXPLOSION_CAMERA_BACKSTEP = BUILDER.defineInRange("explosionCameraBackstep", 8.0, 0.0, 40.0);
        DEV_EXPLOSION_CAMERA_SECONDS = BUILDER.defineInRange("explosionCameraSeconds", 5.0, 0.0, 6.0);
        DEV_CHUNK_RADIUS = BUILDER
            .comment("Chunk radius streamed to a remote pilot. 0 disables the extra streaming entirely.")
            .defineInRange("chunkRadius", 6, 0, 12);
        DEV_HITBOX_SCALE = BUILDER.defineInRange("hitboxScale", 1.0, 0.2, 3.0);
        DEV_HITBOX_Y_OFFSET = BUILDER.defineInRange("hitboxYOffset", 0.0, -1.0, 1.0);
        FPV_CAMERA_HEIGHT = BUILDER.defineInRange("fpvCameraHeight", 0.5, 0.0, 1.5);
        DEV_NO_DAMAGE = BUILDER.comment("Drones and their components take no wear or crash damage.")
            .define("noDamage", false);
        INFINITE_AMMO = BUILDER.comment("Rocket pods and payload bays fire without using up loaded ammo.")
            .define("infiniteAmmo", false);
        DEV_VERBOSE_LOG = BUILDER.comment("Write per-tick flight/controller/chunk traces to the log. Costly: only for debugging.")
            .define("verboseLog", false);
        BUILDER.pop();

        BUILDER.push("performance");
        QUIET_LOGGERS = BUILDER
            .comment("Loggers to quiet down at startup, as \"prefix\" or \"prefix=LEVEL\" (default level WARN).",
                     "Some physics mods log every sub-level move at INFO, which stalls the server thread on disk I/O.",
                     "Leave the list empty to touch nothing.")
            .defineListAllowEmpty(java.util.List.of("quietLoggers"),
                () -> java.util.List.of("dev.ryanhcode.sable=WARN"),
                o -> o instanceof String s && !s.isBlank());
        CLIENT_PHYSICS = BUILDER
            .comment("Simulate the drone you pilot on your own client and send the resulting pose to the server.",
                     "Keeps the drone responsive when the server tick is slow, at the cost of server authority.")
            .define("clientSidePhysics", true);
        PHYSICS_SUBSTEPS = BUILDER
            .comment("Physics integration steps per game tick for quadcopters. 4 = 80 Hz.",
                     "One 20 Hz step moves a fast drone three blocks at a time, which integrates",
                     "drag badly and can step straight through a wall. Higher costs more CPU.")
            .defineInRange("physicsSubsteps", 4, 1, 8);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CommonConfig() {}
}
