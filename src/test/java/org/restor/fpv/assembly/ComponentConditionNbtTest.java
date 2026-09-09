package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentConditionNbtTest {
    @Test
    void missingConditionLoadsAsUndamaged() {
        assertEquals(ComponentCondition.MAX, ComponentCondition.read(new CompoundTag()));
    }

    @Test
    void conditionRoundTripsThroughNbt() {
        CompoundTag tag = new CompoundTag();
        ComponentCondition.write(tag, 625);
        assertTrue(tag.contains(ComponentCondition.NBT_KEY));
        assertEquals(625, ComponentCondition.read(tag));
    }

    @Test
    void malformedRangeIsClamped() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ComponentCondition.NBT_KEY, 1400);
        assertEquals(ComponentCondition.MAX, ComponentCondition.read(tag));
        tag.putInt(ComponentCondition.NBT_KEY, -20);
        assertEquals(0, ComponentCondition.read(tag));
    }
}
