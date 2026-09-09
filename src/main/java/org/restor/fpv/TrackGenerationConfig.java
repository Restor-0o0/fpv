package org.restor.fpv;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrackGenerationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue REGION_CHANCE;
    public static final ModConfigSpec.IntValue SPACING_CHUNKS;
    public static final ModConfigSpec.IntValue MAX_PENDING_REGIONS;
    public static final ModConfigSpec.IntValue PENDING_SCAN_PER_TICK;
    public static final ModConfigSpec.IntValue PLANNER_TERRAIN_CHECK_BUDGET;
    public static final ModConfigSpec SPEC;

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
