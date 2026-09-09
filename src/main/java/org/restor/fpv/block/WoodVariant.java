package org.restor.fpv.block;

import net.minecraft.util.StringRepresentable;

// Wood the assembly station was crafted from; carried on the item by the vanilla
// minecraft:block_state component and copied back on break.
public enum WoodVariant implements StringRepresentable {
    OAK("oak"),
    SPRUCE("spruce"),
    BIRCH("birch"),
    JUNGLE("jungle"),
    ACACIA("acacia"),
    DARK_OAK("dark_oak"),
    MANGROVE("mangrove"),
    CHERRY("cherry"),
    BAMBOO("bamboo"),
    CRIMSON("crimson"),
    WARPED("warped");

    private final String name;

    WoodVariant(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
