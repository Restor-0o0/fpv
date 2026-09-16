package org.restor.fpv.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.restor.fpv.rules.ServerRule;

// Simple fixed-wing FPV plane: thrust + airspeed-driven lift/drag, banked-turn yaw
// coordination. Riding-only (no remote-control station like the drones have).
public class PlaneEntity extends Entity
    implements Pilotable, org.restor.fpv.antidrone.RadioInterferenceReceiver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EntityDataAccessor<Float>   DATA_ROLL         = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_THROTTLE     = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED        = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float>   DATA_YAW_SYNC     = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_PITCH_SYNC   = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_ROLL  = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_PITCH = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_YAW   = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    // Orbit autopilot params, synced so the client predicts the exact same circle locally.
    private static final EntityDataAccessor<Boolean> DATA_ORBIT        = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float>   DATA_ORB_CX       = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ORB_CZ       = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ORB_R        = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ORB_DIR      = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ORB_Y        = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);

    // Same SI convention as DroneEntity: 1 block = 1 m, velocities in blocks/tick.
    protected static final float GRAVITY = 9.81f;
    private static final float MS2_TO_BT = 0.05f / 20f;

    // Airframe constants shared by all plane types.

    private static final float ALIGN_RESPONSIVENESS = 6f; // 1/s, max flight-path-to-nose alignment rate at speed
    private static final float TURN_AUTHORITY = 1.8f;  // nose leads the lift-driven turn by this much
    private static final float MAX_TURN_RATE = 85f;    // deg/s cap on the bank-driven heading change

    // Per-type airframe tuning — subclasses override in their constructor.
    protected float maxThrust = 320f;                  // N
    protected float stallSpeed = 6.0f;                 // m/s, lift fades out below this
    protected float liftCoeff = 0.12f;                 // lift balances gravity at ~20 m/s
    protected float maxLiftAccel = GRAVITY * 1.6f;     // cap climb force so extra speed doesn't rocket up
    protected float maxSpeedBt = 4.0f;                 // hard speed cap in blocks/tick (×20 = m/s)
    protected float dryMass = 30f;                     // kg, empty airframe
    protected float wingArea = 6f;                     // arbitrary lift-coefficient scale
    protected float maxRollRate = 180f;                // deg/s
    protected float maxPitchRate = 95f;                // deg/s
    protected float maxYawRate = 55f;                  // deg/s (rudder trim, on top of bank-turn)
    protected float rotationResponsiveness = 9f;
    protected float dragCoeff = 0.045f;

    public float rollO;
    private float displayRoll;
    private final Quaternionf attitude = new Quaternionf();
    private boolean attitudeValid;
    private float throttleInput;
    private float angVelRoll, angVelPitch, angVelYaw;
    private boolean armed;
    private boolean pilotWasMounted;

    private boolean useController;
    private float ctrlThrottle, ctrlPitch, ctrlRoll, ctrlYaw;
    private boolean ctrlArm;
    private int ctrlAge;
    private int serverLogTick;

    // Client-side prediction (local pilot only), mirrors DroneEntity's approach.
    private float predThrottle, predPitch, predRoll, predYaw;
    private int predAge = 100;
    private Vec3 serverSyncPos;

    // Belly-camera gimbal look (client-only, local pilot's mouse look while in
    // "camera mode"). Never synced — meaningless to anyone but the pilot's own client.
    private float gimbalYaw, gimbalPitch;
    private boolean stabilizedGimbalView;

    private Vec3 mountReturnPos;
    private java.util.UUID dummyId;

    private final org.restor.fpv.antidrone.RadioJamState jamState = new org.restor.fpv.antidrone.RadioJamState();
    private static final float JAM_LINK_LOSS = 0.25f;

    // Orbit autopilot: circle a fixed point (the camera-lock target) at a set radius.
    private static final float ORBIT_RADIUS_MARGIN = 3.0f;
    private boolean orbitActive;
    private Vec3 orbitCenter;
    private double orbitRadius;
    private double orbitY;
    private int orbitDir = 1; // +1 CCW / -1 CW, chosen from motion at engage
    private float orbitBlend;
    private boolean orbitWasActive;
    private float orbitRollIn, orbitPitchIn, orbitYawIn;
    private float orbitTargetRoll;
    private float orbitDisplayRoll;

    public void setOrbit(boolean active, Vec3 center, double radius) {
        orbitActive = active;
        entityData.set(DATA_ORBIT, active);
        resetOrbitSmoothing();
        if (active) {
            orbitCenter = center;
            orbitRadius = Math.max(4.0, radius + ORBIT_RADIUS_MARGIN);
            orbitY = getY();
            Vec3 v = getDeltaMovement();
            double rx = getX() - center.x, rz = getZ() - center.z;
            double cross = rx * v.z - rz * v.x; // sign picks the current rotation sense
            orbitDir = cross >= 0 ? 1 : -1;
            entityData.set(DATA_ORB_CX, (float) center.x);
            entityData.set(DATA_ORB_CZ, (float) center.z);
            entityData.set(DATA_ORB_R, (float) orbitRadius);
            entityData.set(DATA_ORB_DIR, (float) orbitDir);
            entityData.set(DATA_ORB_Y, (float) orbitY);
        } else {
            orbitCenter = null;
        }
    }

    private void resetOrbitSmoothing() {
        orbitBlend = 0f;
        orbitRollIn = 0f;
        orbitPitchIn = 0f;
        orbitYawIn = 0f;
        orbitTargetRoll = 0f;
        orbitDisplayRoll = 0f;
    }

    public boolean isOrbiting() { return orbitActive || entityData.get(DATA_ORBIT); }

    public PlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_ROLL,         0f);
        builder.define(DATA_THROTTLE,     0f);
        builder.define(DATA_ARMED,        false);
        builder.define(DATA_YAW_SYNC,     0f);
        builder.define(DATA_PITCH_SYNC,   0f);
        builder.define(DATA_ANGVEL_ROLL,  0f);
        builder.define(DATA_ANGVEL_PITCH, 0f);
        builder.define(DATA_ANGVEL_YAW,   0f);
        builder.define(DATA_ORBIT,        false);
        builder.define(DATA_ORB_CX,       0f);
        builder.define(DATA_ORB_CZ,       0f);
        builder.define(DATA_ORB_R,        12f);
        builder.define(DATA_ORB_DIR,      1f);
        builder.define(DATA_ORB_Y,        0f);
    }

    @Override
    public void tick() {
        rollO = displayRoll;
        super.tick();
        if (isNoGravity()) setNoGravity(false);

        if (!level().isClientSide) serverTick();
        else clientTick();
    }

    // -------------------------------------------------------------------------
    //  Server tick
    // -------------------------------------------------------------------------

    private void serverTick() {
        ServerPlayer pilot = getControllingPassenger() instanceof ServerPlayer sp ? sp : null;
        if (jamState.refresh(level().getGameTime()) > JAM_LINK_LOSS) {
            // Link lost: the failsafe cuts the motor and centres the surfaces, so a jammed
            // plane glides down instead of flying on.
            ctrlThrottle = 0f;
            ctrlPitch = 0f;
            ctrlRoll = 0f;
            ctrlYaw = 0f;
            ctrlArm = false;
            if (orbitActive) setOrbit(false, Vec3.ZERO, 0);
        }
        if (pilot != null) {
            // Keep a hard-loaded bubble around the plane so flying far and fast never
            // outruns chunk loading (which was dumping the pilot back to the launch point).
            updateForcedChunks();
            updateTargetStreaming(pilot);
            if (!pilotWasMounted) {
                angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
                resetAttitudeFromEuler(entityData.get(DATA_ROLL));
                pilotWasMounted = true;
            }

            if (orbitActive) {
                doOrbit();
            } else {
                if (useController) {
                    ctrlAge++;
                    if (ctrlAge > 40) clearControllerInput();
                }

                throttleInput = Math.max(0f, Math.min(1f, ctrlThrottle));
                float pitchTarget = applyDeadband(ctrlPitch, 0.05f);
                float rollTarget  = applyDeadband(ctrlRoll,  0.05f);
                float yawTarget   = applyDeadband(ctrlYaw,   0.05f);
                boolean wasArmed = armed;
                if (ctrlArm) armed = true;
                if (!wasArmed && armed && throttleInput > 0.10f
                    && getDeltaMovement().horizontalDistanceSqr() < 0.0025) {
                    handLaunch();
                }
                if (armed && throttleInput < 0.01f && onGround()) armed = false;

                float dt = 0.05f;
                if (armed) {
                    applyFlightPhysics(dt, throttleInput, pitchTarget, rollTarget, yawTarget);
                } else {
                    angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
                }
                applyAerodynamics(dt);
                applyGravity();
            }
        } else {
            setOrbit(false, Vec3.ZERO, 0);
            pilotWasMounted = false;
            armed = false;
            throttleInput = 0;
            angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
            // Parked planes get no aerodynamics: lift plus the velocity-to-nose alignment
            // turned the gravity tick into forward speed, so an untouched plane crept away.
            if (!onGround()) applyAerodynamics(0.05f);
            applyGravity();
            clearForcedChunks();
            clearTargetStreaming();
        }

        if (!(pilot != null && orbitActive)) applyMovement();

        if (pilot != null) {
            serverLogTick++;
            if (org.restor.fpv.CommonConfig.DEV_VERBOSE_LOG.get() && serverLogTick >= 20) {
                serverLogTick = 0;
                LOGGER.info("[PLANE-SRV] ctrlThr={} armed={} thrIn={} vel={} yaw={} pitch={} roll={} onGround={}",
                    String.format("%.3f", ctrlThrottle),
                    armed,
                    String.format("%.3f", throttleInput),
                    getDeltaMovement(),
                    String.format("%.1f", getYRot()),
                    String.format("%.1f", getXRot()),
                    String.format("%.1f", entityData.get(DATA_ROLL)),
                    onGround());
            }
        }

        entityData.set(DATA_THROTTLE, throttleInput);
        entityData.set(DATA_ARMED, armed);
        entityData.set(DATA_YAW_SYNC, getYRot());
        entityData.set(DATA_PITCH_SYNC, getXRot());
        entityData.set(DATA_ANGVEL_ROLL, angVelRoll);
        entityData.set(DATA_ANGVEL_PITCH, angVelPitch);
        entityData.set(DATA_ANGVEL_YAW, angVelYaw);
    }

    private void applyFlightPhysics(float dt, float throttle, float pitchIn, float rollIn, float yawIn) {
        float thrustAccel = throttle * maxThrust / totalMass();
        float curRoll = entityData.get(DATA_ROLL);
        Vec3 fwd = bodyForward(curRoll);
        float dv = thrustAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(fwd.x * dv, fwd.y * dv, fwd.z * dv));

        // Full-manual / acro: stick commands a BODY-frame rotation RATE for both roll and
        // pitch — no self-leveling and no angle limits, so full-stick gives unlimited rolls
        // and loops and the plane holds whatever attitude you leave it in. Body-frame is
        // what makes it feel like a plane: banked + elevator carves a turn, not a climb.
        float targetRollRate  = clamp(rollIn,  -1f, 1f) * maxRollRate;
        float targetPitchRate = clamp(pitchIn, -1f, 1f) * maxPitchRate;
        // Pure rudder only — no bank-induced auto-yaw. At steep bank the body-yaw axis is
        // near-horizontal, so auto-coordinating yaw there rotated the nose UP in the world
        // (the "banked 90° + elevator pulls up instead of turning" bug). Turns now come
        // purely from body-pitch (elevator) swinging the nose around, like real acro flight.
        float targetYawRate = -clamp(yawIn, -1f, 1f) * maxYawRate;

        float resp = Math.min(rotationResponsiveness * dt, 1.0f);
        angVelRoll  += (targetRollRate  - angVelRoll)  * resp;
        angVelPitch += (targetPitchRate - angVelPitch) * resp;
        angVelYaw   += (targetYawRate   - angVelYaw)   * resp;

        float newRoll = integrateRotation(dt, angVelRoll, angVelPitch, angVelYaw, curRoll);
        newRoll = applyCoordinatedTurn(dt, newRoll);
        entityData.set(DATA_ROLL, newRoll);
    }

    /**
     * Banked lift pulls the aircraft sideways; without swinging the nose with it the airframe
     * just slips and the flight-path alignment drags it straight again. This is what makes a
     * roll actually turn the plane.
     */
    private float applyCoordinatedTurn(float dt, float roll) {
        double speedMs = getDeltaMovement().length() * 20.0;
        if (speedMs < stallSpeed * 0.8) return roll;
        Vector3f up = bodyUp(roll);
        Vec3 fwd = bodyForward(roll);
        double fh = Math.hypot(fwd.x, fwd.z);
        if (fh < 1e-3) return roll;
        double lateral = (fwd.z * up.x - fwd.x * up.z) / fh;
        if (Math.abs(lateral) < 1e-4) return roll;

        float liftFactor = (float) Math.min(1.0, speedMs / stallSpeed);
        double liftAccel = Math.min(maxLiftAccel,
            liftFactor * liftCoeff * wingArea * speedMs * speedMs / totalMass());
        double rate = Math.toDegrees(liftAccel * lateral * TURN_AUTHORITY / speedMs);
        rate = clamp((float) rate, -MAX_TURN_RATE, MAX_TURN_RATE);

        attitude.rotateLocalY((float) Math.toRadians(rate * dt)).normalize();
        float[] a = DroneEntity.decomposeAttitude(attitude);
        setYRot(normalizeYaw(a[0]));
        setXRot(a[1]);
        return a[2];
    }

    private void handLaunch() {
        // Fixed wings have no wheel/ground-roll model, and lift only balances weight well
        // above stall speed — a low-lift airframe thrown at walking pace sinks back into the
        // ground long before thrust can build the airspeed it needs. So the throw puts it at
        // flying speed straight away and gives it a little climb to work with.
        Vec3 forward = bodyForward(entityData.get(DATA_ROLL)).normalize();
        double speedMs = Math.min(maxSpeedBt * 20.0 * 0.85,
            Math.max(stallSpeed * 1.25, levelFlightSpeed() * 0.95));
        double launchSpeed = speedMs / 20.0;
        setPos(getX(), getY() + 0.35, getZ());
        setDeltaMovement(forward.x * launchSpeed, Math.max(0.15, forward.y * launchSpeed),
            forward.z * launchSpeed);
    }

    /** Airspeed at which lift balances weight — below it the airframe sinks whatever the stick says. */
    protected float levelFlightSpeed() {
        return (float) Math.sqrt(GRAVITY * totalMass() / (liftCoeff * wingArea));
    }

    private float integrateRotation(float dt, float rRoll, float rPitch, float rYaw, float curRoll) {
        ensureAttitude(curRoll);
        attitude
            .rotateX((float) Math.toRadians(rPitch * dt))
            .rotateY((float) Math.toRadians(-rYaw * dt))
            .rotateZ((float) Math.toRadians(rRoll * dt))
            .normalize();
        float[] a = DroneEntity.decomposeAttitude(attitude);
        setYRot(normalizeYaw(a[0]));
        setXRot(a[1]);
        return a[2];
    }

    private void ensureAttitude(float roll) {
        if (!attitudeValid) resetAttitudeFromEuler(roll);
    }

    private void resetAttitudeFromEuler(float roll) {
        attitude.set(DroneEntity.attitudeQuat(getYRot(), getXRot(), roll));
        attitudeValid = true;
    }

    private void setAttitudeFromEuler(float yaw, float pitch, float roll) {
        setYRot(yaw);
        setXRot(pitch);
        attitude.set(DroneEntity.attitudeQuat(yaw, pitch, roll));
        attitudeValid = true;
    }

    private float setAttitudeFromQuat(Quaternionf q) {
        attitude.set(q).normalize();
        attitudeValid = true;
        float[] a = DroneEntity.decomposeAttitude(attitude);
        setYRot(normalizeYaw(a[0]));
        setXRot(a[1]);
        return a[2];
    }

    private void applyAerodynamics(float dt) {
        Vec3 vel = getDeltaMovement();
        double speedMs = vel.length() * 20.0;
        if (speedMs < 1e-3) return;

        // Lift acts along body-up, fades to zero below stall speed (arcade stall model).
        float roll = entityData.get(DATA_ROLL);
        Vector3f up = bodyUp(roll);

        float liftFactor = (float) Math.min(1.0, speedMs / stallSpeed);
        // liftCoeff tuned so lift balances gravity at the type's cruise speed; capped by
        // maxLiftAccel so extra speed doesn't rocket the plane vertically.
        float liftAccel = Math.min(maxLiftAccel, liftFactor * liftCoeff * wingArea * (float) (speedMs * speedMs) / totalMass());
        float liftDv = liftAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(up.x * liftDv, up.y * liftDv, up.z * liftDv));

        // Quadratic drag opposing velocity.
        vel = getDeltaMovement();
        double dragAccel = 0.5 * 1.225 * dragCoeff * speedMs * speedMs / totalMass();
        double drag = Math.min(dragAccel * MS2_TO_BT, vel.length() * 0.5);
        if (vel.length() > 1e-4) {
            setDeltaMovement(vel.subtract(vel.normalize().scale(drag)));
        }

        // Bend the flight path toward the nose once there's enough airspeed for control
        // surfaces to bite. Without this, only a perpendicular lift force nudges velocity,
        // so after a fast attitude change the plane can keep coasting along its old
        // velocity vector — flying sideways/belly-first instead of turning with the nose.
        // Effectiveness ramps in with speed, like real control-surface authority.
        vel = getDeltaMovement();
        double curSpeed = vel.length();
        if (curSpeed > 1e-4) {
                Vec3 fwd = bodyForward(roll);
                float speedFactor = (float) Math.min(1.0, Math.max(0.0, (speedMs - stallSpeed) / stallSpeed));
                float alignBlend = (float) Math.min(1.0, ALIGN_RESPONSIVENESS * speedFactor * dt);
            if (alignBlend > 0f) {
                Vec3 blended = vel.normalize().scale(1 - alignBlend).add(fwd.scale(alignBlend));
                if (blended.lengthSqr() > 1e-8) {
                    setDeltaMovement(blended.normalize().scale(curSpeed));
                }
            }
        }
    }

    private Vec3 bodyForward(float roll) {
        ensureAttitude(roll);
        Vector3f fwd = new Vector3f(0, 0, 1);
        attitude.transform(fwd);
        return new Vec3(fwd.x, fwd.y, fwd.z);
    }

    private Vector3f bodyUp(float roll) {
        ensureAttitude(roll);
        Vector3f up = new Vector3f(0, 1, 0);
        attitude.transform(up);
        return up;
    }

    protected void applyGravity() {
        setDeltaMovement(getDeltaMovement().add(0, -GRAVITY * MS2_TO_BT, 0));
        Vec3 vel = getDeltaMovement();
        double max = maxSpeedBt;
        if (vel.lengthSqr() > max * max) {
            setDeltaMovement(vel.scale(max / vel.length()));
        }
    }

    private void applyMovement() {
        Vec3 vel = getDeltaMovement();
        if (onGround()) {
            // Only clip the sink rate so the plane doesn't dig into the ground each tick —
            // killing horizontal speed too (as a prior version did) capped ground-roll speed
            // at a crawl far below stall speed, so the plane could never build up to takeoff.
            // Ground friction applies only when it isn't under power, so an idle plane stops.
            double vy = Math.min(vel.y, 0) < 0 ? 0 : vel.y;
            double vx = vel.x;
            double vz = vel.z;
            if (!armed) {
                vx = Math.abs(vx) < 0.002 ? 0 : vx * 0.6;
                vz = Math.abs(vz) < 0.002 ? 0 : vz * 0.6;
            }
            vel = new Vec3(vx, vy, vz);
            setDeltaMovement(vel);
        }
        move(MoverType.SELF, vel);
    }

    // -------------------------------------------------------------------------
    //  Client tick — mirrors DroneEntity's prediction/slerp-correction approach.
    // -------------------------------------------------------------------------

    private void clientTick() {
        float dt = 0.05f;
        float target = entityData.get(DATA_THROTTLE);
        throttleInput += (target - throttleInput) * 0.4f;

        boolean orbitSynced = entityData.get(DATA_ORBIT);
        if (orbitSynced) {
            if (!orbitWasActive) {
                resetOrbitSmoothing();
                orbitWasActive = true;
            }
            flyOrbit(new Vec3(entityData.get(DATA_ORB_CX), entityData.get(DATA_ORB_Y), entityData.get(DATA_ORB_CZ)),
                entityData.get(DATA_ORB_R), Math.round(entityData.get(DATA_ORB_DIR)), entityData.get(DATA_ORB_Y));
            displayRoll = entityData.get(DATA_ROLL);
            predAge = 100;
            if (serverSyncPos != null) {
                Vec3 delta = serverSyncPos.subtract(position());
                double d2 = delta.lengthSqr();
                if (d2 > 144.0) {
                    setPos(serverSyncPos.x, serverSyncPos.y, serverSyncPos.z);
                } else if (d2 > 1.0e-4) {
                    Vec3 p = position().add(delta.scale(0.06));
                    setPos(p.x, p.y, p.z);
                }
            }
            return;
        } else if (orbitWasActive) {
            orbitWasActive = false;
            resetOrbitSmoothing();
        }

        predAge++;
        boolean localPilot = predAge <= 5;
        if (localPilot) {
            boolean isArmed = entityData.get(DATA_ARMED);
            if (isArmed) {
                float pitchIn = applyDeadband(predPitch, 0.05f);
                float rollIn  = applyDeadband(predRoll,  0.05f);
                float yawIn   = applyDeadband(predYaw,   0.05f);
                float curRoll = displayRoll;

                float targetRollRate  = clamp(rollIn, -1f, 1f) * maxRollRate;
                float targetPitchRate = clamp(pitchIn, -1f, 1f) * maxPitchRate;
                float targetYawRate = -clamp(yawIn, -1f, 1f) * maxYawRate;
                float resp = Math.min(rotationResponsiveness * dt, 1.0f);
                angVelRoll  += (targetRollRate  - angVelRoll)  * resp;
                angVelPitch += (targetPitchRate - angVelPitch) * resp;
                angVelYaw   += (targetYawRate   - angVelYaw)   * resp;

                displayRoll = integrateRotation(dt, angVelRoll, angVelPitch, angVelYaw, curRoll);
                displayRoll = applyCoordinatedTurn(dt, displayRoll);
            } else {
                angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
            }

            Quaternionf qc = DroneEntity.attitudeQuat(getYRot(), getXRot(), displayRoll);
            Quaternionf qs = DroneEntity.attitudeQuat(entityData.get(DATA_YAW_SYNC),
                entityData.get(DATA_PITCH_SYNC), entityData.get(DATA_ROLL));
            qc.slerp(qs, 0.08f);
            displayRoll = applyAttitudeClient(qc);
        } else {
            displayRoll = entityData.get(DATA_ROLL);
            setAttitudeFromEuler(entityData.get(DATA_YAW_SYNC), entityData.get(DATA_PITCH_SYNC), displayRoll);
        }

        if (localPilot) {
            move(MoverType.SELF, getDeltaMovement());
            if (serverSyncPos != null) {
                Vec3 delta = serverSyncPos.subtract(position());
                double d2 = delta.lengthSqr();
                // Speed-scaled snap tolerance + blend (same reasoning as DroneEntity): batched
                // server move packets arrive several blocks away at high airspeed, so a fixed
                // 5-block snap threshold would stutter periodically when flying fast.
                double speed = getDeltaMovement().length();       // blocks/tick
                double snapDist = Math.max(5.0, speed * 8.0);
                double blend = Math.min(0.30, 0.15 + speed * 0.10);
                if (d2 > snapDist * snapDist) {
                    setPos(serverSyncPos.x, serverSyncPos.y, serverSyncPos.z);
                } else if (d2 > 1.0e-4) {
                    Vec3 p = position().add(delta.scale(blend));
                    setPos(p.x, p.y, p.z);
                }
            }
        } else if (serverSyncPos != null) {
            // Not predicting locally (e.g. orbit autopilot, where the client sends no stick
            // inputs): the client's own velocity is stale, so DON'T integrate it — smoothly
            // ease toward the authoritative position instead. Prevents drift-then-snap jitter.
            Vec3 p = position().add(serverSyncPos.subtract(position()).scale(0.35));
            setPos(p.x, p.y, p.z);
        } else {
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    private float applyAttitudeClient(Quaternionf q) {
        return setAttitudeFromQuat(q);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide && (predAge <= 5 || entityData.get(DATA_ORBIT))) {
            serverSyncPos = new Vec3(x, y, z);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    public void setClientPredictedInputs(float throttle, float pitch, float roll, float yaw) {
        predThrottle = throttle;
        predPitch = pitch;
        predRoll = roll;
        predYaw = yaw;
        predAge = 0;
    }

    protected void syncVisualAttitude(float yaw, float pitch, float roll) {
        setAttitudeFromEuler(yaw, pitch, roll);
        entityData.set(DATA_YAW_SYNC, yaw);
        entityData.set(DATA_PITCH_SYNC, pitch);
        entityData.set(DATA_ROLL, roll);
    }

    // -------------------------------------------------------------------------
    //  Mounting
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide && !hasPassenger(player)) {
            if (player instanceof ServerPlayer sp) {
                if (!org.restor.fpv.item.RcLink.hasTransmitter(sp)) {
                    sp.displayClientMessage(Component.translatable("msg.fpv.need_transmitter"), true);
                } else if (!mountPilot(sp)) {
                    sp.displayClientMessage(Component.translatable("msg.fpv.need_goggles"), true);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Boards the pilot for a mounted FPV flight; fails when the goggles are missing. */
    public boolean mountPilot(ServerPlayer player) {
        if (level().isClientSide || hasAnyPilot()) return false;
        if (!org.restor.fpv.item.RcLink.hasGoggles(player)) return false;
        Vec3 boardPos = safeReturnPos(player);
        if (!player.startRiding(this)) return false;
        mountReturnPos = boardPos;
        PilotSessions.open(player, this);
        player.setInvisible(true);
        PilotSessions.syncFlagsToSelf(player);
        spawnDummy(player, boardPos);
        return true;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof ServerPlayer sp) {
            PilotSessions.close(sp);
            sp.setInvisible(false);
            PilotSessions.syncFlagsToSelf(sp);
            discardDummy();
            if (mountReturnPos != null && sp.level() == level()) {
                sp.teleportTo(mountReturnPos.x, mountReturnPos.y, mountReturnPos.z);
            }
        }
    }

    /** Boarding spot to come back to; never the plane itself, which would strand the pilot midair. */
    protected Vec3 safeReturnPos(Player player) {
        Vec3 pos = player.position();
        return pos.distanceToSqr(position()) < 1.0E-4 ? pos.add(0, 1.0, 0) : pos;
    }

    private void spawnDummy(ServerPlayer player, Vec3 at) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        discardDummy();
        OperatorDummyEntity dummy = ModEntities.OPERATOR_DUMMY.get().create(sl);
        if (dummy == null) return;
        dummy.setPos(at.x, at.y, at.z);
        dummy.setYRot(player.getYRot());
        dummy.bindTo(this, player.getUUID());
        dummy.setRadio(defaultRadio(player));
        dummy.setHeadGear(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        sl.addFreshEntity(dummy);
        dummyId = dummy.getUUID();
    }

    private static net.minecraft.world.item.ItemStack defaultRadio(ServerPlayer player) {
        net.minecraft.world.item.ItemStack held = org.restor.fpv.item.RcLink.heldTransmitter(player);
        return held.isEmpty()
            ? new net.minecraft.world.item.ItemStack(org.restor.fpv.item.ModItems.RC_TRANSMITTER.get())
            : held;
    }

    private void discardDummy() {
        if (dummyId != null && level() instanceof net.minecraft.server.level.ServerLevel sl
            && sl.getEntity(dummyId) instanceof OperatorDummyEntity dummy) {
            dummy.discard();
        }
        dummyId = null;
    }

    // Server orbit autopilot: fly the same physics as manual control, but feed gentle stick
    // commands toward a loose circle. This avoids hard setPos() snapping and lets block
    // collisions work through normal move().
    private void doOrbit() {
        if (orbitCenter == null) { orbitActive = false; entityData.set(DATA_ORBIT, false); return; }
        flyOrbit(orbitCenter, orbitRadius, orbitDir, orbitY);
    }

    private void flyOrbit(Vec3 center, double radius, int dir, double holdY) {
        float dt = 0.05f;
        orbitBlend = Math.min(1.0f, orbitBlend + 0.045f);
        double ease = orbitBlend * orbitBlend * (3.0 - 2.0 * orbitBlend);

        double rx = getX() - center.x;
        double rz = getZ() - center.z;
        double dist = Math.sqrt(rx * rx + rz * rz);
        if (dist < 1e-3) dist = 1e-3;

        double radiusError = dist - radius;
        double tangentX = -rz / dist * dir;
        double tangentZ = rx / dist * dir;
        double radialX = rx / dist;
        double radialZ = rz / dist;
        double intercept = clamp((float) (radiusError / Math.max(radius * 0.45, 5.0)), -1.10f, 1.10f);
        double desiredX = tangentX - radialX * intercept;
        double desiredZ = tangentZ - radialZ * intercept;
        double desiredLen = Math.sqrt(desiredX * desiredX + desiredZ * desiredZ);
        if (desiredLen < 1e-4) { desiredX = tangentX; desiredZ = tangentZ; desiredLen = 1.0; }
        desiredX /= desiredLen;
        desiredZ /= desiredLen;

        float altitudeError = (float) (holdY - getY());
        double desiredSpeed = Math.min(maxSpeedBt * 0.70, Math.max(0.75, getDeltaMovement().horizontalDistance()));
        double desiredY = clamp(altitudeError * 0.010f - (float) getDeltaMovement().y * 0.20f, -0.12f, 0.12f);
        Vec3 desiredVel = new Vec3(desiredX * desiredSpeed, desiredY, desiredZ * desiredSpeed);
        Vec3 vel = getDeltaMovement();
        double maxStep = 0.055 + 0.045 * ease;
        Vec3 change = desiredVel.subtract(vel);
        if (change.length() > maxStep) change = change.normalize().scale(maxStep);
        Vec3 newVel = vel.add(change);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-newVel.x, newVel.z));
        float newYaw = approachDegrees(getYRot(), desiredYaw, 3.0f + 7.0f * orbitBlend);
        float newPitch = approachDegrees(getXRot(), 0f, 2.0f);
        orbitDisplayRoll += (((float) (-dir * clamp((float) (24.0 + Math.abs(radiusError) * 1.4), 18.0f, 45.0f)) * ease) - orbitDisplayRoll) * 0.16f;
        setAttitudeFromEuler(newYaw, newPitch, orbitDisplayRoll);
        entityData.set(DATA_ROLL, orbitDisplayRoll);
        setDeltaMovement(newVel);

        throttleInput = (float) (0.58f + 0.08f * ease);
        armed = true;
        applyMovement();
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = wrapDegrees(target - current);
        delta = clamp(delta, -maxDelta, maxDelta);
        return wrapDegrees(current + delta);
    }

    // ---- Chunk force-loading around the piloted plane (prevents far-flight unload) ----
    private final java.util.Set<Long> forcedChunks = new java.util.HashSet<>();
    private final java.util.Set<Long> targetForcedChunks = new java.util.HashSet<>();
    private final java.util.Set<Long> targetStreamedChunks = new java.util.HashSet<>();
    private final java.util.Set<Long> targetStreamTickets = new java.util.HashSet<>();
    private Vec3 targetStreamCenter;
    private long lastTargetStreamCenter = Long.MIN_VALUE;
    private int targetCacheCenterRefresh;

    public void setTargetStreaming(Vec3 center) {
        targetStreamCenter = center;
        if (center == null) clearTargetStreaming();
    }

    private static final int MAX_STREAM_SENDS_PER_TICK = 4;
    private static final int MAX_STREAM_TICKETS = 6;
    private long lastForcedCenter = Long.MIN_VALUE;
    private long lastSelfCacheCenter = Long.MIN_VALUE;

    private void updateForcedChunks() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        // r=1: the pilot rides the plane, so their own player ticket already keeps a full
        // view distance loaded and ticking around it. This is only a head start on the chunk
        // the plane is about to enter — a wider ring meant force-ticking dozens of chunks of
        // other mods' block entities and contraptions at 20 Hz for nothing.
        int r = 1;
        long center = net.minecraft.world.level.ChunkPos.asLong(
            blockPosition().getX() >> 4, blockPosition().getZ() >> 4);
        if (center == lastForcedCenter && !forcedChunks.isEmpty()) return;
        lastForcedCenter = center;
        int cx = net.minecraft.world.level.ChunkPos.getX(center);
        int cz = net.minecraft.world.level.ChunkPos.getZ(center);
        java.util.Set<Long> want = new java.util.HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                want.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        syncForcedChunkSet(sl, forcedChunks, want);
    }

    private void clearForcedChunks() {
        lastForcedCenter = Long.MIN_VALUE;
        if (forcedChunks.isEmpty()) return;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            syncForcedChunkSet(sl, forcedChunks, java.util.Collections.emptySet());
        } else {
            forcedChunks.clear();
        }
    }

    private void updateTargetStreaming(ServerPlayer pilot) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl) || targetStreamCenter == null) {
            // Only when the plane actually changed chunk — this used to fire every tick, and
            // every one of those packets re-centres the client's chunk cache.
            if (pilot != null) {
                net.minecraft.world.level.ChunkPos self = chunkPosition();
                if (self.toLong() != lastSelfCacheCenter) {
                    lastSelfCacheCenter = self.toLong();
                    pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket(self.x, self.z));
                }
            }
            clearTargetStreaming();
            return;
        }
        lastSelfCacheCenter = Long.MIN_VALUE;
        int r = net.minecraft.util.Mth.clamp(org.restor.fpv.Config.DEV_CHUNK_RADIUS.get(), 0, 12);
        if (r == 0) {
            clearTargetStreaming();
            return;
        }
        net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(
            ((int) Math.floor(targetStreamCenter.x)) >> 4,
            ((int) Math.floor(targetStreamCenter.z)) >> 4);

        if (center.toLong() != lastTargetStreamCenter) {
            lastTargetStreamCenter = center.toLong();
            targetStreamedChunks.clear();
            targetCacheCenterRefresh = 0;
        }
        if (targetCacheCenterRefresh-- <= 0) {
            pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket(center.x, center.z));
            targetCacheCenterRefresh = 10;
        }

        java.util.Set<Long> wantForced = new java.util.HashSet<>();
        int tickRadius = Math.min(2, r);
        for (int dx = -tickRadius; dx <= tickRadius; dx++) {
            for (int dz = -tickRadius; dz <= tickRadius; dz++) {
                wantForced.add(net.minecraft.world.level.ChunkPos.asLong(center.x + dx, center.z + dz));
            }
        }
        syncForcedChunkSet(sl, targetForcedChunks, wantForced);

        int sentThisTick = 0;
        ring:
        for (int d = 0; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (d > 0 && Math.abs(dx) != d && Math.abs(dz) != d) continue;
                    if (sentThisTick >= MAX_STREAM_SENDS_PER_TICK) break ring;
                    int cx = center.x + dx;
                    int cz = center.z + dz;
                    long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                    if (targetStreamedChunks.contains(key)) continue;
                    net.minecraft.world.level.chunk.LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                    if (chunk != null) {
                        pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                            chunk, sl.getLightEngine(), null, null));
                        targetStreamedChunks.add(key);
                        if (targetStreamTickets.remove(key)) removeStreamTicket(sl, key);
                        sentThisTick++;
                    } else if (targetStreamTickets.size() < MAX_STREAM_TICKETS && targetStreamTickets.add(key)) {
                        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(cx, cz);
                        sl.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
                    }
                }
            }
        }

        int forgetRadius = r + 2;
        targetStreamedChunks.removeIf(k -> chunkDist(k, center) > forgetRadius);
        targetStreamTickets.removeIf(k -> {
            if (chunkDist(k, center) > forgetRadius) {
                removeStreamTicket(sl, k);
                return true;
            }
            return false;
        });
    }

    private void clearTargetStreaming() {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            syncForcedChunkSet(sl, targetForcedChunks, java.util.Collections.emptySet());
            for (long key : targetStreamTickets) removeStreamTicket(sl, key);
        }
        targetStreamCenter = null;
        targetStreamedChunks.clear();
        targetStreamTickets.clear();
        lastTargetStreamCenter = Long.MIN_VALUE;
        targetCacheCenterRefresh = 0;
    }

    // Entity-ticking tickets (level 31), not ServerLevel#setChunkForced: the latter writes
    // ForcedChunksSavedData and marks it dirty every time the bubble moves, and leaks the
    // whole bubble into the save file if the game dies mid-flight.
    private static void syncForcedChunkSet(net.minecraft.server.level.ServerLevel sl, java.util.Set<Long> current,
                                           java.util.Set<Long> wanted) {
        if (current.equals(wanted)) return;
        for (long c : current) {
            if (!wanted.contains(c)) setEntityTicking(sl, c, false);
        }
        for (long c : wanted) {
            if (!current.contains(c)) setEntityTicking(sl, c, true);
        }
        current.clear();
        current.addAll(wanted);
    }

    private static void setEntityTicking(net.minecraft.server.level.ServerLevel sl, long key, boolean add) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(
            net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key));
        if (add) sl.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
        else sl.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
    }

    private static int chunkDist(long key, net.minecraft.world.level.ChunkPos center) {
        return Math.max(Math.abs(net.minecraft.world.level.ChunkPos.getX(key) - center.x),
            Math.abs(net.minecraft.world.level.ChunkPos.getZ(key) - center.z));
    }

    private static void removeStreamTicket(net.minecraft.server.level.ServerLevel sl, long key) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(
            net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key));
        sl.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) {
            if (!getPassengers().isEmpty()) ejectPassengers();
            clearForcedChunks();
            clearTargetStreaming();
        }
        super.remove(reason);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        var e = getFirstPassenger();
        if (e instanceof LivingEntity le) return le;
        return null;
    }

    @Override
    protected boolean canRide(Entity entity) { return entity instanceof Player; }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return mountReturnPos != null ? mountReturnPos : position().add(0, 1.5, 0);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (hasPassenger(passenger)) {
            // Belly-mounted camera: below and slightly forward of center. Pitch/roll/yaw
            // orientation is applied by the FPV overlay — only position here.
            double yaw = Math.toRadians(getYRot());
            double forward = getBbWidth() * 0.1;
            double x = getX() - Math.sin(yaw) * forward;
            double z = getZ() + Math.cos(yaw) * forward;
            double cameraY = getY() + getBbHeight() * 0.15 - passenger.getEyeHeight();
            moveFunction.accept(passenger, x, cameraY, z);
        }
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return !isFlightReady(); }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (source.getEntity() instanceof Player && hasPassenger(source.getEntity())) return false;
        if (level().isClientSide) return true;

        boolean explosion = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        if (!explosion && source.getEntity() instanceof Player p) {
            org.restor.fpv.util.CraftPickup.giveTo(p, getDropStack());
        } else if (!explosion) {
            spawnAtLocation(getDropStack());
        }
        discard();
        return true;
    }

    /** Airframe plus whatever is bolted on; heavier planes accelerate, climb and turn worse. */
    public float totalMass() {
        return dryMass + payloadMass();
    }

    protected float payloadMass() {
        return 0f;
    }

    public float getModelScale() {
        return 1.0f;
    }

    public net.minecraft.world.item.Item getModelItem() {
        return getDropItem();
    }

    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE.get();
    }

    protected net.minecraft.world.item.ItemStack getDropStack() {
        return new net.minecraft.world.item.ItemStack(getDropItem());
    }

    // -------------------------------------------------------------------------
    //  Controller input / getters
    // -------------------------------------------------------------------------

    public void setControllerInput(float throttle, float pitch, float roll, float yaw, boolean arm) {
        useController = true;
        ctrlThrottle = throttle;
        ctrlPitch = pitch;
        ctrlRoll = roll;
        ctrlYaw = yaw;
        ctrlArm = arm;
        ctrlAge = 0;
    }

    public void clearControllerInput() { useController = false; ctrlAge = 0; }

    @Override
    public void applyRadioInterference(long sourceId, float strength, int durationTicks) {
        if (level().isClientSide || !ServerRule.RADIO.on()) return;
        ServerPlayer pilot = getControllingPassenger() instanceof ServerPlayer sp ? sp : null;
        if (pilot != null && (pilot.isCreative() || pilot.hasPermissions(2))) return;
        jamState.apply(sourceId, strength, durationTicks, level().getGameTime());
    }

    @Override
    public ServerPlayer radioOperator() {
        return getControllingPassenger() instanceof ServerPlayer sp ? sp : null;
    }

    public boolean isRadioJammed() { return jamState.strength() > JAM_LINK_LOSS; }

    @Override
    public boolean hasAnyPilot() { return getControllingPassenger() != null; }

    @Override
    public boolean isActiveDummy(java.util.UUID dummy) {
        return dummyId != null && dummyId.equals(dummy);
    }

    public float getRoll() { return displayRoll; }
    public float getPrevRoll() { return rollO; }
    // Server-authoritative roll (synced), valid on both sides — used for ghost recording.
    public float getSyncedRoll() { return entityData.get(DATA_ROLL); }
    public float getVisualThrottle() { return entityData.get(DATA_THROTTLE); }
    public boolean isArmed() { return entityData.get(DATA_ARMED); }
    public float getPredThrottle() { return predThrottle; }
    public float getSoundThrottle() { return predAge <= 5 ? Math.max(0f, Math.min(1f, predThrottle)) : getVisualThrottle(); }
    public float getPredPitch()    { return predPitch; }
    public float getPredRoll()     { return predRoll; }
    public float getPredYaw()      { return predYaw; }

    // Belly-camera gimbal limits: full 360° pan (yaw wraps freely) and unrestricted look-down
    // (to nearly straight down). Only the up direction is capped — that's the belly-mount
    // limit (the camera can't look up through the aircraft body).
    public static final float GIMBAL_PITCH_MIN = -180f; // full lower hemisphere, including straight down/behind
    public static final float GIMBAL_PITCH_MAX = 20f;   // limited upward look above the horizon

    public float getGimbalYaw()   { return gimbalYaw; }
    public float getGimbalPitch() { return gimbalPitch; }
    public boolean isStabilizedGimbalView() { return stabilizedGimbalView; }
    public void setGimbal(float yaw, float pitch) {
        gimbalYaw = wrapDegrees(yaw);
        gimbalPitch = clamp(pitch, GIMBAL_PITCH_MIN, GIMBAL_PITCH_MAX);
    }
    public void setStabilizedGimbalView(boolean stabilized) { stabilizedGimbalView = stabilized; }

    private static float wrapDegrees(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static float applyDeadband(float v, float threshold) { return Math.abs(v) < threshold ? 0 : v; }

    private static float normalizeYaw(float yaw) {
        while (yaw > 180) yaw -= 360;
        while (yaw < -180) yaw += 360;
        return yaw;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_ROLL, tag.getFloat("Roll"));
        armed = tag.getBoolean("Armed");
        entityData.set(DATA_ARMED, armed);
        // Persist the launch point so a save/reload mid-flight doesn't lose it (which used
        // to leave the pilot dumped at the plane's position on dismount/detonation).
        if (tag.contains("ReturnX")) {
            mountReturnPos = new Vec3(tag.getDouble("ReturnX"), tag.getDouble("ReturnY"), tag.getDouble("ReturnZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Roll", entityData.get(DATA_ROLL));
        tag.putBoolean("Armed", armed);
        if (mountReturnPos != null) {
            tag.putDouble("ReturnX", mountReturnPos.x);
            tag.putDouble("ReturnY", mountReturnPos.y);
            tag.putDouble("ReturnZ", mountReturnPos.z);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }
}
