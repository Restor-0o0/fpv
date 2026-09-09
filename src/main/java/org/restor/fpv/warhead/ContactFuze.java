package org.restor.fpv.warhead;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Impact fuze shared by the kamikaze builds. It stays safe until the craft has actually
 * flown clear of the spot it lifted off from, and then needs a real deceleration to fire,
 * so a wobbly climb-out, a scrape along the runway or setting back down is survivable.
 */
public final class ContactFuze {
    private static final int ARM_TICKS = 20;
    private static final double ARM_RANGE = 3.0;
    private static final double ARM_CLIMB = 1.5;
    // Blocks/tick of speed lost in one tick (~3 m/s). Thrust and gravity together move a
    // craft by well under a tenth of this per tick, so only a collision can reach it.
    private static final double FIRE_DECELERATION = 0.15;

    private Vec3 pad;
    private Vec3 lastVelocity = Vec3.ZERO;
    private double reportedImpact;
    private int airborneTicks;
    private boolean armed;

    public boolean isArmed() { return armed; }

    /**
     * A collision the craft measured itself. Client-side flight physics resolves hits between
     * server ticks, so the pilot's own impact vector is a truer reading than the velocity
     * difference the server can see.
     */
    public void reportImpact(double magnitude) {
        reportedImpact = Math.max(reportedImpact, magnitude);
    }

    /** Call once per server tick after the craft has moved; true means detonate now. */
    public boolean tick(Entity craft, boolean powered) {
        Vec3 velocity = craft.getDeltaMovement();
        double deceleration = Math.max(lastVelocity.subtract(velocity).length(), reportedImpact);
        lastVelocity = velocity;
        reportedImpact = 0;

        if (!powered) {
            armed = false;
            airborneTicks = 0;
            pad = null;
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

        boolean contact = craft.horizontalCollision || craft.verticalCollision || craft.onGround();
        return contact && deceleration >= FIRE_DECELERATION;
    }
}
