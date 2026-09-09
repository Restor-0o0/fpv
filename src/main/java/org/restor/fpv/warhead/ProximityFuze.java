package org.restor.fpv.warhead;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Airburst fuze for interceptors: arms like a contact fuze, then fires as soon as a valid
 * target comes inside the burst radius — the drone never has to actually hit anything.
 * The self-destruct timer keeps a missed intercept from loitering armed forever.
 */
public final class ProximityFuze {
    private static final int ARM_TICKS = 20;
    private static final double ARM_RANGE = 3.0;
    private static final double ARM_CLIMB = 1.5;
    public static final double TRIGGER_RADIUS = 2.75;

    private Vec3 pad;
    private int airborneTicks;
    private boolean armed;

    public boolean isArmed() { return armed; }

    public void disarm() {
        armed = false;
        airborneTicks = 0;
        pad = null;
    }

    /** Call once per server tick; true means detonate now. */
    public boolean tick(Entity craft, boolean powered, double radius, Predicate<Entity> target) {
        if (!powered) {
            disarm();
            return false;
        }

        Vec3 position = craft.position();
        if (craft.onGround()) {
            airborneTicks = 0;
            if (!armed) pad = position;
        } else {
            airborneTicks++;
            if (pad == null) pad = position;
        }

        if (!armed) {
            armed = airborneTicks >= ARM_TICKS
                && (position.distanceTo(pad) >= ARM_RANGE || position.y - pad.y >= ARM_CLIMB);
            return false;
        }

        return !craft.level().getEntities(craft, craft.getBoundingBox().inflate(radius), target).isEmpty();
    }
}
