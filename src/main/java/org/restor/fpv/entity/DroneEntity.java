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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.data.Battery;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.flight.FlightController;
import org.restor.fpv.flight.FlightMode;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

public class DroneEntity extends Entity implements Pilotable {

    private static final Logger LOGGER = LogUtils.getLogger();
    // Log every N server ticks while flying (20 = once/sec, 1 = every tick)
    private static final int LOG_INTERVAL = 20;
    private int logTick;

    // Existing synced state
    private static final EntityDataAccessor<Float>   DATA_ROLL          = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_VISUAL_THR    = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT_MODE   = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED         = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float>   DATA_BATTERY       = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_RSSI          = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    // Velocity sync — allows client to reproduce server movement each tick (no lerp jitter)
    private static final EntityDataAccessor<Float>   DATA_VEL_X         = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_VEL_Y         = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_VEL_Z         = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    // Rotation sync — bypasses lerp so FPV camera updates instantly
    private static final EntityDataAccessor<Float>   DATA_YAW_SYNC      = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_PITCH_SYNC    = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_CAMERA_ANGLE  = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    // Angular velocity sync (°/s) — client uses these for forward extrapolation at 60fps.
    // This prevents stutter when 2+ server ticks are batched before the client processes them.
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_ROLL   = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_PITCH  = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_ANGVEL_YAW    = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    // Per-drone rate multipliers. Initialized from the client config (the "default
    // config") when the drone is created, then owned by this entity — editing them
    // in the settings screen affects only this drone unless saved as default.
    private static final EntityDataAccessor<Float>   DATA_RATE_PITCH    = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_RATE_ROLL     = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_RATE_YAW      = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    // 6-digit link code for the remote control station (synced so the code screen can prefill).
    private static final EntityDataAccessor<String>  DATA_LINK_CODE     = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);

    // Physics in SI units: 1 block = 1 m, 20 ticks/s. Velocities are stored in
    // blocks/tick, so accelerations (m/s²) convert as Δv_bt = a * dt / 20.
    private static final float GRAVITY = 9.81f;       // m/s²
    private static final float AIR_DENSITY = 1.225f;  // kg/m³
    private static final float MS2_TO_BT = 0.05f / 20f; // (a m/s²) * this = Δblocks/tick per tick

    public DroneData droneData;
    public FlightController flightController;
    public Battery battery;
    public float rssi = 1.0f;

    private float prevPilotXRot;
    protected float throttleInput;
    private int controlCooldown;
    private boolean pilotWasMounted;
    // Roll interpolation mirrors xRotO/yRotO: rollO is saved before clientTick
    // updates displayRoll, giving smooth partialTick lerp at 60+ fps.
    public float rollO;
    private float displayRoll;
    // Angular velocity state in degrees/second — persistent between ticks for rotational inertia.
    // Lerped toward stick target each physics step per the spec.
    private float angVelRoll;
    private float angVelPitch;
    private float angVelYaw;

    // Controller input (server-side)
    private boolean useController;
    private float ctrlThrottle, ctrlPitch, ctrlRoll, ctrlYaw;
    private boolean ctrlArm, ctrlModeSwitch, prevCtrlArm, prevCtrlMode;
    private int ctrlAge;

    // Client-side prediction (local pilot only). ClientEventHandler mirrors the stick
    // values here at the same moment it sends them to the server, so the client can
    // integrate rotation immediately instead of waiting a network round-trip.
    private float predThrottle, predPitch, predRoll, predYaw;
    private int predAge = 100;

    // Remote control session (server-side): a player at a control station flies this
    // drone via camera projection. Vanilla ServerPlayer camera-follow keeps chunks
    // loaded around the drone and handles sneak-to-exit; we teleport the pilot back
    // to the station when the session ends.
    private float lastHitboxScale = 1f;
    private float lastHitboxYOff;

    private java.util.UUID remotePilotId;
    private java.util.UUID dummyId;
    private Vec3 stationReturnPos;
    private Vec3 mountReturnPos;
    private ServerPlayer activePilot;
    private boolean pilotWasInvulnerable;
    // Chunks force-loaded around the drone during a remote session (vanilla forceload,
    // ticking). Guarantees the drone keeps simulating wherever it flies.
    private final java.util.Set<Long> forcedChunks = new java.util.HashSet<>();
    // Manual chunk streaming to the pilot's client. Vanilla only streams chunks
    // around the player, and that path proved unreliable with a detached camera —
    // so while piloted, the drone itself sends chunk packets around its position
    // (and requests async loading/generation via tickets).
    private final java.util.Set<Long> streamedChunks = new java.util.HashSet<>();
    private final java.util.Set<Long> streamTickets = new java.util.HashSet<>();
    private long lastStreamCenter = Long.MIN_VALUE;
    // Diagnostics for the [CHUNKSTREAM] log line — filled in by streamChunksToPilot().
    private int lastStreamSentThisTick = 0;
    private int lastStreamPendingThisTick = 0;
    private int lastStreamCandidates = 0;
    // Latest position from server entity-move packets; blended softly in clientTick
    // instead of letting vanilla snap the entity (see lerpTo override).
    private Vec3 serverSyncPos;

    public DroneEntity(EntityType<?> type, Level level) {
        this(type, level, DroneData.CINEWHOOP);
    }

    public DroneEntity(EntityType<?> type, Level level, DroneData data) {
        super(type, level);
        this.droneData = data;
        this.flightController = new FlightController(data);
        this.battery = new Battery(data.batteryCapacity, 4.2f);
        this.entityData.set(DATA_CAMERA_ANGLE, data.cameraAngle);
        this.blocksBuilding = true;
        if (!level.isClientSide) {
            // New drones adopt the saved default config; NBT load overrides for existing ones.
            entityData.set(DATA_RATE_PITCH, org.restor.fpv.Config.RC_PITCH_RATE.get().floatValue());
            entityData.set(DATA_RATE_ROLL,  org.restor.fpv.Config.RC_ROLL_RATE.get().floatValue());
            entityData.set(DATA_RATE_YAW,   org.restor.fpv.Config.RC_YAW_RATE.get().floatValue());
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ROLL,        0f);
        entityData.define(DATA_VISUAL_THR,  0f);
        entityData.define(DATA_FLIGHT_MODE, 0);
        entityData.define(DATA_ARMED,       false);
        entityData.define(DATA_BATTERY,     1f);
        entityData.define(DATA_RSSI,        1f);
        entityData.define(DATA_VEL_X,       0f);
        entityData.define(DATA_VEL_Y,       0f);
        entityData.define(DATA_VEL_Z,       0f);
        entityData.define(DATA_YAW_SYNC,    0f);
        entityData.define(DATA_PITCH_SYNC,  0f);
        entityData.define(DATA_CAMERA_ANGLE, 0f);
        entityData.define(DATA_ANGVEL_ROLL,  0f);
        entityData.define(DATA_ANGVEL_PITCH, 0f);
        entityData.define(DATA_ANGVEL_YAW,   0f);
        entityData.define(DATA_RATE_PITCH,   1f);
        entityData.define(DATA_RATE_ROLL,    1f);
        entityData.define(DATA_RATE_YAW,     1f);
        entityData.define(DATA_LINK_CODE,    "");
    }

    // -------------------------------------------------------------------------
    //  Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        // Mirror the xRotO/yRotO pattern: save displayRoll as rollO BEFORE super.tick()
        // so partialTick interpolation in FPVOverlay sees prev vs current each frame.
        rollO = displayRoll;
        super.tick();
        if (isNoGravity()) setNoGravity(false);

        // Pick up dev-tab hitbox changes live.
        float hbScale = (float) org.restor.fpv.Config.DEV_HITBOX_SCALE.get().doubleValue();
        float hbYOff  = (float) org.restor.fpv.Config.DEV_HITBOX_Y_OFFSET.get().doubleValue();
        if (hbScale != lastHitboxScale || hbYOff != lastHitboxYOff) {
            lastHitboxScale = hbScale;
            lastHitboxYOff = hbYOff;
            refreshDimensions();
        }

        if (!level().isClientSide) {
            serverTick();
        } else {
            clientTick();
        }
    }

    // -------------------------------------------------------------------------
    //  Server tick — authoritative physics
    // -------------------------------------------------------------------------

    private void serverTick() {
        ServerPlayer pilot = getControllingPassenger() instanceof ServerPlayer sp ? sp : resolveRemotePilot();
        activePilot = pilot;
        boolean hasPilot = pilot != null;
        if (hasPilot) {
            streamChunksToPilot(pilot);
        } else {
            clearChunkStreaming();
        }

        if (hasPilot) {
            if (!pilotWasMounted) {
                prevPilotXRot = pilot.getXRot();
                angVelRoll  = 0;
                angVelPitch = 0;
                angVelYaw   = 0;
                pilotWasMounted = true;
            }

            if (useController) {
                ctrlAge++;
                if (ctrlAge > 40) clearControllerInput();
            }
            readControls();

            float dt = 0.05f;
            // angVelRoll/Pitch/Yaw are persistent °/s — pass directly to FlightController.
            float currentRollRate  = angVelRoll;
            float currentPitchRate = angVelPitch;
            float currentYawRate   = angVelYaw;

            flightController.throttle = throttleInput;
            flightController.update(dt, currentRollRate, currentPitchRate, currentYawRate,
                entityData.get(DATA_ROLL), getXRot());

            battery.tick(throttleInput, effMotorPower());

            if (flightController.isArmed()) {
                applyFlightPhysics(dt);
            }
            applyDrag(dt);
            applyGravity(dt);

            if (battery.isEmpty()) flightController.armed = false;

            // ---- physics diagnostics (once per second while piloted) ----
            if (++logTick >= LOG_INTERVAL) {
                logTick = 0;
                float hoverPct = GRAVITY * mass()
                    / (effMotorPower() * getBatteryPowerFactor() * droneData.maxThrottle) * 100f;
                Vec3 v = getDeltaMovement();
                // Recompute thrust vector for logging
                Quaternionf rotLog = new Quaternionf()
                    .rotateY((float) Math.toRadians(-getYRot()))
                    .rotateX((float) Math.toRadians(getXRot()))
                    .rotateZ((float) Math.toRadians(entityData.get(DATA_ROLL)));
                Vector3f upLog = new Vector3f(0, 1, 0);
                rotLog.transform(upLog);
                LOGGER.info("[FPV-ANGLES] yaw={} pitch={} roll={}",
                    String.format("%.1f", getYRot()),
                    String.format("%.1f", getXRot()),
                    String.format("%.1f", entityData.get(DATA_ROLL)));
                LOGGER.info("[FPV-INPUTS] armed={} mode={} thr={} pitchIn={} rollIn={} yawIn={} hoverAt={}%%",
                    flightController.isArmed(),
                    flightController.getMode().name(),
                    String.format("%.3f", throttleInput),
                    String.format("%.3f", ctrlPitch),
                    String.format("%.3f", ctrlRoll),
                    String.format("%.3f", ctrlYaw),
                    String.format("%.0f", hoverPct));
                if (remotePilotId != null && activePilot != null) {
                    LOGGER.info("[REMOTE] drone=({},{},{}) pilot=({},{},{}) dist={} forced={} chunkR={}",
                        String.format("%.0f", getX()), String.format("%.0f", getY()), String.format("%.0f", getZ()),
                        String.format("%.0f", activePilot.getX()), String.format("%.0f", activePilot.getY()), String.format("%.0f", activePilot.getZ()),
                        String.format("%.1f", distanceTo(activePilot)),
                        forcedChunks.size(),
                        org.restor.fpv.Config.DEV_CHUNK_RADIUS.get());
                }
                if (activePilot != null) {
                    LOGGER.info("[CHUNKSTREAM] pilot={} dim={} candidates={} sentThisTick={} totalStreamed={} pendingTickets={} chunkR={} viewDist={}",
                        activePilot.getGameProfile().getName(),
                        level().dimension().location(),
                        lastStreamCandidates, lastStreamSentThisTick, streamedChunks.size(), lastStreamPendingThisTick,
                        org.restor.fpv.Config.DEV_CHUNK_RADIUS.get(),
                        activePilot.server.getPlayerList().getViewDistance());
                }
                LOGGER.info("[FPV-THRUST] dir=({},{},{}) | vel=({},{},{}) spd={} h={}",
                    String.format("%.2f", upLog.x),
                    String.format("%.2f", upLog.y),
                    String.format("%.2f", upLog.z),
                    String.format("%.2f", v.x),
                    String.format("%.2f", v.y),
                    String.format("%.2f", v.z),
                    String.format("%.2f", v.length()),
                    String.format("%.1f", getHeightAboveGround()));
            }
        } else {
            pilotWasMounted = false;
            idleFallback();
            logTick = 0;
        }

        if (!flightController.isArmed()) {
            throttleInput = 0;
            angVelRoll  = 0;
            angVelPitch = 0;
            angVelYaw   = 0;
        }

        applyMovement();

        // Sync state to clients
        Vec3 vel = getDeltaMovement();
        entityData.set(DATA_VISUAL_THR,  throttleInput);
        entityData.set(DATA_ARMED,       flightController.armed);
        entityData.set(DATA_FLIGHT_MODE, flightController.getMode().ordinal());
        entityData.set(DATA_BATTERY,     battery.getPercentRemaining());
        entityData.set(DATA_RSSI,        rssi);
        entityData.set(DATA_VEL_X,       (float) vel.x);
        entityData.set(DATA_VEL_Y,       (float) vel.y);
        entityData.set(DATA_VEL_Z,       (float) vel.z);
        entityData.set(DATA_YAW_SYNC,    getYRot());
        entityData.set(DATA_PITCH_SYNC,  getXRot());
        entityData.set(DATA_ANGVEL_ROLL,  angVelRoll);
        entityData.set(DATA_ANGVEL_PITCH, angVelPitch);
        entityData.set(DATA_ANGVEL_YAW,   angVelYaw);
    }

    // -------------------------------------------------------------------------
    //  Client tick — mirrors server physics using synced data for smooth motion.
    //  isControlledByLocalInstance() defaults to true when local player rides,
    //  so Minecraft won't lerp position from server. We simulate it ourselves.
    // -------------------------------------------------------------------------

    private void clientTick() {
        float dt = 0.05f;
        float target = entityData.get(DATA_VISUAL_THR);
        throttleInput += (target - throttleInput) * 0.4f;

        // Fresh predicted inputs are only ever set for the drone THIS client controls
        // (riding or via remote station), so predAge is the ownership signal.
        predAge++;
        boolean localPilot = predAge <= 5;
        if (localPilot) {
            // Predict rotation from our own sticks — zero input latency, and immune to
            // sync-packet batching (the cause of the 20Hz freeze-jump camera stutter).
            flightController.armed = entityData.get(DATA_ARMED);
            flightController.throttle = predThrottle;
            flightController.targetPitch = applyDeadband(predPitch, 0.05f);
            flightController.targetRoll  = applyDeadband(predRoll,  0.05f);
            flightController.targetYaw   = applyDeadband(predYaw,   0.05f);
            flightController.update(dt, angVelRoll, angVelPitch, angVelYaw, displayRoll, getXRot());
            if (flightController.isArmed()) {
                displayRoll = integrateRotation(dt, flightController.rollOutput,
                    flightController.pitchOutput, flightController.yawOutput, displayRoll);
            } else {
                angVelRoll = 0; angVelPitch = 0; angVelYaw = 0;
            }
            // Soft correction toward the authoritative server attitude, spread over
            // ~12 ticks. Done as a quaternion slerp: correcting yaw/pitch/roll each
            // along its own shortest path wobbles when the axes change together
            // (yawing while tilted), because that combination is not the shortest
            // rotation — slerp follows the single shortest arc.
            Quaternionf qc = attitudeQuat(getYRot(), getXRot(), displayRoll);
            Quaternionf qs = attitudeQuat(entityData.get(DATA_YAW_SYNC),
                entityData.get(DATA_PITCH_SYNC), entityData.get(DATA_ROLL));
            qc.slerp(qs, 0.08f);
            displayRoll = applyAttitude(qc);
        } else {
            // Not piloting locally (spectating someone else's drone): follow server values.
            // yRotO/xRotO are already saved by super.tick(), so Mth.rotLerp in FPVOverlay
            // interpolates smoothly at 60+ fps; rollO/displayRoll mirror that split.
            setYRot(entityData.get(DATA_YAW_SYNC));
            setXRot(entityData.get(DATA_PITCH_SYNC));
            displayRoll = entityData.get(DATA_ROLL);
        }

        // Use server velocity only. Local force prediction caused visible FPV jitter
        // when the drone was tilted because client and server fought over position.
        setDeltaMovement(new Vec3(
            entityData.get(DATA_VEL_X),
            entityData.get(DATA_VEL_Y),
            entityData.get(DATA_VEL_Z)
        ));

        // Ground
        Vec3 vel = getDeltaMovement();
        if (onGround() && vel.y < 0) {
            vel = new Vec3(vel.x * 0.7, 0, vel.z * 0.7);
            setDeltaMovement(vel);
        }

        move(MoverType.SELF, getDeltaMovement());

        // Blend toward the authoritative server position captured in lerpTo() —
        // gentle so it never reads as a snap; hard snap only if truly far off.
        if (localPilot && serverSyncPos != null) {
            Vec3 delta = serverSyncPos.subtract(position());
            double d2 = delta.lengthSqr();
            // Tolerate a much larger gap before hard-snapping when moving fast: server move
            // packets are batched, so at 50+ m/s a legitimate update can arrive several
            // blocks away. Snapping to it each time it landed was the periodic high-speed
            // stutter. Scale the snap threshold and the soft-blend rate with speed so the
            // gap still closes smoothly within a few ticks instead of jumping.
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

        limitVelocity();
    }

    // -------------------------------------------------------------------------
    //  Controls
    // -------------------------------------------------------------------------

    private void readControls() {
        if (useController) readControllerInput();
        else readKeyboardInput();
    }

    private void readKeyboardInput() {
        Player pilot = activePilot;
        if (pilot == null) return;
        float forward = pilot.zza;
        float strafe  = pilot.xxa;

        // Mouse vertical delta → pitch
        float pitchDelta = pilot.getXRot() - prevPilotXRot;
        prevPilotXRot = pilot.getXRot();
        flightController.targetPitch = applyDeadband(clamp(pitchDelta * 0.08f, -1, 1), 0.02f);
        flightController.targetRoll  = 0;
        // A key (+1 leftImpulse) = yaw left = negative yaw rate
        flightController.targetYaw   = applyDeadband(clamp(-strafe, -1, 1), 0.1f);

        if (forward > 0.1f) {
            throttleInput = Math.min(1.0f, throttleInput + 0.03f);
            if (!flightController.isArmed()) {
                flightController.toggleArm();
                throttleInput = 0.2f;
            }
        } else if (forward < -0.1f) {
            throttleInput = Math.max(0.0f, throttleInput - 0.03f);
        } else {
            throttleInput = Math.max(0.0f, throttleInput - 0.005f);
        }

        if (flightController.isArmed() && throttleInput < 0.01f && onGround() && forward <= 0.1f) {
            flightController.armed = false;
        }
        if (controlCooldown > 0) controlCooldown--;
    }

    private void readControllerInput() {
        // ACRO: throttle is pure 0-100% from stick, never negative thrust.
        // In ANGLE/HORIZON the same mapping applies — clamp defensively to [0,1].
        throttleInput = Math.max(0f, Math.min(1f, ctrlThrottle));
        // Apply deadband to stick axes — prevents drift from off-center transmitter sticks
        flightController.targetPitch = applyDeadband(ctrlPitch, 0.05f);
        flightController.targetRoll  = applyDeadband(ctrlRoll,  0.05f);
        flightController.targetYaw   = applyDeadband(ctrlYaw,   0.05f);

        if (ctrlArm && !flightController.isArmed()) {
            flightController.armed = true;
        }
        prevCtrlArm = ctrlArm;

        if (ctrlModeSwitch && !prevCtrlMode && controlCooldown <= 0) {
            flightController.cycleMode();
            controlCooldown = 5;
        }
        prevCtrlMode = ctrlModeSwitch;

        // Disarm on landing: throttle idle + on ground.
        // In ACRO mode require an extra margin so a brief full-down stick while flying
        // doesn't accidentally disarm mid-air if the drone clips a block.
        boolean isAcro = flightController.getMode() == org.restor.fpv.flight.FlightMode.ACRO;
        float disarmThreshold = isAcro ? 0.005f : 0.01f;
        if (flightController.isArmed() && throttleInput < disarmThreshold && onGround()) {
            flightController.armed = false;
        }
        if (controlCooldown > 0) controlCooldown--;
    }

    // -------------------------------------------------------------------------
    //  Physics
    // -------------------------------------------------------------------------

    // No pilot: motors are dead — the drone simply falls (gravity + drag), keeping
    // its attitude. Roll levels out slowly once it lands.
    private void idleFallback() {
        flightController.armed = false;
        throttleInput = 0;
        angVelRoll  = 0;
        angVelPitch = 0;
        angVelYaw   = 0;
        applyDrag(0.05f);
        applyGravity(0.05f);
        if (onGround()) {
            entityData.set(DATA_ROLL, entityData.get(DATA_ROLL) * 0.8f);
        }
    }

    private void applyMovement() {
        Vec3 vel = getDeltaMovement();
        if (onGround() && vel.y < 0) {
            vel = new Vec3(vel.x * 0.7, 0, vel.z * 0.7);
            setDeltaMovement(vel);
        }
        move(MoverType.SELF, vel);
        collideWithEntities(vel);
    }

    // Prop strike: flying into a living entity damages and shoves it, kills most of
    // the drone's speed, and lets subclasses react (kamikaze detonates).
    private void collideWithEntities(Vec3 velBefore) {
        double speed = velBefore.length();
        if (speed < 0.1) return; // under ~2 m/s a touch is harmless
        var hits = level().getEntities(this, getBoundingBox().inflate(0.1),
            e -> e instanceof LivingEntity && !e.isSpectator() && !hasPassenger(e)
                && !(e instanceof OperatorDummyEntity)
                && !(e instanceof ServerPlayer sp && isRemotePilot(sp)));
        if (hits.isEmpty()) return;
        float dmg = (float) (speed * 20.0 * mass() * 0.6);
        for (Entity e : hits) {
            e.hurt(level().damageSources().thrown(this, activePilot), dmg);
            e.push(velBefore.x * 0.8, Math.abs(velBefore.y) * 0.4 + 0.1, velBefore.z * 0.8);
        }
        setDeltaMovement(getDeltaMovement().scale(0.35));
        onEntityCollision();
    }

    /** Called after hitting a living entity at speed — kamikaze overrides to detonate. */
    protected void onEntityCollision() {}

    // Fraction of rotation inputs mixed into each motor (tuned so full stick at mid-throttle
    // doesn't clip motors to zero on the low side).
    private static final float ROT_MIX = 0.5f;

    private void applyFlightPhysics(float dt) {
        float t  = flightController.throttle * droneData.maxThrottle;
        float ro = flightController.rollOutput;
        float po = flightController.pitchOutput;
        float yo = flightController.yawOutput;

        float newRoll = integrateRotation(dt, ro, po, yo, entityData.get(DATA_ROLL));
        entityData.set(DATA_ROLL, newRoll);

        // Throttle directly sets total thrust — no motor mixing on the thrust axis.
        // Clamp to [0,1]: ACRO never produces negative (reverse) thrust.
        float totalFrac = Math.max(0f, t);

        // Ground effect: extra lift close to terrain surface
        double height = getHeightAboveGround();
        float groundEffect = (height > 0.05 && height < droneData.armLength * 5.0)
            ? 1.0f + 0.25f * (float) Math.exp(-height / droneData.armLength)
            : 1.0f;

        // Thrust acceleration in m/s²: motorPower is max thrust in Newtons.
        float thrustAccel = totalFrac * groundEffect * getBatteryPowerFactor()
            * effMotorPower() / mass();

        // Rotate body-up axis by current orientation to get world-space thrust direction.
        // In Minecraft's left-handed coordinate system, rotateZ(+roll) correctly tilts the
        // up-vector toward the right side (−X when facing south = west = the player's right).
        Quaternionf rot = attitudeQuat(getYRot(), getXRot(), entityData.get(DATA_ROLL));
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);
        float dv = thrustAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(up.x * dv, up.y * dv, up.z * dv));

        // limitVelocity() NOT called here — it runs in applyGravity() after gravity is subtracted.
        // Calling it here caused: thrust cap (1.245) → gravity subtracts 1.177 → net ≈ 0.07 (hover!)
        // regardless of throttle. Fix: cap only after the full physics step is complete.
    }

    // Shared by server physics and client prediction: lerps angular velocity toward the
    // commanded rates, rotates the orientation about the drone's own BODY axes, and
    // returns the new roll angle (roll storage differs — entityData server, displayRoll client).
    private float integrateRotation(float dt, float ro, float po, float yo, float curRoll) {
        // Target angular rates in °/s from stick output, scaled by this drone's multipliers.
        // Yaw negated: Minecraft yRot increases = CCW (left), so positive yaw output = CW (right) = yRot decreasing.
        float rateRoll  = entityData.get(DATA_RATE_ROLL);
        float ratePitch = entityData.get(DATA_RATE_PITCH);
        float rateYaw   = entityData.get(DATA_RATE_YAW);
        // Trim hooks (°/s): modular drones add a constant bias here when their motor
        // thrust is imbalanced (e.g. a weaker prop on one corner).
        float targetRollRate  =  clamp(ro, -1f, 1f) * droneData.maxRollRate  * rateRoll  + trimRollRate();
        float targetPitchRate =  clamp(po, -1f, 1f) * droneData.maxPitchRate * ratePitch + trimPitchRate();
        float targetYawRate   = -clamp(yo, -1f, 1f) * droneData.maxYawRate   * rateYaw   - trimYawRate();

        // Lerp angular velocity toward target — gives rotational inertia per spec.
        // factor = min(responsiveness * dt, 1.0): at dt=0.05, racing=15 → factor=0.75 per tick.
        // Heavier drone (dev weight) = more rotational inertia = slower response.
        float massMult = (float) org.restor.fpv.Config.DEV_MASS_MULT.get().doubleValue();
        float resp = Math.min(droneData.rotationResponsiveness / massMult * dt, 1.0f);
        angVelRoll  += (targetRollRate  - angVelRoll)  * resp;
        angVelPitch += (targetPitchRate - angVelPitch) * resp;
        angVelYaw   += (targetYawRate   - angVelYaw)   * resp;

        // Integrate in the BODY frame: append this tick's increments to the current
        // orientation quaternion. Yaw spins around the drone's own up axis — when the
        // drone is pitched/banked, a yaw input carves the turn a real quad makes,
        // instead of always rotating about the world vertical.
        // (rotateY sign: local +Y euler maps to yRot decreasing, matching the old
        //  yRot += angVelYaw*dt convention at level attitude.)
        Quaternionf q = attitudeQuat(getYRot(), getXRot(), curRoll)
            .rotateX((float) Math.toRadians(angVelPitch * dt))
            .rotateY((float) Math.toRadians(-angVelYaw * dt))
            .rotateZ((float) Math.toRadians(angVelRoll * dt));
        return applyAttitude(q);
    }

    public static Quaternionf attitudeQuat(float yawDeg, float pitchDeg, float rollDeg) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(-yawDeg))
            .rotateX((float) Math.toRadians(pitchDeg))
            .rotateZ((float) Math.toRadians(rollDeg));
    }

    // Decomposes a quaternion into the Ry(-yaw)·Rx(pitch)·Rz(roll) order used by the
    // renderer and thrust code. Returns {yawDeg, pitchDeg, rollDeg}.
    // Matrix elements (JOML m<col><row>, column vectors): M[1][2] = -sin(pitch).
    public static float[] decomposeAttitude(Quaternionf q) {
        org.joml.Matrix3f m = new org.joml.Matrix3f().set(q);
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-m.m21(), -1f, 1f)));
        float yaw   = (float) -Math.toDegrees(Math.atan2(m.m20(), m.m22()));
        float roll  = (float) Math.toDegrees(Math.atan2(m.m01(), m.m11()));
        return new float[]{yaw, pitch, roll};
    }

    private float applyAttitude(Quaternionf q) {
        float[] a = decomposeAttitude(q);
        setYRot(normalizeYaw(a[0]));
        setXRot(a[1]);
        return a[2];
    }

    private double getHeightAboveGround() {
        int surfaceY = level().getHeight(
            Heightmap.Types.MOTION_BLOCKING,
            blockPosition().getX(),
            blockPosition().getZ()
        );
        return getY() - surfaceY;
    }

    // The prop discs + frame top present more area to airflow along body-up (disc-on) than
    // edge-on. Split the velocity into its body-up (axial) and in-plane (forward-flight)
    // components and drag each with its own CdA: low planar drag lets a forward tilt build
    // speed, while the higher axial drag brakes hard when you flip the drone to reverse.
    // The split is deterministic (no normalize of the tiny near-hover velocity), so it does
    // not jitter at low speed the way a single normalized-velocity CdA did.
    private static final double AXIAL_DRAG_MULT = 2.5;

    private void applyDrag(float dt) {
        Vec3 vel = getDeltaMovement();
        if (vel.length() <= 0.001) return;

        Quaternionf rot = attitudeQuat(getYRot(), getXRot(), entityData.get(DATA_ROLL));
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);
        Vec3 upV = new Vec3(up.x, up.y, up.z);

        Vec3 axial = upV.scale(vel.dot(upV));   // disc-on component (along body-up)
        Vec3 planar = vel.subtract(axial);       // edge-on component (forward flight)
        setDeltaMovement(dragComponent(axial, droneData.dragCoefficient * AXIAL_DRAG_MULT)
            .add(dragComponent(planar, droneData.dragCoefficient)));
    }

    private Vec3 dragComponent(Vec3 v, double cdA) {
        double speed = v.length();  // blocks/tick
        if (speed < 1e-4) return v;
        double vMs = speed * 20.0;
        cdA *= org.restor.fpv.Config.DEV_DRAG_MULT.get(); // dev braking/reversal-authority knob
        double dragAccel = 0.5 * AIR_DENSITY * cdA * vMs * vMs / mass(); // m/s²
        double drag = Math.min(dragAccel * MS2_TO_BT, speed * 0.5); // never reverse in one step
        return v.subtract(v.normalize().scale(drag));
    }

    // Effective mass/power with the dev-tab multipliers applied. Mass touches every
    // place real weight matters: thrust & drag accel, terminal velocity (emerges from
    // drag), rotation inertia and (via hover throttle) battery drain.
    private float mass() {
        return droneData.mass * (float) org.restor.fpv.Config.DEV_MASS_MULT.get().doubleValue();
    }

    private float effMotorPower() {
        return droneData.motorPower * (float) org.restor.fpv.Config.DEV_POWER_MULT.get().doubleValue();
    }

    private void applyGravity(float dt) {
        setDeltaMovement(getDeltaMovement().add(0, -GRAVITY * MS2_TO_BT, 0));
        limitVelocity();
    }

    // -------------------------------------------------------------------------
    //  Entity overrides
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            if (!hasPassenger(player)) {
                // Remember where the pilot boarded: a skin statue stands there for
                // the duration and the pilot is teleported back there on dismount.
                Vec3 boardPos = player.position();
                if (player.startRiding(this) && player instanceof ServerPlayer sp) {
                    mountReturnPos = boardPos;
                    sp.setInvisible(true);
                    spawnDummy(sp, boardPos);
                }
                return InteractionResult.SUCCESS;
            } else if (flightController.isArmed()) {
                flightController.cycleMode();
                return InteractionResult.SUCCESS;
            }
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
            // Camera position: forward from center along the drone's heading (yaw only).
            // Pitch/roll orientation is applied by FPVOverlay — don't rotate position here.
            double yaw = Math.toRadians(getYRot());
            boolean showProps = this instanceof RacingDroneEntity
                || this instanceof RemoteRacingDroneEntity
                || this instanceof KamikazeDroneEntity;
            double forward = getBbWidth() * (showProps ? 0.02 : 0.15);
            double x = getX() - Math.sin(yaw) * forward;
            double z = getZ() + Math.cos(yaw) * forward;

            // Camera above the frame top (real FPV top-plate camera mount) in every mode —
            // a mode-dependent height caused a camera jump when cycling modes mid-flight.
            double cameraY = getY() + getBbHeight() * (showProps ? 1.05 : 1.4) - passenger.getEyeHeight();
            moveFunction.accept(passenger, x, cameraY, z);
        }
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        net.minecraft.world.entity.EntityDimensions d = super.getDimensions(pose);
        float s = (float) org.restor.fpv.Config.DEV_HITBOX_SCALE.get().doubleValue();
        return net.minecraft.world.entity.EntityDimensions.scalable(d.width * s, d.height * s);
    }

    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        return super.makeBoundingBox()
            .move(0, org.restor.fpv.Config.DEV_HITBOX_Y_OFFSET.get(), 0);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return mountReturnPos != null ? mountReturnPos : position().add(0, 1.5, 0);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (source.getEntity() instanceof Player && hasPassenger(source.getEntity())) return false;
        if (level().isClientSide) return true;
        // Any real damage breaks the drone (punch, explosion, arrows, mobs) —
        // like a boat/minecart. Explosions vaporize it (no drop); a player in
        // creative breaks it without a drop; everything else drops the item.
        boolean explosion = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        if (!explosion && !creative) {
            spawnAtLocation(getDropStack());
        }
        discard();
        return true;
    }

    /** Item dropped when the drone is broken by a player. */
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.CINEWHOOP.get();
    }

    /** Full drop stack — modular drones override to keep their component NBT. */
    protected net.minecraft.world.item.ItemStack getDropStack() {
        return new net.minecraft.world.item.ItemStack(getDropItem());
    }

    // Constant attitude bias in °/s (scaled by throttle inside the override) —
    // used by modular drones with imbalanced props. Sign: matches stick outputs.
    protected float trimRollRate()  { return 0; }
    protected float trimPitchRate() { return 0; }
    protected float trimYawRate()   { return 0; }

    // -------------------------------------------------------------------------
    //  Save / Load
    // -------------------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_ROLL, tag.getFloat("Roll"));
        battery.load(tag.getCompound("Battery"));
        if (tag.contains("FlightMode")) {
            flightController.armed = tag.getBoolean("Armed");
            entityData.set(DATA_ARMED, flightController.armed);
            // Saved FlightMode intentionally ignored: single-mode build (ACRO only).
        }
        // CamV2 marks saves made after racing uptilt changed 35°→20°; older saved angles
        // are dropped once so existing drones pick up the new default from droneData.
        if (tag.contains("CameraAngle") && tag.getBoolean("CamV2")) {
            entityData.set(DATA_CAMERA_ANGLE, tag.getFloat("CameraAngle"));
        }
        if (tag.contains("RatePitch")) {
            entityData.set(DATA_RATE_PITCH, tag.getFloat("RatePitch"));
            entityData.set(DATA_RATE_ROLL,  tag.getFloat("RateRoll"));
            entityData.set(DATA_RATE_YAW,   tag.getFloat("RateYaw"));
        }
        if (tag.contains("LinkCode")) entityData.set(DATA_LINK_CODE, tag.getString("LinkCode"));
        entityData.set(DATA_FLIGHT_MODE, flightController.getMode().ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Roll", entityData.get(DATA_ROLL));
        CompoundTag batteryTag = new CompoundTag();
        battery.save(batteryTag);
        tag.put("Battery", batteryTag);
        tag.putBoolean("Armed", flightController.armed);
        tag.putInt("FlightMode", flightController.getMode().ordinal());
        tag.putBoolean("ModeV2", true);
        tag.putFloat("CameraAngle", entityData.get(DATA_CAMERA_ANGLE));
        tag.putBoolean("CamV2", true);
        tag.putFloat("RatePitch", entityData.get(DATA_RATE_PITCH));
        tag.putFloat("RateRoll",  entityData.get(DATA_RATE_ROLL));
        tag.putFloat("RateYaw",   entityData.get(DATA_RATE_YAW));
        tag.putString("LinkCode", entityData.get(DATA_LINK_CODE));
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private float clampPitch(float pitch) { return Math.max(-85, Math.min(85, pitch)); }
    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private float applyDeadband(float v, float threshold) { return Math.abs(v) < threshold ? 0 : v; }
    private void limitVelocity() {
        // Hard top-speed clamp, dev-tunable (m/s → blocks/tick). Real terminal velocities
        // still emerge from quadratic drag in applyDrag(); this is the upper safety cap.
        Vec3 vel = getDeltaMovement();
        double max = org.restor.fpv.Config.DEV_MAX_SPEED.get() / 20.0;
        if (vel.lengthSqr() > max * max) {
            setDeltaMovement(vel.scale(max / vel.length()));
        }
    }
    private float getBatteryPowerFactor() {
        float charge = level().isClientSide ? entityData.get(DATA_BATTERY) : battery.getPercentRemaining();
        return 0.35f + charge * 0.65f;
    }
    private float normalizeYaw(float yaw) {
        while (yaw >  180) yaw -= 360;
        while (yaw < -180) yaw += 360;
        return yaw;
    }

    // -------------------------------------------------------------------------
    //  Getters
    // -------------------------------------------------------------------------

    public float getRoll()     { return displayRoll; }
    public float getPrevRoll() { return rollO; }
    // Last stick values the local client sent (works for both RC and keyboard paths) —
    // used by the OSD stick-position boxes and throttle readout.
    public float getPredThrottle() { return predThrottle; }
    public float getPredPitch()    { return predPitch; }
    public float getPredRoll()     { return predRoll; }
    public float getPredYaw()      { return predYaw; }
    public float getSyncAngVelRoll()  { return entityData.get(DATA_ANGVEL_ROLL); }
    public float getSyncAngVelPitch() { return entityData.get(DATA_ANGVEL_PITCH); }
    public float getSyncAngVelYaw()   { return entityData.get(DATA_ANGVEL_YAW); }
    public float getVisualThrottle() { return entityData.get(DATA_VISUAL_THR); }
    public FlightMode getFlightMode(){ return FlightMode.values()[entityData.get(DATA_FLIGHT_MODE)]; }
    public float getBatteryPercent() { return entityData.get(DATA_BATTERY); }
    public float getRssi()           { return entityData.get(DATA_RSSI); }
    public float getCameraAngle()    { return entityData.get(DATA_CAMERA_ANGLE); }
    public void readFromItemTag(CompoundTag tag) {}

    public void adjustCameraAngle(float delta) {
        entityData.set(DATA_CAMERA_ANGLE, clamp(entityData.get(DATA_CAMERA_ANGLE) + delta, 0f, 60f));
    }

    public float getRatePitch() { return entityData.get(DATA_RATE_PITCH); }
    public float getRateRoll()  { return entityData.get(DATA_RATE_ROLL); }
    public float getRateYaw()   { return entityData.get(DATA_RATE_YAW); }

    // axis: 0 = pitch, 1 = roll, 2 = yaw. Called on the server from DroneRatesPacket,
    // and on the client for instant slider read-back (server sync confirms it).
    public void setRateMultiplier(int axis, float value) {
        float v = clamp(value, 0.1f, 3.0f);
        switch (axis) {
            case 0 -> entityData.set(DATA_RATE_PITCH, v);
            case 1 -> entityData.set(DATA_RATE_ROLL, v);
            case 2 -> entityData.set(DATA_RATE_YAW, v);
        }
    }

    // -------------------------------------------------------------------------
    //  Controller input (set by network packet on server)
    // -------------------------------------------------------------------------

    public void setControllerInput(float throttle, float pitch, float roll, float yaw,
                                   boolean arm, boolean modeSwitch) {
        useController   = true;
        ctrlThrottle    = throttle;
        ctrlPitch       = pitch;
        ctrlRoll        = roll;
        ctrlYaw         = yaw;
        ctrlArm         = arm;
        ctrlModeSwitch  = modeSwitch;
        ctrlAge         = 0;
    }

    public void clearControllerInput() { useController = false; ctrlAge = 0; }
    public boolean hasControllerInput() { return useController; }

    // -------------------------------------------------------------------------
    //  Remote control (station + link code)
    // -------------------------------------------------------------------------

    /** True for drone types flown from the control station (no riding). */
    public boolean isRemoteControllable() { return false; }

    /** Any pilot: a riding passenger or an active remote session. */
    public boolean hasAnyPilot() {
        return remotePilotId != null || getControllingPassenger() != null;
    }

    /** The controlling player (ridden or remote) for race timing, without side effects. Null if none. */
    public ServerPlayer getRacePilot() {
        if (getControllingPassenger() instanceof ServerPlayer sp) return sp;
        if (remotePilotId != null && level().getServer() != null) {
            ServerPlayer p = level().getServer().getPlayerList().getPlayer(remotePilotId);
            if (p != null && !p.isRemoved() && p.getCamera() == this) return p;
        }
        return null;
    }

    public String getLinkCode() { return entityData.get(DATA_LINK_CODE); }
    public void setLinkCode(String code) { entityData.set(DATA_LINK_CODE, code); }

    public boolean hasRemotePilot() { return remotePilotId != null; }

    public boolean isRemotePilot(ServerPlayer player) {
        return remotePilotId != null && remotePilotId.equals(player.getUUID());
    }

    public void beginRemoteControl(ServerPlayer player) {
        remotePilotId = player.getUUID();
        stationReturnPos = player.position();
        prevPilotXRot = player.getXRot();
        // The pilot's body follows the drone (that's what streams chunks to the
        // client) — keep it invisible and invulnerable for the duration.
        pilotWasInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        player.setInvisible(true);
        spawnDummy(player, stationReturnPos);
        // Vanilla camera-follow: teleports the pilot along with the drone and resets
        // the camera on sneak (wantsToStopRiding).
        player.setCamera(this);
    }

    // Validates the session each tick; ends it (and teleports the pilot back to the
    // station) when the pilot left, disconnected, or pressed sneak (vanilla resets
    // the camera to the player itself).
    private ServerPlayer resolveRemotePilot() {
        if (remotePilotId == null) return null;
        ServerPlayer p = level().getServer() != null
            ? level().getServer().getPlayerList().getPlayer(remotePilotId) : null;
        if (p == null || p.isRemoved() || p.getCamera() != this) {
            endRemoteControl();
            return null;
        }
        updateForcedChunks();
        return p;
    }

    public void endRemoteControl() {
        if (remotePilotId == null) return;
        ServerPlayer p = level().getServer() != null
            ? level().getServer().getPlayerList().getPlayer(remotePilotId) : null;
        remotePilotId = null;
        clearForcedChunks();
        clearChunkStreaming();
        discardDummy();
        if (p != null) {
            if (p.getCamera() == this) p.setCamera(p);
            p.setInvulnerable(pilotWasInvulnerable);
            p.setInvisible(false);
            if (stationReturnPos != null) {
                p.teleportTo(stationReturnPos.x, stationReturnPos.y, stationReturnPos.z);
            }
        }
        stationReturnPos = null;
    }

    // Visible pilot stand-in (rendered with the pilot's skin) at the given spot.
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

    // Pushes chunk packets around the drone straight to the pilot's client:
    // re-centers the client chunk cache on the drone and sends every loaded chunk
    // in radius that the client doesn't have yet. Unloaded chunks get an async
    // load/generation ticket and are sent as soon as they're ready.
    private void streamChunksToPilot(ServerPlayer pilot) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int r = Mth.clamp(org.restor.fpv.Config.DEV_CHUNK_RADIUS.get(), 2, 12);
        net.minecraft.world.level.ChunkPos center = chunkPosition();

        if (center.toLong() != lastStreamCenter) {
            lastStreamCenter = center.toLong();
            pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket(center.x, center.z));
        }

        int sentThisTick = 0;
        int candidates = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                candidates++;
                long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                if (streamedChunks.contains(key)) continue;
                net.minecraft.world.level.chunk.LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                        chunk, sl.getLightEngine(), null, null));
                    streamedChunks.add(key);
                    sentThisTick++;
                    if (streamTickets.remove(key)) removeStreamTicket(sl, key);
                } else if (streamTickets.add(key)) {
                    net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(cx, cz);
                    sl.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
                }
            }
        }
        lastStreamSentThisTick = sentThisTick;
        lastStreamCandidates = candidates;
        lastStreamPendingThisTick = streamTickets.size();

        // Forget entries far behind so flying back re-streams them (the client will
        // have dropped them when its cache center moved on), and release far tickets.
        int fr = r + 2;
        streamedChunks.removeIf(k -> chunkDist(k, center) > fr);
        streamTickets.removeIf(k -> {
            if (chunkDist(k, center) > fr) {
                removeStreamTicket(sl, k);
                return true;
            }
            return false;
        });
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

    private void clearChunkStreaming() {
        streamedChunks.clear();
        lastStreamCenter = Long.MIN_VALUE;
        if (!streamTickets.isEmpty() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (long k : streamTickets) removeStreamTicket(sl, k);
        }
        streamTickets.clear();
    }

    // Vanilla forceload tickets in a radius around the drone, moved as it flies —
    // keeps the drone's own chunks ENTITY-TICKING (streaming tickets above only
    // load chunks, they don't tick entities). Capped small: ticking is expensive.
    private void updateForcedChunks() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int r = Math.min(2, org.restor.fpv.Config.DEV_CHUNK_RADIUS.get());
        if (r <= 0) { clearForcedChunks(); return; }
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
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            endRemoteControl();
            clearForcedChunks();
            clearChunkStreaming();
        }
        super.remove(reason);
    }

    // Server entity-move packets: while this client predicts the drone (riding is
    // already suppressed by vanilla, but remote control has no passenger), do NOT
    // let vanilla snap position/rotation at 20Hz over the smooth local simulation —
    // that was visible as yaw-stutter. Stash the position and blend in clientTick.
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (level().isClientSide && predAge <= 5) {
            serverSyncPos = new Vec3(x, y, z);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    // Called client-side by ClientEventHandler with the same values it sends to the
    // server, enabling zero-latency rotation prediction in clientTick().
    public void setClientPredictedInputs(float throttle, float pitch, float roll, float yaw) {
        predThrottle = throttle;
        predPitch    = pitch;
        predRoll     = roll;
        predYaw      = yaw;
        predAge      = 0;
    }
}
