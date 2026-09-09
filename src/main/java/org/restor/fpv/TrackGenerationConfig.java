package org.restor.fpv;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TrackGenerationConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue REGION_CHANCE;
    public static final ForgeConfigSpec.IntValue SPACING_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_REGIONS;
    public static final ForgeConfigSpec.IntValue PENDING_SCAN_PER_TICK;
    public static final ForgeConfigSpec.IntValue PLANNER_TERRAIN_CHECK_BUDGET;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("naturalTracks");
        ENABLED = BUILDER.define("enabled", true);
        REGION_CHANCE = BUILDER.defineInRange("regionChance", 0.04, 0.0, 1.0);
        SPACING_CHUNKS = BUILDER.defineInRange("spacingChunks", 32, 8, 128);
        MAX_PENDING_REGIONS = BUILDER.defineInRange("maxPendingRegions", 256, 16, 4096);
        PENDING_SCAN_PER_TICK = BUILDER.defineInRange("pendingScanPerTick", 32, 1, 256);
        PLANNER_TERRAIN_CHECK_BUDGET = BUILDER.defineInRange("plannerTerrainCheckBudget", 250_000, 1_000, 2_000_000);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private TrackGenerationConfig() {}
}
