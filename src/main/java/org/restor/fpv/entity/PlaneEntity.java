package org.restor.fpv.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

// Simple fixed-wing FPV plane: thrust + airspeed-driven lift/drag, banked-turn yaw
// coordination. Riding-only (no remote-control station like the drones have).
public class PlaneEntity extends Entity implements Pilotable {

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
    private static final float GRAVITY = 9.81f;
    private static final float MS2_TO_BT = 0.05f / 20f;

    // Airframe constants shared by all plane types.
    private static final float MASS = 30f;             // kg
    private static final float WING_AREA = 6f;         // arbitrary lift-coefficient scale
    private static final float DRAG_COEFF = 0.045f;
    private static final float MAX_ROLL_RATE = 140f;   // deg/s
    private static final float MAX_PITCH_RATE = 60f;   // deg/s
    private static final float MAX_YAW_RATE = 40f;     // deg/s (rudder trim, on top of bank-turn)
    private static final float ROTATION_RESPONSIVENESS = 6f;
    private static final float ALIGN_RESPONSIVENESS = 6f; // 1/s, max flight-path-to-nose alignment rate at speed

    // Per-type airframe tuning — subclasses override in their constructor.
    protected float maxThrust = 320f;                  // N
    protected float stallSpeed = 6.0f;                 // m/s, lift fades out below this
    protected float liftCoeff = 0.12f;                 // lift balances gravity at ~20 m/s
    protected float maxLiftAccel = GRAVITY * 1.6f;     // cap climb force so extra speed doesn't rocket up
    protected float maxSpeedBt = 4.0f;                 // hard speed cap in blocks/tick (×20 = m/s)

    public float rollO;
    private float displayRoll;
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

    private Vec3 mountReturnPos;
    private java.util.UUID dummyId;

    // Orbit autopilot: circle a fixed point (the camera-lock target) at a set radius.
    private static final float ORBIT_SPEED = 0.9f; // blocks/tick (~18 m/s), smooth for filming
    private boolean orbitActive;
    private Vec3 orbitCenter;
    private double orbitRadius;
    private double orbitY;
    private int orbitDir = 1; // +1 CCW / -1 CW, chosen from motion at engage

    public void setOrbit(boolean active, Vec3 center, double radius) {
        orbitActive = active;
        entityData.set(DATA_ORBIT, active);
        if (active) {
            orbitCenter = center;
            orbitRadius = Math.max(4.0, radius);
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
        }
    }

    public boolean isOrbiting() { return orbitActive; }

    public PlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ROLL,         0f);
        entityData.define(DATA_THROTTLE,     0f);
        entityData.define(DATA_ARMED,        false);
        entityData.define(DATA_YAW_SYNC,     0f);
        entityData.define(DATA_PITCH_SYNC,   0f);
        entityData.define(DATA_ANGVEL_ROLL,  0f);
        entityData.define(DATA_ANGVEL_PITCH, 0f);
        entityData.define(DATA_ANGVEL_YAW,   0f);
        entityData.define(DATA_ORBIT,        false);
        entityData.define(DATA_ORB_CX,       0f);
        entityData.define(DATA_ORB_CZ,       0f);
        entityData.define(DATA_ORB_R,        12f);
        entityData.define(DATA_ORB_DIR,      1f);
        entityData.define(DATA_ORB_Y,        0f);
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
        if (pilot != null) {
            // Keep a hard-loaded bubble around the plane so flying far and fast never
            // outruns chunk loading (which was dumping the pilot back to the launch point).
            updateForcedChunks();
            if (!pilotWasMounted) {
                angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
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
                if (ctrlArm) armed = true;
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
            orbitActive = false;
            pilotWasMounted = false;
            armed = false;
            throttleInput = 0;
            angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
            applyAerodynamics(0.05f);
            applyGravity();
            clearForcedChunks();
        }

        if (!(pilot != null && orbitActive)) applyMovement();

        if (pilot != null) {
            serverLogTick++;
            if (serverLogTick >= 20) {
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
        float thrustAccel = throttle * maxThrust / MASS;
        Vec3 fwd = getLookAngle();
        float dv = thrustAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(fwd.x * dv, fwd.y * dv, fwd.z * dv));

        float curRoll = entityData.get(DATA_ROLL);
        // Full-manual / acro: stick commands a BODY-frame rotation RATE for both roll and
        // pitch — no self-leveling and no angle limits, so full-stick gives unlimited rolls
        // and loops and the plane holds whatever attitude you leave it in. Body-frame is
        // what makes it feel like a plane: banked + elevator carves a turn, not a climb.
        float targetRollRate  = clamp(rollIn,  -1f, 1f) * MAX_ROLL_RATE;
        float targetPitchRate = clamp(pitchIn, -1f, 1f) * MAX_PITCH_RATE;
        // Pure rudder only — no bank-induced auto-yaw. At steep bank the body-yaw axis is
        // near-horizontal, so auto-coordinating yaw there rotated the nose UP in the world
        // (the "banked 90° + elevator pulls up instead of turning" bug). Turns now come
        // purely from body-pitch (elevator) swinging the nose around, like real acro flight.
        float targetYawRate = -clamp(yawIn, -1f, 1f) * MAX_YAW_RATE;

        float resp = Math.min(ROTATION_RESPONSIVENESS * dt, 1.0f);
        angVelRoll  += (targetRollRate  - angVelRoll)  * resp;
        angVelPitch += (targetPitchRate - angVelPitch) * resp;
        angVelYaw   += (targetYawRate   - angVelYaw)   * resp;

        float newRoll = integrateRotation(dt, angVelRoll, angVelPitch, angVelYaw, curRoll);
        entityData.set(DATA_ROLL, newRoll);
    }

    private float integrateRotation(float dt, float rRoll, float rPitch, float rYaw, float curRoll) {
        Quaternionf q = DroneEntity.attitudeQuat(getYRot(), getXRot(), curRoll)
            .rotateX((float) Math.toRadians(rPitch * dt))
            .rotateY((float) Math.toRadians(-rYaw * dt))
            .rotateZ((float) Math.toRadians(rRoll * dt));
        float[] a = DroneEntity.decomposeAttitude(q);
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
        Quaternionf rot = DroneEntity.attitudeQuat(getYRot(), getXRot(), roll);
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);

        float liftFactor = (float) Math.min(1.0, speedMs / stallSpeed);
        // liftCoeff tuned so lift balances gravity at the type's cruise speed; capped by
        // maxLiftAccel so extra speed doesn't rocket the plane vertically.
        float liftAccel = Math.min(maxLiftAccel, liftFactor * liftCoeff * WING_AREA * (float) (speedMs * speedMs) / MASS);
        float liftDv = liftAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(up.x * liftDv, up.y * liftDv, up.z * liftDv));

        // Quadratic drag opposing velocity.
        vel = getDeltaMovement();
        double dragAccel = 0.5 * 1.225 * DRAG_COEFF * speedMs * speedMs / MASS;
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
            Vec3 fwd = getLookAngle();
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

    private void applyGravity() {
        setDeltaMovement(getDeltaMovement().add(0, -GRAVITY * MS2_TO_BT, 0));
        Vec3 vel = getDeltaMovement();
        double max = maxSpeedBt;
        if (vel.lengthSqr() > max * max) {
            setDeltaMovement(vel.scale(max / vel.length()));
        }
    }

    private void applyMovement() {
        Vec3 vel = getDeltaMovement();
        if (onGround() && vel.y < 0) {
            // Only clip the sink rate so the plane doesn't dig into the ground each tick —
            // killing horizontal speed too (as a prior version did) capped ground-roll speed
            // at a crawl far below stall speed, so the plane could never build up to takeoff.
            vel = new Vec3(vel.x, 0, vel.z);
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

        // Orbit autopilot: predict the exact same circle locally from the synced params, so
        // it's perfectly smooth instead of easing toward batched server position updates.
        if (entityData.get(DATA_ORBIT)) {
            displayRoll = stepOrbit(entityData.get(DATA_ORB_CX), entityData.get(DATA_ORB_CZ),
                entityData.get(DATA_ORB_R), Math.round(entityData.get(DATA_ORB_DIR)),
                entityData.get(DATA_ORB_Y));
            predAge = 100; // ensure we re-enter local prediction cleanly when orbit ends
            return;
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

                float targetRollRate  = clamp(rollIn, -1f, 1f) * MAX_ROLL_RATE;
                float targetPitchRate = clamp(pitchIn, -1f, 1f) * MAX_PITCH_RATE;
                float targetYawRate = -clamp(yawIn, -1f, 1f) * MAX_YAW_RATE;
                float resp = Math.min(ROTATION_RESPONSIVENESS * dt, 1.0f);
                angVelRoll  += (targetRollRate  - angVelRoll)  * resp;
                angVelPitch += (targetPitchRate - angVelPitch) * resp;
                angVelYaw   += (targetYawRate   - angVelYaw)   * resp;

                displayRoll = integrateRotation(dt, angVelRoll, angVelPitch, angVelYaw, curRoll);
            } else {
                angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
            }

            Quaternionf qc = DroneEntity.attitudeQuat(getYRot(), getXRot(), displayRoll);
            Quaternionf qs = DroneEntity.attitudeQuat(entityData.get(DATA_YAW_SYNC),
                entityData.get(DATA_PITCH_SYNC), entityData.get(DATA_ROLL));
            qc.slerp(qs, 0.08f);
            displayRoll = applyAttitudeClient(qc);
        } else {
            setYRot(entityData.get(DATA_YAW_SYNC));
            setXRot(entityData.get(DATA_PITCH_SYNC));
            displayRoll = entityData.get(DATA_ROLL);
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
        float[] a = DroneEntity.decomposeAttitude(q);
        setYRot(normalizeYaw(a[0]));
        setXRot(a[1]);
        return a[2];
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (level().isClientSide && predAge <= 5) {
            serverSyncPos = new Vec3(x, y, z);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    public void setClientPredictedInputs(float throttle, float pitch, float roll, float yaw) {
        predThrottle = throttle;
        predPitch = pitch;
        predRoll = roll;
        predYaw = yaw;
        predAge = 0;
    }

    // -------------------------------------------------------------------------
    //  Mounting
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide && !hasPassenger(player)) {
            Vec3 boardPos = player.position();
            if (player.startRiding(this) && player instanceof ServerPlayer sp) {
                mountReturnPos = boardPos;
                sp.setInvisible(true);
                spawnDummy(sp, boardPos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof ServerPlayer sp) {
            sp.setInvisible(false);
            discardDummy();
            if (mountReturnPos != null) {
                sp.teleportTo(mountReturnPos.x, mountReturnPos.y, mountReturnPos.z);
                mountReturnPos = null;
            }
        }
    }

    private void spawnDummy(ServerPlayer player, Vec3 at) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        OperatorDummyEntity dummy = ModEntities.OPERATOR_DUMMY.get().create(sl);
        if (dummy == null) return;
        dummy.setPos(at.x, at.y, at.z);
        dummy.setYRot(player.getYRot());
        dummy.bindTo(this, player.getUUID());
        sl.addFreshEntity(dummy);
        dummyId = dummy.getUUID();
    }

    private void discardDummy() {
        if (dummyId != null && level() instanceof net.minecraft.server.level.ServerLevel sl
            && sl.getEntity(dummyId) instanceof OperatorDummyEntity dummy) {
            dummy.discard();
        }
        dummyId = null;
    }

    // Server orbit step: sweep the plane around the centre, set attitude + bank, and mark it
    // armed. The identical kinematics run client-side (stepOrbit) for smooth prediction.
    private void doOrbit() {
        if (orbitCenter == null) { orbitActive = false; entityData.set(DATA_ORBIT, false); return; }
        float bank = stepOrbit(orbitCenter.x, orbitCenter.z, orbitRadius, orbitDir, orbitY);
        entityData.set(DATA_ROLL, bank);
        throttleInput = 0.5f;
        armed = true;
        angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
    }

    // Kinematic circle step (used by both server and client): advance one tick around the
    // circle of radius R about (cx,cz), easing radius + altitude, face the tangent. Returns
    // the bank angle to display. setPos/setYRot make it self-contained per side.
    private float stepOrbit(double cx, double cz, double radius, int dir, double holdY) {
        double rx = getX() - cx, rz = getZ() - cz;
        double dist = Math.sqrt(rx * rx + rz * rz);
        if (dist < 1e-3) { rx = radius; rz = 0; dist = radius; }
        double ang = Math.atan2(rz, rx);
        double omega = dir * ORBIT_SPEED / Math.max(radius, 4.0);
        double newAng = ang + omega;
        double newDist = dist + (radius - dist) * 0.08;
        double nx = cx + Math.cos(newAng) * newDist;
        double nz = cz + Math.sin(newAng) * newDist;
        double ny = getY() + (holdY - getY()) * 0.08;

        float yaw = (float) Math.toDegrees(Math.atan2(-(nx - getX()), (nz - getZ())));
        setYRot(yaw);
        setXRot(0f);
        setDeltaMovement(new Vec3(nx - getX(), ny - getY(), nz - getZ()));
        setPos(nx, ny, nz);
        return (float) (-dir * 28.0); // bank into the turn
    }

    // ---- Chunk force-loading around the piloted plane (prevents far-flight unload) ----
    private final java.util.Set<Long> forcedChunks = new java.util.HashSet<>();

    private void updateForcedChunks() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int r = 3; // a small bubble ahead/behind is enough with the pilot's own view tickets
        int cx = blockPosition().getX() >> 4;
        int cz = blockPosition().getZ() >> 4;
        java.util.Set<Long> want = new java.util.HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                want.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        if (want.equals(forcedChunks)) return;
        for (long c : forcedChunks) {
            if (!want.contains(c)) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            }
        }
        for (long c : want) {
            if (!forcedChunks.contains(c)) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), true);
            }
        }
        forcedChunks.clear();
        forcedChunks.addAll(want);
    }

    private void clearForcedChunks() {
        if (forcedChunks.isEmpty()) return;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (long c : forcedChunks) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            }
        }
        forcedChunks.clear();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) clearForcedChunks();
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
    public double getPassengersRidingOffset() { return 0.0; }

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
    public boolean isPushable() { return false; }

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
    public boolean hasAnyPilot() { return getControllingPassenger() != null; }

    public float getRoll() { return displayRoll; }
    public float getPrevRoll() { return rollO; }
    public float getVisualThrottle() { return entityData.get(DATA_THROTTLE); }
    public boolean isArmed() { return entityData.get(DATA_ARMED); }
    public float getPredThrottle() { return predThrottle; }
    public float getPredPitch()    { return predPitch; }
    public float getPredRoll()     { return predRoll; }
    public float getPredYaw()      { return predYaw; }

    // Belly-camera gimbal limits: full 360° pan (yaw wraps freely) and unrestricted look-down
    // (to nearly straight down). Only the up direction is capped — that's the belly-mount
    // limit (the camera can't look up through the aircraft body).
    public static final float GIMBAL_PITCH_MIN = -89f; // ~straight down (avoid the ±90 gimbal-lock singularity)
    public static final float GIMBAL_PITCH_MAX = 20f;  // just above the horizon

    public float getGimbalYaw()   { return gimbalYaw; }
    public float getGimbalPitch() { return gimbalPitch; }
    public void setGimbal(float yaw, float pitch) {
        gimbalYaw = wrapDegrees(yaw);
        gimbalPitch = clamp(pitch, GIMBAL_PITCH_MIN, GIMBAL_PITCH_MAX);
    }

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
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
