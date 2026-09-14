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
import org.restor.fpv.compat.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.CommonConfig;
import org.restor.fpv.antidrone.AntiDronePolicy;
import org.restor.fpv.antidrone.RadioInterferenceReceiver;
import org.restor.fpv.data.Battery;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.flight.FlightController;
import org.restor.fpv.flight.FlightMode;
import org.restor.fpv.flight.RadioFailsafe;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

public class DroneEntity extends Entity implements Pilotable, RadioInterferenceReceiver {

    private static final Logger LOGGER = LogUtils.getLogger();
    // Log every N server ticks while flying (20 = once/sec, 1 = every tick)
    private static final int LOG_INTERVAL = 20;
    private int logTick;
    // Finer physics-behaviour trace: [FPV-PHYS] every N ticks (2 = 10 Hz). Shows how the drone
    // climbs/sinks vs pitch, throttle and gravity so behaviour is readable straight from the log.
    private static final int PHYS_LOG_INTERVAL = 2;
    private int physLogTick;
    private float dbgThrustAccel;   // last thrust accel magnitude (m/s²), 0 when not thrusting
    private float dbgThrustVert;    // its vertical (world-up) component (m/s²)

    // Existing synced state
    private static final EntityDataAccessor<Float>   DATA_ROLL          = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_VISUAL_THR    = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT_MODE   = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED         = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float>   DATA_BATTERY       = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>   DATA_RSSI          = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RADIO_STATE   = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
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
    // Loaded payload charges (FPV_IDEAS §4). Synced so the OSD and belly model reflect the count.
    private static final EntityDataAccessor<Integer> DATA_PAYLOAD_AMMO  = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    // Mini rockets on the rocket-pod rails, fired by the pilot with the weapon key.
    private static final EntityDataAccessor<Integer> DATA_ROCKET_AMMO   = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    // Link diagnostics: last control packet the server acted on, and its tick cost.
    private static final EntityDataAccessor<Integer> DATA_ACK_SEQ       = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SRV_MSPT      = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_POSE_LOCK     = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);

    // Physics in SI units: 1 block = 1 m, 20 ticks/s. Velocities are stored in
    // blocks/tick, so accelerations (m/s²) convert as Δv_bt = a * dt / 20.
    private static final float GRAVITY = 9.81f;       // m/s²
    private static final float AIR_DENSITY = 1.225f;  // kg/m³
    private static final float MS2_TO_BT = 0.05f / 20f; // (a m/s²) * this = Δblocks/tick per tick
    // Throttle→thrust curve exponent. 2.0 = real prop physics (thrust ∝ RPM², RPM ∝ throttle):
    // hover stick = √(1/(maxThrottle·TWR)), so the presets' TWR alone sets hover height and there
    // is strong climb headroom above it. Full stick is unaffected, so top speed doesn't suffer.
    private static final float THROTTLE_EXP = 2.0f;
    // Heavy-lift rigs fly on a vertical-speed stick instead of a collective: centred = hold.
    private static final float MAX_CLIMB_RATE = 4.0f;   // m/s at full stick
    private static final float CLIMB_TAU = 0.6f;        // s to settle on the commanded rate
    private static final float PROP_STRIKE_JOULES_PER_DAMAGE = 25.0f;
    private static final float PROP_STRIKE_MAX_DAMAGE = 8.0f;
    private static final int PROP_STRIKE_COOLDOWN_TICKS = 10;

    public DroneData droneData;
    public FlightController flightController;
    public Battery battery;
    public float rssi = 1.0f;

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
    private float heldThrottle, heldPitch, heldRoll, heldYaw;
    private boolean ctrlModeSwitch, prevCtrlMode;
    private final RadioFailsafe radioFailsafe = new RadioFailsafe();
    private int rssiUpdateTick;
    private float baseRssi = 1.0f;
    private final java.util.Map<Long, ActiveRadioInterference> radioInterference = new java.util.HashMap<>();

    private record ActiveRadioInterference(float strength, long expiresAt) {}

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
    private boolean remoteFpv = true;
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
    private static final int MAX_STREAM_SENDS_PER_TICK = 4;
    private static final int MAX_STREAM_SENDS_SLOW_TICK = 64;
    private static final int MAX_STREAM_TICKETS = 6;
    private long lastStreamCenter = Long.MIN_VALUE;
    private long lastForcedCenter = Long.MIN_VALUE;
    // Diagnostics for the [CHUNKSTREAM] log line — filled in by streamChunksToPilot().
    private int lastStreamSentThisTick = 0;
    private int lastStreamPendingThisTick = 0;
    private int lastStreamCandidates = 0;
    // Latest position from server entity-move packets; blended softly in clientTick
    // instead of letting vanilla snap the entity (see lerpTo override).
    private Vec3 serverSyncPos;
    private final java.util.Map<Integer, Long> propStrikeCooldowns = new java.util.HashMap<>();
    private int lastControlSeq;
    private static final int CLIENT_POSE_TIMEOUT = 40;
    private int clientPoseTicks;
    private Vec3 clientImpact = Vec3.ZERO;
    private final java.util.List<Vec3> poseTrail = new java.util.ArrayList<>();
    private java.util.List<Vec3> lastTickTrail = java.util.List.of();
    private boolean predArmed;
    private int predArmTicks;
    private boolean poseHoriz;
    private boolean poseVert;
    private boolean poseGround;
    private Vec3 travelDir = Vec3.ZERO;

    public DroneEntity(EntityType<?> type, Level level) {
        this(type, level, DroneData.CINEWHOOP);
    }

    public DroneEntity(EntityType<?> type, Level level, DroneData data) {
        super(type, level);
        this.droneData = data;
        this.flightController = new FlightController(data);
        this.battery = new Battery(data.batteryCapacity, 4.2f);
        applyEnduranceProfile();
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
        this.entityData.define(DATA_ROLL,        0f);
        this.entityData.define(DATA_VISUAL_THR,  0f);
        this.entityData.define(DATA_FLIGHT_MODE, 0);
        this.entityData.define(DATA_ARMED,       false);
        this.entityData.define(DATA_BATTERY,     1f);
        this.entityData.define(DATA_RSSI,        1f);
        this.entityData.define(DATA_RADIO_STATE, RadioFailsafe.State.DISARMED.ordinal());
        this.entityData.define(DATA_VEL_X,       0f);
        this.entityData.define(DATA_VEL_Y,       0f);
        this.entityData.define(DATA_VEL_Z,       0f);
        this.entityData.define(DATA_YAW_SYNC,    0f);
        this.entityData.define(DATA_PITCH_SYNC,  0f);
        this.entityData.define(DATA_CAMERA_ANGLE, 0f);
        this.entityData.define(DATA_ANGVEL_ROLL,  0f);
        this.entityData.define(DATA_ANGVEL_PITCH, 0f);
        this.entityData.define(DATA_ANGVEL_YAW,   0f);
        this.entityData.define(DATA_RATE_PITCH,   1f);
        this.entityData.define(DATA_RATE_ROLL,    1f);
        this.entityData.define(DATA_RATE_YAW,     1f);
        this.entityData.define(DATA_LINK_CODE,    "");
        this.entityData.define(DATA_PAYLOAD_AMMO, 0);
        this.entityData.define(DATA_ROCKET_AMMO, 0);
        this.entityData.define(DATA_ACK_SEQ, 0);
        this.entityData.define(DATA_SRV_MSPT, 0);
        this.entityData.define(DATA_POSE_LOCK, false);
    }

    // ---- Payload (drop charge) ----
    public static final int   MAX_PAYLOAD  = 4;      // charges a drone can carry
    private static final float MIN_PAYLOAD_TWR = 1.6f;  // thrust/weight kept in hand when fully loaded
    private static final float CHARGE_MASS = 0.10f;  // kg each — extra mass hurts the flight
    private static final int   FIRE_COOLDOWN = 12;   // ticks between drops
    public  static final int   MAX_ROCKETS = 6;
    private static final float ROCKET_MASS = 0.07f;
    private static final int   WEAPON_COOLDOWN = 10;
    private static final float DART_SHOT_POWER = 2.0f;
    private int payloadCooldown;
    private int weaponCooldown;

    // ---- Autonomy (FPV_IDEAS §3 "Добрые дроны игрока") ----
    // A firmware chip in the assembly's firmware slot decides which modes the owner can cycle
    // by sneak-right-clicking with an empty hand. Autonomous flight is simple fly-to-point
    // movement (not ACRO physics); anchored modes orbit or hold a remembered spot.
    private DroneMode autoMode = DroneMode.OFF;
    private java.util.UUID companionOwnerId;
    private net.minecraft.core.BlockPos anchor;
    private boolean recalled;
    private LivingEntity autoTarget;
    private int targetScanCooldown;
    private float patrolPhase;

    // -------------------------------------------------------------------------
    //  Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        // Mirror the xRotO/yRotO pattern: save displayRoll as rollO BEFORE super.tick()
        // so partialTick interpolation in FpvCamera sees prev vs current each frame.
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
        if (poseTrail.isEmpty()) {
            lastTickTrail = java.util.List.of();
        } else {
            lastTickTrail = java.util.List.copyOf(poseTrail);
            poseTrail.clear();
        }
        if (clientPoseTicks > 0) {
            horizontalCollision = poseHoriz;
            verticalCollision = poseVert;
            setOnGround(poseGround);
        }
        poseHoriz = false;
        poseVert = false;
        poseGround = false;
        refreshRadioInterference();
        if (payloadCooldown > 0) payloadCooldown--;
        if (weaponCooldown > 0) weaponCooldown--;
        ServerPlayer pilot = getControllingPassenger() instanceof ServerPlayer sp ? sp : resolveRemotePilot();
        activePilot = pilot;
        boolean hasPilot = pilot != null;
        boolean projectedPilot = getControllingPassenger() != null || remoteFpv;
        if (hasPilot && projectedPilot) {
            pilot.setInvisible(true);
            if (getControllingPassenger() == null) {
                pilot.setPos(getX(), getY(), getZ());
                pilot.setDeltaMovement(Vec3.ZERO);
                pilot.fallDistance = 0;
            }
            // Riding pilots move with the drone, so vanilla already streams the world around
            // them; duplicating it here cost a full chunk-radius scan and re-sent packets the
            // client threw away ("Ignoring chunk since it's not in the view range").
            if (getControllingPassenger() == null || clientPoseTicks > 0) streamChunksToPilot(pilot);
            else clearChunkStreaming();
        } else {
            clearChunkStreaming();
        }

        if (hasPilot) {
            if (!pilotWasMounted) {
                angVelRoll  = 0;
                angVelPitch = 0;
                angVelYaw   = 0;
                pilotWasMounted = true;
            }

            RadioFailsafe.State previousRadioState = radioFailsafe.state();
            radioFailsafe.tick(radioPolicy());
            if (previousRadioState != RadioFailsafe.State.FAILSAFE_HOLD
                && radioFailsafe.state() == RadioFailsafe.State.FAILSAFE_HOLD) {
                heldThrottle = ctrlThrottle;
                heldPitch = ctrlPitch;
                heldRoll = ctrlRoll;
                heldYaw = ctrlYaw;
            }
            readControls();
            flightController.armed = radioFailsafe.state().motorsArmed();
            updateRssi(pilot);
            entityData.set(DATA_ACK_SEQ, lastControlSeq);
            entityData.set(DATA_SRV_MSPT, org.restor.fpv.ServerTickMonitor.msptTenths());

            float dt = 0.05f;
            // angVelRoll/Pitch/Yaw are persistent °/s — pass directly to FlightController.
            float currentRollRate  = angVelRoll;
            float currentPitchRate = angVelPitch;
            float currentYawRate   = angVelYaw;

            flightController.throttle = resolveThrottle(throttleInput);
            flightController.update(dt, currentRollRate, currentPitchRate, currentYawRate,
                entityData.get(DATA_ROLL), getXRot());

            if (CommonConfig.REALISTIC_BATTERY.get()) battery.tick(throttleInput, 1.0f);

            dbgThrustAccel = 0;
            dbgThrustVert = 0;
            if (clientPoseTicks <= 0) {
                if (flightController.isArmed()) {
                    applyFlightPhysics(dt);
                }
                applyDrag(dt);
                applyGravity(dt);
            }

            if (CommonConfig.REALISTIC_BATTERY.get() && battery.isEmpty()) {
                radioFailsafe.forceDisarm();
                flightController.armed = false;
            }

            // ---- fine physics trace (10 Hz): angle → thrust vs gravity → vertical/horizontal speed.
            // vThrust = vertical lift from thrust (m/s²); net = vThrust − g (pre-drag: >0 tends to
            // climb, <0 to sink); vSpd/hSpd in m/s (+up); alt = m above ground.
            if (CommonConfig.DEV_VERBOSE_LOG.get() && ++physLogTick >= PHYS_LOG_INTERVAL) {
                physLogTick = 0;
                Vec3 pv = getDeltaMovement();
                LOGGER.info("[FPV-PHYS] pitch={} roll={} thr={}% vThrust={} net={} vSpd={} hSpd={} alt={}",
                    String.format("%.1f", getXRot()),
                    String.format("%.1f", entityData.get(DATA_ROLL)),
                    String.format("%.0f", flightController.throttle * 100f),
                    String.format("%.1f", dbgThrustVert),
                    String.format("%.1f", dbgThrustVert - GRAVITY),
                    String.format("%.2f", pv.y * 20.0),
                    String.format("%.2f", Math.sqrt(pv.x * pv.x + pv.z * pv.z) * 20.0),
                    String.format("%.1f", getHeightAboveGround()));
            }

            // ---- physics diagnostics (once per second while piloted) ----
            if (CommonConfig.DEV_VERBOSE_LOG.get() && ++logTick >= LOG_INTERVAL) {
                logTick = 0;
                // Hover stick %: solve maxThrottle·throttle^EXP·thrustAccelMax = g for throttle.
                float thrustAccelMax = effMotorPower() * getBatteryPowerFactor() / mass();
                float hoverFrac = GRAVITY / Math.max(0.01f, droneData.maxThrottle * thrustAccelMax);
                float hoverPct = (float) Math.pow(Math.min(1f, hoverFrac), 1f / THROTTLE_EXP) * 100f;
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
            radioFailsafe.forceDisarm();
            baseRssi = 0.0f;
            rssi = 0.0f;
            ServerPlayer owner = autoMode.isAutonomous()
                && (!CommonConfig.REALISTIC_BATTERY.get() || !battery.isEmpty()) ? resolveCompanionOwner() : null;
            if (owner != null) {
                autonomousTick(owner);  // fly-to-point autonomy; no gravity/drag
            } else {
                idleFallback();         // owner offline/absent: motors cut, drone settles
            }
            logTick = 0;
        }

        if (!flightController.isArmed()) {
            throttleInput = 0;
            angVelRoll  = 0;
            angVelPitch = 0;
            angVelYaw   = 0;
        }

        if (clientPoseTicks > 0) {
            clientPoseTicks--;
            collideWithEntities(getDeltaMovement());
        } else {
            applyMovement();
        }

        // Sync state to clients
        Vec3 vel = getDeltaMovement();
        entityData.set(DATA_VISUAL_THR,  throttleInput);
        entityData.set(DATA_ARMED,       flightController.armed);
        entityData.set(DATA_FLIGHT_MODE, flightController.getMode().ordinal());
        entityData.set(DATA_BATTERY, CommonConfig.REALISTIC_BATTERY.get() ? battery.getPercentRemaining() : 1.0f);
        entityData.set(DATA_RSSI,        rssi);
        entityData.set(DATA_RADIO_STATE, radioFailsafe.state().ordinal());
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
        boolean poseLock = entityData.get(DATA_POSE_LOCK);
        boolean localPilot = predAge <= 5 && !poseLock;
        boolean clientSim = localPilot && clientPhysics();
        if (localPilot) {
            // Predict rotation from our own sticks — zero input latency, and immune to
            // sync-packet batching (the cause of the 20Hz freeze-jump camera stutter).
            flightController.armed = predictedArmed();
            flightController.throttle = resolveThrottle(predThrottle);
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
            if (!clientSim) {
                Quaternionf qc = attitudeQuat(getYRot(), getXRot(), displayRoll);
                Quaternionf qs = attitudeQuat(entityData.get(DATA_YAW_SYNC),
                    entityData.get(DATA_PITCH_SYNC), entityData.get(DATA_ROLL));
                qc.slerp(qs, 0.08f);
                displayRoll = applyAttitude(qc);
            }
        } else {
            // Not piloting locally (spectating someone else's drone): follow server values.
            // yRotO/xRotO are already saved by super.tick(), so Mth.rotLerp in FpvCamera
            // interpolates smoothly at 60+ fps; rollO/displayRoll mirror that split.
            setYRot(entityData.get(DATA_YAW_SYNC));
            setXRot(entityData.get(DATA_PITCH_SYNC));
            displayRoll = entityData.get(DATA_ROLL);
        }

        if (poseLock) {
            // Explosion cinematic: the server drives the camera, so stop simulating over it.
            setDeltaMovement(Vec3.ZERO);
            if (serverSyncPos != null) setPos(serverSyncPos.x, serverSyncPos.y, serverSyncPos.z);
            return;
        }

        if (clientSim) {
            // Full local simulation: a slow server tick (heavy physics mods) would otherwise
            // arrive as seconds-old velocity, which is what made the drone feel disconnected.
            if (flightController.isArmed()) applyFlightPhysics(dt);
            applyDrag(dt);
            applyGravity(dt);
        } else {
            setDeltaMovement(new Vec3(
                entityData.get(DATA_VEL_X),
                entityData.get(DATA_VEL_Y),
                entityData.get(DATA_VEL_Z)
            ));
        }

        // Ground
        Vec3 vel = getDeltaMovement();
        if (onGround() && vel.y < 0) {
            vel = new Vec3(vel.x * 0.7, 0, vel.z * 0.7);
            setDeltaMovement(vel);
        }

        moveWithBlockCollisionDamping(getDeltaMovement());

        // Blend toward the authoritative server position captured in lerpTo() —
        // gentle so it never reads as a snap; hard snap only if truly far off.
        if (clientSim && serverSyncPos != null) {
            // We are authoritative here, so the only thing worth following is a real
            // server-side relocation (teleport, race reset), never ordinary lag drift.
            Vec3 far = serverSyncPos.subtract(position());
            if (far.lengthSqr() > 128.0 * 128.0) setPos(serverSyncPos.x, serverSyncPos.y, serverSyncPos.z);
        } else if (localPilot && serverSyncPos != null) {
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

        if (clientSim) sendClientPose();
    }

    public Vec3 travelDirection() {
        if (travelDir.lengthSqr() > 1.0e-6) return travelDir;
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() > 1.0e-6) return v.normalize();
        return getLookAngle();
    }

    /**
     * A point {@code distance} back along the path actually flown, walking the recorded
     * poses in reverse. A straight step along the last velocity lands the camera in a wall
     * or beside it, because the velocity at impact already slides along the surface.
     */
    public Vec3 cameraTrackBack(double distance) {
        Vec3 cur = position();
        double remaining = distance;
        for (int i = lastTickTrail.size() - 1; i >= 0; i--) {
            Vec3 prev = lastTickTrail.get(i);
            Vec3 step = prev.subtract(cur);
            double d = step.length();
            if (d < 1.0e-4) continue;
            if (d >= remaining) return cur.add(step.scale(remaining / d));
            remaining -= d;
            cur = prev;
        }
        return cur.subtract(travelDirection().scale(remaining));
    }

    private static boolean clientPhysics() {
        return CommonConfig.SPEC.isLoaded() && CommonConfig.CLIENT_PHYSICS.get();
    }

    private byte collisionFlags() {
        int f = 0;
        if (horizontalCollision) f |= org.restor.fpv.network.DronePosePacket.FLAG_HORIZONTAL;
        if (verticalCollision)   f |= org.restor.fpv.network.DronePosePacket.FLAG_VERTICAL;
        if (onGround())          f |= org.restor.fpv.network.DronePosePacket.FLAG_GROUND;
        return (byte) f;
    }

    /** While locked the server is authoritative again — used for the explosion camera. */
    public void setPoseLocked(boolean locked) {
        if (level().isClientSide) return;
        entityData.set(DATA_POSE_LOCK, locked);
        if (locked) {
            clientPoseTicks = 0;
            poseHoriz = false;
            poseVert = false;
            poseGround = false;
        }
    }

    private float bodyRoll() {
        return level().isClientSide ? displayRoll : entityData.get(DATA_ROLL);
    }

    private void sendClientPose() {
        Vec3 pos = position();
        Vec3 vel = getDeltaMovement();
        org.restor.fpv.compat.PacketDistributor.sendToServer(
            new org.restor.fpv.network.DronePosePacket(getId(), pos.x, pos.y, pos.z,
                getYRot(), getXRot(), displayRoll,
                (float) vel.x, (float) vel.y, (float) vel.z,
                angVelRoll, angVelPitch, angVelYaw,
                (float) clientImpact.x, (float) clientImpact.y, (float) clientImpact.z,
                collisionFlags()));
        clientImpact = Vec3.ZERO;
    }

    /** Client-authoritative pose from the pilot. Server stops integrating while these keep coming. */
    public void applyClientPose(double x, double y, double z, float yaw, float pitch, float roll,
                                float vx, float vy, float vz,
                                float avr, float avp, float avy, Vec3 impact,
                                boolean collHoriz, boolean collVert, boolean collGround) {
        if (!clientPhysics()) return;
        if (entityData.get(DATA_POSE_LOCK)) return;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(vx) || !Float.isFinite(vy) || !Float.isFinite(vz)
            || !Float.isFinite(avr) || !Float.isFinite(avp) || !Float.isFinite(avy)) return;
        if (distanceToSqr(x, y, z) > 512.0 * 512.0) return;

        // The velocity that arrives after a hit is already damped along the wall, so the
        // impact vector (the part that was killed) is the only honest approach direction.
        if (impact.lengthSqr() > 1.0e-6) {
            travelDir = impact.normalize();
        } else {
            Vec3 v = new Vec3(vx, vy, vz);
            if (v.lengthSqr() > 0.01) travelDir = v.normalize();
        }

        setPos(x, y, z);
        setYRot(yaw);
        setXRot(pitch);
        entityData.set(DATA_ROLL, roll);
        setDeltaMovement(vx, vy, vz);
        angVelRoll = avr;
        angVelPitch = avp;
        angVelYaw = avy;
        clientPoseTicks = CLIENT_POSE_TIMEOUT;
        // Sticky within the server tick: a collision reported by any of the ~20 poses that
        // land between two server ticks must not be lost to the poses that follow it.
        poseHoriz |= collHoriz;
        poseVert |= collVert;
        poseGround |= collGround;
        if (poseTrail.size() < 256) poseTrail.add(new Vec3(x, y, z));
        if (impact.lengthSqr() > 1.0e-8) onMovementImpact(impact);
    }

    /** Every client pose applied since the previous server tick, oldest first. */
    public java.util.List<Vec3> lastTickPath() { return lastTickTrail; }

    // -------------------------------------------------------------------------
    //  Controls
    // -------------------------------------------------------------------------

    private void readControls() {
        readControllerInput();
    }

    private void readControllerInput() {
        RadioFailsafe.State state = radioFailsafe.state();
        if (state == RadioFailsafe.State.ARMED) {
            applyRadioInput(ctrlThrottle, ctrlPitch, ctrlRoll, ctrlYaw);
        } else if (state == RadioFailsafe.State.FAILSAFE_HOLD) {
            applyRadioInput(heldThrottle, heldPitch, heldRoll, heldYaw);
        } else if (state == RadioFailsafe.State.FAILSAFE_NEUTRAL) {
            applyRadioInput(CommonConfig.RADIO_FAILSAFE_THROTTLE.get().floatValue(), 0, 0, 0);
        } else {
            applyRadioInput(0, 0, 0, 0);
        }

        if (state == RadioFailsafe.State.ARMED && ctrlModeSwitch && !prevCtrlMode && controlCooldown <= 0) {
            flightController.cycleMode();
            controlCooldown = 5;
        }
        prevCtrlMode = ctrlModeSwitch;
        if (controlCooldown > 0) controlCooldown--;
    }

    private void applyRadioInput(float throttle, float pitch, float roll, float yaw) {
        throttleInput = clamp(throttle, 0, 1);
        flightController.targetPitch = applyDeadband(clamp(pitch, -1, 1), 0.05f);
        flightController.targetRoll = applyDeadband(clamp(roll, -1, 1), 0.05f);
        flightController.targetYaw = applyDeadband(clamp(yaw, -1, 1), 0.05f);
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
        moveWithBlockCollisionDamping(vel);
        collideWithEntities(vel);
    }

    private void moveWithBlockCollisionDamping(Vec3 vel) {
        if (vel.lengthSqr() > 0.01) travelDir = vel.normalize();
        Vec3 before = position();
        move(MoverType.SELF, vel);
        Vec3 moved = position().subtract(before);
        Vec3 damped = dampBlockedVelocity(vel, moved);
        setDeltaMovement(damped);
        Vec3 velocityDelta = vel.subtract(damped);
        if (velocityDelta.lengthSqr() > 1.0e-8) {
            if (level().isClientSide) clientImpact = clientImpact.add(velocityDelta);
            else onMovementImpact(velocityDelta);
        }
    }

    private static Vec3 dampBlockedVelocity(Vec3 intended, Vec3 moved) {
        double eps = 1.0e-5;
        boolean blockedX = Math.abs(intended.x) > eps && Math.abs(moved.x) < Math.abs(intended.x) * 0.75;
        boolean blockedY = Math.abs(intended.y) > eps && Math.abs(moved.y) < Math.abs(intended.y) * 0.75;
        boolean blockedZ = Math.abs(intended.z) > eps && Math.abs(moved.z) < Math.abs(intended.z) * 0.75;
        if (!blockedX && !blockedY && !blockedZ) return intended;

        double slide = 0.78;
        double normal = 0.08;
        return new Vec3(
            blockedX ? moved.x * normal : intended.x * slide,
            blockedY ? moved.y * normal : intended.y * slide,
            blockedZ ? moved.z * normal : intended.z * slide);
    }

    // Prop strike: flying into a living entity damages and shoves it, kills most of
    // the drone's speed, and lets subclasses react (kamikaze detonates).
    private void collideWithEntities(Vec3 velBefore) {
        double speed = velBefore.length();
        if (speed < 0.1) return; // under ~2 m/s a touch is harmless
        var hits = level().getEntities(this, getBoundingBox().inflate(0.1),
            e -> e instanceof LivingEntity && !e.isSpectator() && !hasPassenger(e)
                && !(e instanceof OperatorDummyEntity)
                && canHitCompanionTarget(e)
                && !(e instanceof ServerPlayer sp && isRemotePilot(sp)));
        if (hits.isEmpty()) return;
        long gameTime = level().getGameTime();
        propStrikeCooldowns.entrySet().removeIf(entry ->
            gameTime - entry.getValue() >= PROP_STRIKE_COOLDOWN_TICKS);
        for (Entity e : hits) {
            Long lastHit = propStrikeCooldowns.get(e.getId());
            if (lastHit == null || gameTime - lastHit >= PROP_STRIKE_COOLDOWN_TICKS) {
                double relativeSpeedMs = velBefore.subtract(e.getDeltaMovement()).length() * 20.0;
                double kineticEnergy = 0.5 * mass() * relativeSpeedMs * relativeSpeedMs;
                float damage = Math.min((float) (kineticEnergy / PROP_STRIKE_JOULES_PER_DAMAGE),
                    PROP_STRIKE_MAX_DAMAGE);
                propStrikeCooldowns.put(e.getId(), gameTime);
                e.hurt(level().damageSources().thrown(this, activePilot), damage);
            }
            e.push(velBefore.x * 0.8, Math.abs(velBefore.y) * 0.4 + 0.1, velBefore.z * 0.8);
        }
        Vec3 beforeImpact = getDeltaMovement();
        setDeltaMovement(beforeImpact.scale(0.35));
        onMovementImpact(beforeImpact.subtract(getDeltaMovement()));
        onEntityCollision();
    }

    protected void onMovementImpact(Vec3 velocityDelta) {}

    /** Called after hitting a living entity at speed — kamikaze overrides to detonate. */
    protected void onEntityCollision() {}

    // Fraction of rotation inputs mixed into each motor (tuned so full stick at mid-throttle
    // doesn't clip motors to zero on the low side).
    private static final float ROT_MIX = 0.5f;

    private void applyFlightPhysics(float dt) {
        float ro = flightController.rollOutput;
        float po = flightController.pitchOutput;
        float yo = flightController.yawOutput;

        if (!level().isClientSide) {
            entityData.set(DATA_ROLL, integrateRotation(dt, ro, po, yo, entityData.get(DATA_ROLL)));
        }

        // Non-linear throttle→thrust curve (thrust ∝ throttle^EXP, since prop thrust ∝ RPM²).
        // A realistic build with TWR ~10 then hovers near 30% stick instead of a linear ~10% —
        // that's what stops the drone feeling "airy"/overpowered and lets a tilt at cruise drop
        // altitude like a real quad. throttle clamped to [0,1] (ACRO never reverses); maxThrottle
        // caps this drone's usable top end.
        float throttle = Math.max(0f, Math.min(1f, flightController.throttle));
        float totalFrac = droneData.maxThrottle * (float) Math.pow(throttle, THROTTLE_EXP);

        // Ground effect: extra lift close to terrain surface
        double height = getHeightAboveGround();
        float groundEffect = (height > 0.05 && height < droneData.armLength * 5.0)
            ? 1.0f + 0.25f * (float) Math.exp(-height / droneData.armLength)
            : 1.0f;

        // Thrust acceleration in m/s²: max thrust (Newtons) is this drone's own — a preset or a
        // module-built assembly (DroneAssembly.totalThrustN) — divided by its own mass. Both are
        // per-drone; no global override. Heavier build = higher hover throttle and less punch,
        // as in reality. DEV_POWER_MULT stays as a global fine multiplier for quick testing.
        float thrustAccel = totalFrac * groundEffect * getBatteryPowerFactor()
            * effMotorPower() / mass();

        // Rotate body-up axis by current orientation to get world-space thrust direction.
        // In Minecraft's left-handed coordinate system, rotateZ(+roll) correctly tilts the
        // up-vector toward the right side (−X when facing south = west = the player's right).
        Quaternionf rot = attitudeQuat(getYRot(), getXRot(), bodyRoll());
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);
        float dv = thrustAccel * MS2_TO_BT;
        setDeltaMovement(getDeltaMovement().add(up.x * dv, up.y * dv, up.z * dv));
        dbgThrustAccel = thrustAccel;
        dbgThrustVert = thrustAccel * up.y;   // vertical lift the thrust provides right now

        // limitVelocity() NOT called here — it runs in applyGravity() after gravity is subtracted.
        // Calling it here caused: thrust cap (1.245) → gravity subtracts 1.177 → net ≈ 0.07 (hover!)
        // regardless of throttle. Fix: cap only after the full physics step is complete.
    }

    // Shared by server physics and client prediction: lerps angular velocity toward the
    // commanded rates, rotates the orientation about the drone's own BODY axes, and
    // returns the new roll angle (roll storage differs — entityData server, displayRoll client).
    private float integrateRotation(float dt, float ro, float po, float yo, float curRoll) {
        // Target angular rates in °/s from stick output, scaled by this drone's multipliers.
        // Yaw negated: Minecraft yRot increases to the right, so positive yaw output = yRot decreasing = left.
        float rateRoll  = entityData.get(DATA_RATE_ROLL);
        float ratePitch = entityData.get(DATA_RATE_PITCH);
        float rateYaw   = entityData.get(DATA_RATE_YAW);
        // Trim hooks (°/s): modular drones add a constant bias here when their motor
        // thrust is imbalanced (e.g. a weaker prop on one corner).
        float targetRollRate  =  clamp(ro, -1f, 1f) * droneData.maxRollRate  * rateRoll  + trimRollRate();
        float targetPitchRate =  clamp(po, -1f, 1f) * droneData.maxPitchRate * ratePitch + trimPitchRate();
        float targetYawRate   = -clamp(yo, -1f, 1f) * droneData.maxYawRate   * rateYaw   - trimYawRate();

        // Lerp angular velocity toward target. Heavier drones rotate AND stop rotating more
        // sluggishly — this is where mass is "felt" in the sticks. Referenced to a 0.5 kg
        // nominal quad; a 1 kg rig turns ~1.3× lazier, a whoop snappier.
        float massInertia = Mth.clamp((float) Math.pow(mass() / 0.5f, 0.45f), 0.5f, 2.0f);
        float resp = Math.min(droneData.rotationResponsiveness / massInertia * dt, 1.0f);
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

    // The prop discs + frame top only present their large area to airflow that hits the disc
    // HEAD-ON — i.e. air flowing up through the disc against the thrust (the drone descending,
    // parachuting, or flipped 180° to brake). Split the velocity into its body-up (axial) and
    // in-plane (forward-flight) components and drag each with its own CdA.
    //
    // Directional axial drag: the high multiplier applies ONLY when the axial velocity opposes
    // thrust (moving "into" the disc, vel·up < 0) — that's braking/descent. When the drone is
    // instead accelerating ALONG its thrust axis (pitched forward and flying forward, or
    // climbing), that motion is edge-on to the disc and uses the low planar drag. Without this,
    // a hard forward tilt braked itself: the forward velocity of a nose-down drone is largely
    // "axial", so the 2.5x drag capped and even reversed the speed gain past ~45° pitch and made
    // the drone sink instead of translating — the opposite of how a real quad converts tilt to
    // speed. The split is deterministic (no normalize of the tiny near-hover velocity), so it
    // does not jitter at low speed the way a single normalized-velocity CdA did.
    private static final double AXIAL_DRAG_MULT = 0.95;

    // Disc drag while DESCENDING is scaled between these two by throttle. Off the throttle the
    // rotor is unloaded and barely brakes, so the drone drops fast (FALL ≈ ¼ of the powered disc
    // drag → terminal fall ≈ 2× faster); powered up it loads the disc for the full parachute/brake
    // (AXIAL_DRAG_MULT). Keeps mass mattering (terminal ∝ √mass) while killing the airy float.
    private static final double FALL_DRAG_MULT = 0.29;

    // Linear momentum bleed (per second): Δv = -k·v each second, applied to the HORIZONTAL
    // velocity only. The aero drag below is quadratic (∝ v²) so it barely acts at low/medium
    // speed, which left a long paraglider-like horizontal coast after cutting throttle. This term
    // scales with v and bleeds that drift, yet is negligible next to the quadratic term near top
    // speed so it does NOT lower max speed. Deliberately NOT applied to vertical velocity — doing
    // so parachuted the fall and made the drone feel airy; falls should stay fast. Tied to the
    // Drag dev slider so the overall horizontal "inertia / glide" feel is tunable.
    private static final double LINEAR_DAMP = 0.18;

    private void applyDrag(float dt) {
        Vec3 vel = getDeltaMovement();
        if (vel.length() <= 0.001) return;

        Quaternionf rot = attitudeQuat(getYRot(), getXRot(), bodyRoll());
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);
        Vec3 upV = new Vec3(up.x, up.y, up.z);

        double axialSpeed = vel.dot(upV);        // signed: <0 = moving into the disc (brake/descend)
        Vec3 axial = upV.scale(axialSpeed);      // component along body-up
        Vec3 planar = vel.subtract(axial);       // in-plane (translational) component
        // Descending: interpolate the disc drag by throttle — idle = light (fast fall),
        // full = loaded disc (heavy brake). Climbing/edge-on uses the low planar drag.
        float thr = Math.max(0f, Math.min(1f, flightController.throttle));
        double descentMult = Mth.lerp(thr, FALL_DRAG_MULT, AXIAL_DRAG_MULT);
        double axialCdA = axialSpeed < 0
            ? droneData.dragCoefficient * descentMult        // disc-on: throttle-scaled brake/parachute
            : droneData.dragCoefficient;                     // moving along thrust: edge-on, low drag
        setDeltaMovement(dragComponent(axial, axialCdA)
            .add(dragComponent(planar, droneData.dragCoefficient * planarDragMult())));

        // Horizontal-only linear momentum bleed on top of the quadratic aero drag (see LINEAR_DAMP).
        double k = LINEAR_DAMP * org.restor.fpv.Config.DEV_DRAG_MULT.get();
        double hFactor = Math.max(0.0, 1.0 - k * dt);
        Vec3 dm = getDeltaMovement();
        setDeltaMovement(dm.x * hFactor, dm.y, dm.z * hFactor);
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

    // This drone's mass in kg — from its own build (preset DroneData or module-derived
    // DroneAssembly.totalMassKg), NOT a global slider. Mass touches every place weight matters:
    // thrust & drag accel, terminal fall speed, rotation inertia (via rotationResponsiveness),
    // impact damage and (via hover throttle) battery drain.
    private float mass() {
        return Math.max(0.01f, droneData.mass + getPayloadAmmo() * CHARGE_MASS + getRocketAmmo() * ROCKET_MASS);
    }

    // Max thrust in Newtons from this drone's build, times a global fine-test multiplier.
    private float effMotorPower() {
        return droneData.motorPower * (float) org.restor.fpv.Config.DEV_POWER_MULT.get().doubleValue();
    }

    private void applyGravity(float dt) {
        // Constant g: every mass falls at the same acceleration. Heavier drones fall FASTER in
        // practice because thrust (÷mass) can't hold them and drag (÷mass) brakes them less —
        // that emerges from the thrust/drag terms, not from scaling g.
        setDeltaMovement(getDeltaMovement().add(0, -GRAVITY * MS2_TO_BT, 0));
        limitVelocity();
    }

    // -------------------------------------------------------------------------
    //  Entity overrides
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            if (!canPlayerAccess(player)) return InteractionResult.FAIL;
            claimOwner(player);
        }
        // Sneak + drop charge in hand loads the payload slot; sneak + empty hand toggles
        // companion (follow) mode.
        if (player.isSecondaryUseActive()) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (held.is(org.restor.fpv.item.ModItems.DROP_CHARGE.get())) {
                if (!level().isClientSide) loadPayload(player, held);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(org.restor.fpv.item.ModItems.MINI_ROCKET.get())) {
                if (!level().isClientSide) loadRockets(player, held);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.isEmpty()) {
                if (!level().isClientSide && !unloadOrdnance(player)) cycleMode(player);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        // While flying itself, right-click talks to the firmware (trades, cargo box) instead of riding.
        if (autoMode.isAutonomous()) {
            if (!level().isClientSide && !autonomousInteract(player)) openStorage(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            if (!hasPassenger(player)) {
                if (player instanceof ServerPlayer sp) startPilotSession(sp);
                return InteractionResult.SUCCESS;
            } else if (flightController.isArmed()) {
                flightController.cycleMode();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /** Radio entry point: goggles give the mounted FPV feed, bare eyes give line-of-sight. */
    public boolean startPilotSession(ServerPlayer player) {
        if (!isFlightReady()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.airframe_incomplete"), true);
            return false;
        }
        if (!org.restor.fpv.item.RcLink.hasTransmitter(player)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.need_transmitter"), true);
            return false;
        }
        if (org.restor.fpv.item.RcLink.hasGoggles(player)) return beginRidingControl(player);
        if (hasAnyPilot() || !canPlayerAccess(player)) return false;
        beginRemoteControl(player, false);
        return true;
    }

    /** Starts the same mounted FPV session as right-clicking a regular racing drone. */
    public boolean beginRidingControl(ServerPlayer player) {
        if (level().isClientSide || hasAnyPilot() || !canPlayerAccess(player)) return false;
        Vec3 boardPos = safeReturnPos(player);
        // Forced mounting lets launch stations use RemoteDroneEntity subclasses while
        // their normal right-click remains reserved for link-code configuration.
        if (!player.startRiding(this, true)) return false;
        claimOwner(player);
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

    @Override
    public LivingEntity getControllingPassenger() {
        var e = getFirstPassenger();
        if (e instanceof LivingEntity le) return le;
        return null;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return entity instanceof Player player && canPlayerAccess(player);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (hasPassenger(passenger)) {
            // Camera position: forward from center along the drone's heading (yaw only).
            // Pitch/roll orientation is applied by FpvCamera — don't rotate position here.
            double yaw = Math.toRadians(getYRot());
            boolean showProps = this instanceof RacingDroneEntity
                || this instanceof RemoteRacingDroneEntity
                || this instanceof KamikazeDroneEntity;
            double forward = getBbWidth() * (showProps ? 0.02 : 0.15);
            double x = getX() - Math.sin(yaw) * forward;
            double z = getZ() + Math.cos(yaw) * forward;

            // Camera vertical position, as a fraction of the frame height (dev-tunable):
            // 0 = bottom, 0.5 = center, 1 = top plate. Same value drives the remote-view eye
            // height below so ridden and remote cameras sit at the same spot.
            double camFrac = org.restor.fpv.Config.FPV_CAMERA_HEIGHT.get();
            double cameraY = getY() + getBbHeight() * camFrac - passenger.getEyeHeight();
            moveFunction.accept(passenger, x, cameraY, z);
        }
    }

    @Override
    public boolean isPickable() { return true; }

    // Remote-control view: the camera IS this entity, so its eye height sets the camera's vertical
    // spot. Match the ridden camera (FPV_CAMERA_HEIGHT fraction of the frame) so both views agree.
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        net.minecraft.world.entity.EntityDimensions d = super.getDimensions(pose);
        float s = (float) org.restor.fpv.Config.DEV_HITBOX_SCALE.get().doubleValue();
        return net.minecraft.world.entity.EntityDimensions.scalable(d.width * s, d.height * s);
    }

    @Override
    protected float getEyeHeight(net.minecraft.world.entity.Pose pose,
                                 net.minecraft.world.entity.EntityDimensions dimensions) {
        return dimensions.height * (float) org.restor.fpv.Config.FPV_CAMERA_HEIGHT.get().doubleValue();
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
        if (org.restor.fpv.CommonConfig.DEV_NO_DAMAGE.get() && !(source.getEntity() instanceof Player)) return false;
        if (source.getEntity() instanceof Player && hasPassenger(source.getEntity())) return false;
        if (level().isClientSide) return true;
        if (!isAuthorizedPlayerDamage(source)) return false;
        // Any real damage breaks the drone (punch, explosion, arrows, mobs) —
        // like a boat/minecart. Explosions vaporize it (no drop); a player in
        // creative breaks it without a drop; everything else drops the item.
        boolean explosion = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        if (!explosion && source.getEntity() instanceof Player p) {
            org.restor.fpv.util.CraftPickup.giveTo(p, getDropStack());
        } else if (!explosion) {
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
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(getDropItem());
        CompoundTag persistent = new CompoundTag();
        CompoundTag batteryTag = new CompoundTag();
        battery.save(batteryTag);
        persistent.put("Battery", batteryTag);
        if (companionOwnerId != null) persistent.putUUID("CompanionOwner", companionOwnerId);
        CustomData.update(stack, tag -> tag.merge(persistent));
        return stack;
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
        if (tag.contains("Battery")) battery.load(tag.getCompound("Battery"));
        if (tag.contains("FlightMode")) {
            radioFailsafe.forceDisarm();
            flightController.armed = false;
            entityData.set(DATA_ARMED, false);
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
        if (tag.contains("PayloadAmmo")) entityData.set(DATA_PAYLOAD_AMMO,
            Math.max(0, Math.min(MAX_PAYLOAD, tag.getInt("PayloadAmmo"))));
        if (tag.contains("RocketAmmo")) entityData.set(DATA_ROCKET_AMMO,
            Math.max(0, Math.min(MAX_ROCKETS, tag.getInt("RocketAmmo"))));
        autoMode = tag.contains("AutoMode") ? DroneMode.byOrdinal(tag.getInt("AutoMode"))
            : tag.getBoolean("Follow") ? DroneMode.FOLLOW : DroneMode.OFF;
        if (tag.contains("Anchor")) anchor = net.minecraft.core.BlockPos.of(tag.getLong("Anchor"));
        if (tag.hasUUID("CompanionOwner")) companionOwnerId = tag.getUUID("CompanionOwner");
        if (tag.contains("MountReturnX")) mountReturnPos = new Vec3(
            tag.getDouble("MountReturnX"), tag.getDouble("MountReturnY"), tag.getDouble("MountReturnZ"));
        if (tag.contains("StationReturnX")) stationReturnPos = new Vec3(
            tag.getDouble("StationReturnX"), tag.getDouble("StationReturnY"), tag.getDouble("StationReturnZ"));
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
        tag.putInt("PayloadAmmo", getPayloadAmmo());
        tag.putInt("RocketAmmo", getRocketAmmo());
        tag.putInt("AutoMode", autoMode.ordinal());
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
        if (companionOwnerId != null) tag.putUUID("CompanionOwner", companionOwnerId);
        if (mountReturnPos != null) {
            tag.putDouble("MountReturnX", mountReturnPos.x);
            tag.putDouble("MountReturnY", mountReturnPos.y);
            tag.putDouble("MountReturnZ", mountReturnPos.z);
        }
        if (stationReturnPos != null) {
            tag.putDouble("StationReturnX", stationReturnPos.x);
            tag.putDouble("StationReturnY", stationReturnPos.y);
            tag.putDouble("StationReturnZ", stationReturnPos.z);
        }
    }

    /** Boarding spot to come back to; never the drone itself, which would strand the pilot midair. */
    protected Vec3 safeReturnPos(ServerPlayer player) {
        Vec3 pos = player.position();
        return pos.distanceToSqr(position()) < 1.0E-4 ? pos.add(0, 1.0, 0) : pos;
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private float clampPitch(float pitch) { return Math.max(-85, Math.min(85, pitch)); }
    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private float applyDeadband(float v, float threshold) { return Math.abs(v) < threshold ? 0 : v; }
    private RadioFailsafe.Policy radioPolicy() {
        return new RadioFailsafe.Policy(
            CommonConfig.RADIO_PACKET_TIMEOUT_TICKS.get(),
            CommonConfig.RADIO_FAILSAFE_HOLD_TICKS.get(),
            CommonConfig.RADIO_FAILSAFE_NEUTRAL_TICKS.get(),
            CommonConfig.RADIO_RECOVERY_PACKETS.get(),
            CommonConfig.RADIO_ARM_THROTTLE_MAXIMUM.get().floatValue());
    }

    private void updateRssi(ServerPlayer pilot) {
        if (!CommonConfig.REALISTIC_RADIO.get()) {
            baseRssi = 1.0f;
            rssi = 1.0f;
            radioInterference.clear();
            return;
        }
        if (++rssiUpdateTick < CommonConfig.RADIO_RSSI_UPDATE_TICKS.get()) return;
        rssiUpdateTick = 0;

        Vec3 source = remotePilotId != null && stationReturnPos != null
            ? stationReturnPos.add(0, 1.0, 0) : pilot.getEyePosition();
        double range = CommonConfig.RADIO_BASE_RANGE.get() * radioRangeMultiplier();
        float sample = clamp01((float) (1.0 - position().distanceTo(source) / Math.max(1.0, range)));
        if (sample > 0.0f && level().clip(new ClipContext(getEyePosition(), source,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
            sample *= CommonConfig.RADIO_LOS_PENALTY.get().floatValue();
        }
        float alpha = CommonConfig.RADIO_RSSI_EMA_ALPHA.get().floatValue();
        baseRssi += (sample - baseRssi) * alpha;
        baseRssi = clamp01(baseRssi);
        refreshRadioInterference();
    }

    @Override
    public void applyRadioInterference(long sourceId, float strength, int durationTicks) {
        if (!CommonConfig.REALISTIC_RADIO.get() || level().isClientSide
            || !Float.isFinite(strength) || strength <= 0.0f || durationTicks <= 0
            || isRadioInterferenceBypassed()) return;
        float boundedStrength = clamp01(strength);
        long expiresAt = level().getGameTime() + Math.min(durationTicks, 400);
        radioInterference.put(sourceId, new ActiveRadioInterference(boundedStrength, expiresAt));
        refreshRadioInterference();
    }

    private void refreshRadioInterference() {
        long now = level().getGameTime();
        radioInterference.values().removeIf(interference -> interference.expiresAt() <= now);
        float strength = 0.0f;
        for (ActiveRadioInterference interference : radioInterference.values()) {
            strength = Math.max(strength, interference.strength());
        }
        rssi = isRadioInterferenceBypassed() ? baseRssi : AntiDronePolicy.applyToRssi(baseRssi, strength);
    }

    private boolean isRadioReceptionBlocked() {
        if (!CommonConfig.REALISTIC_RADIO.get()) return false;
        refreshRadioInterference();
        return !radioInterference.isEmpty() && !isRadioInterferenceBypassed()
            && rssi <= CommonConfig.JAMMER_PACKET_LOSS_RSSI.get().floatValue();
    }

    private boolean isRadioInterferenceBypassed() {
        ServerPlayer operator = getRadioOperator();
        ServerPlayer owner = resolveCompanionOwner();
        return isPrivilegedRadioPlayer(operator) || isPrivilegedRadioPlayer(owner);
    }

    private static boolean isPrivilegedRadioPlayer(ServerPlayer player) {
        return player != null && (player.isCreative() || player.hasPermissions(2));
    }

    protected float radioRangeMultiplier() {
        return CommonConfig.RADIO_DEFAULT_VTX_RANGE_MULTIPLIER.get().floatValue();
    }

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
        if (!CommonConfig.REALISTIC_BATTERY.get()) return 1.0f;
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
    // Server-authoritative roll (synced), valid on both sides — used for ghost recording
    // where displayRoll (client-only) would read 0 on the server.
    public float getSyncedRoll() { return entityData.get(DATA_ROLL); }

    // -------------------------------------------------------------------------
    //  Companion / follow mode — FPV_IDEAS §3
    // -------------------------------------------------------------------------

    public boolean isFollowing() { return autoMode.isAutonomous(); }

    public DroneMode getAutoMode() { return autoMode; }

    /** Called home by a guard station: hold the anchor and stop hunting. */
    public void setRecalled(boolean value) {
        recalled = value;
        if (value) autoTarget = null;
    }

    public void setGuardAnchor(net.minecraft.core.BlockPos pos) { anchor = pos; }

    public float batteryCharge() { return battery.getPercentRemaining(); }

    public net.minecraft.world.item.ItemStack toItemStack() { return getDropStack(); }

    /** Firmware installed in this drone, or null. Only modular drones carry one. */
    public org.restor.fpv.component.FirmwareType firmware() { return null; }

    public boolean canPlayerAccess(Player player) {
        if (companionOwnerId == null || companionOwnerId.equals(player.getUUID())) return true;
        ServerPlayer owner = resolveCompanionOwner();
        return owner != null && (owner.isAlliedTo(player) || player.isAlliedTo(owner));
    }

    public void claimOwner(Player player) {
        if (!level().isClientSide && companionOwnerId == null) companionOwnerId = player.getUUID();
    }

    protected boolean isAuthorizedPlayerDamage(net.minecraft.world.damagesource.DamageSource source) {
        return !(source.getEntity() instanceof Player player) || canPlayerAccess(player);
    }

    /** Cycles through the modes the installed firmware supports; a bare FC flies nothing on its own. */
    public void cycleMode(Player player) {
        if (!isFlightReady()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.airframe_incomplete"), true);
            return;
        }
        org.restor.fpv.component.FirmwareType fw = firmware();
        if (fw == null) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.mode_needs_firmware"), true);
            return;
        }
        setAutoMode(fw.next(autoMode), player);
    }

    public void setAutoMode(DroneMode mode, Player player) {
        autoMode = mode;
        autoTarget = null;
        if (mode.isAutonomous()) {
            claimOwner(player);
            if (mode.isAnchored()) anchor = blockPosition();
            playSound(org.restor.fpv.sound.ModSounds.ARM.get(), 0.7f, 1.8f);
        }
        if (player != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "msg.fpv.mode_set", net.minecraft.network.chat.Component.translatable(mode.translationKey())), true);
        }
    }

    private ServerPlayer resolveCompanionOwner() {
        if (companionOwnerId == null || level().getServer() == null) return null;
        ServerPlayer p = level().getServer().getPlayerList().getPlayer(companionOwnerId);
        return p != null && !p.isRemoved() && p.level() == level() ? p : null;
    }

    // Simple fly-to-point hover at the owner's shoulder (never ACRO physics — same reasoning
    // as NPC drones: stable and cheap). Sets velocity directly; applyMovement() then moves us.
    private void autonomousTick(ServerPlayer owner) {
        flightController.armed = true;   // props spin via DATA_VISUAL_THR
        throttleInput = 0.55f;
        if (CommonConfig.REALISTIC_BATTERY.get()) battery.tick(throttleInput, 1.0f);
        if (CommonConfig.REALISTIC_BATTERY.get() && battery.isEmpty()) {
            idleFallback();
            return;
        }

        if (!recalled && (autoMode.engagesHostiles() || autoMode == DroneMode.PATROL)) refreshAutoTarget(owner);
        Vec3 station = autoTarget != null
            ? engagePoint(autoTarget)
            : autoMode.isAnchored() ? anchorStation(owner) : shoulderStation(owner);

        Vec3 to = station.subtract(position());
        double dist = to.length();
        if (dist > 24 && !autoMode.isAnchored()) {
            // Left too far behind (teleport, fast elytra, …) — pop over to the owner.
            setPos(station.x, station.y, station.z);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        double speed = autoTarget != null ? 1.4 : 0.9;
        Vec3 targetVel = dist > 0.05 ? to.scale(Math.min(speed, dist * 0.22) / dist) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().scale(0.62).add(targetVel.scale(0.38)));

        // Look at whatever we are working on, hold level attitude.
        Vec3 look = autoTarget != null ? autoTarget.position() : null;
        setYRot(look != null
            ? normalizeYaw((float) (Math.toDegrees(Math.atan2(position().x - look.x, look.z - position().z))))
            : normalizeYaw(owner.getYRot()));
        setXRot(0);
        entityData.set(DATA_ROLL, entityData.get(DATA_ROLL) * 0.8f);

        companionAttackTick(owner);
        if (autoTarget != null) autoEngageTick(owner, autoTarget);
        if (CommonConfig.REALISTIC_BATTERY.get() && battery.isEmpty()) idleFallback();
    }

    private Vec3 shoulderStation(ServerPlayer owner) {
        double yawRad = Math.toRadians(owner.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        if (owner.isShiftKeyDown()) return owner.getEyePosition().add(fwd.scale(1.1)).add(0, -0.2, 0);
        // Standing still, it closes in beside the owner's chest so it stays in view and
        // within reach; only while they are actually moving does it trail the shoulder.
        boolean moving = owner.getDeltaMovement().horizontalDistanceSqr() > 0.006
            || owner.isFallFlying() || owner.isPassenger();
        if (!moving) return owner.position().add(right.scale(0.85)).add(fwd.scale(0.55)).add(0, 1.35, 0);
        return owner.position().add(right.scale(1.4)).subtract(fwd.scale(1.0)).add(0, 1.9, 0);
    }

    private Vec3 anchorStation(ServerPlayer owner) {
        if (anchor == null) anchor = blockPosition();
        Vec3 center = Vec3.atCenterOf(anchor).add(0, 2.0, 0);
        if (autoMode != DroneMode.PATROL) return center;
        patrolPhase += 0.02f;
        return center.add(Math.cos(patrolPhase) * PATROL_RADIUS, Math.sin(patrolPhase * 0.7f) * 1.5,
            Math.sin(patrolPhase) * PATROL_RADIUS);
    }

    // Holds a standoff ring beside the target at head height rather than parking on top of
    // it, so the drone keeps a stable attack station instead of oscillating over its head.
    private Vec3 engagePoint(LivingEntity target) {
        Vec3 lead = target.position().add(clampLength(target.getDeltaMovement().scale(6), 1.5));
        Vec3 away = position().subtract(lead);
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        return lead.add(away.normalize().scale(ENGAGE_STANDOFF)).add(0, target.getBbHeight() * 0.85, 0);
    }

    private static Vec3 clampLength(Vec3 v, double max) {
        double len = v.length();
        return len <= max ? v : v.scale(max / len);
    }

    private void refreshAutoTarget(ServerPlayer owner) {
        Vec3 post = autoMode.isAnchored() && anchor != null ? Vec3.atCenterOf(anchor) : owner.position();
        if (autoTarget != null && (autoTarget.isRemoved() || !autoTarget.isAlive()
            || autoTarget.distanceToSqr(post) > GUARD_RADIUS * GUARD_RADIUS * 2.25)) {
            autoTarget = null;
        }
        if (--targetScanCooldown > 0) return;
        targetScanCooldown = 10;
        org.restor.fpv.component.FirmwareType fw = firmware();
        if (fw == null || !(fw.canFight() || fw.marksTargets())) return;

        Vec3 center = autoMode.isAnchored() && anchor != null ? Vec3.atCenterOf(anchor) : owner.position();
        LivingEntity best = null;
        double bestSqr = Double.MAX_VALUE;
        for (net.minecraft.world.entity.monster.Monster m : level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                net.minecraft.world.phys.AABB.ofSize(center, GUARD_RADIUS * 2, GUARD_RADIUS, GUARD_RADIUS * 2))) {
            if (!m.isAlive() || isCompanionAlly(owner, m)) continue;
            if (m.distanceToSqr(center) > GUARD_RADIUS * GUARD_RADIUS) continue;
            if (fw.marksTargets()) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, MARK_DURATION, 0, false, false));
            // Ranked from the drone, not from the guarded point: the thing about to bite is
            // what gets hit, even when something further out entered the ring first.
            double d = m.distanceToSqr(this);
            if (d < bestSqr) {
                bestSqr = d;
                best = m;
            }
        }
        autoTarget = fw.canFight() ? best : null;
    }

    /** Close-quarters fallback for a guardian with no weapon module: bump the target. */
    private void autoEngageTick(ServerPlayer owner, LivingEntity target) {
        if (ramCooldown > 0) {
            ramCooldown--;
            return;
        }
        double reach = ENGAGE_STANDOFF + target.getBbWidth() + getBbWidth();
        if (distanceToSqr(target) > reach * reach || !consumeCompanionWeaponEnergy(1.5f)) return;
        target.hurt(damageSources().mobAttack(owner), RAM_DAMAGE);
        target.knockback(0.4, position().x - target.getX(), position().z - target.getZ());
        ramCooldown = 20;
        playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
    }

    private static final double GUARD_RADIUS = 20.0;
    private static final double PATROL_RADIUS = 6.0;
    private static final double ENGAGE_STANDOFF = 1.8;
    private static final int    MARK_DURATION = 45;
    private static final float  RAM_DAMAGE = 3.0f;
    private int ramCooldown;

    /** Module-driven combat hook: modular drones override (e.g. dart gun fires at monsters). */
    protected void companionAttackTick(ServerPlayer owner) {}

    /** The hostile the autonomy layer is currently working on, or null. */
    public LivingEntity autoTarget() { return autoTarget; }

    protected boolean isCompanionAlly(ServerPlayer owner, LivingEntity target) {
        return target == owner || owner.isAlliedTo(target) || target.isAlliedTo(owner);
    }

    protected boolean canHitCompanionTarget(Entity entity) {
        if (companionOwnerId == null || !(entity instanceof LivingEntity target)) return true;
        ServerPlayer owner = resolveCompanionOwner();
        return owner == null || !isCompanionAlly(owner, target);
    }

    protected boolean consumeCompanionWeaponEnergy(float powerFactor) {
        if (!CommonConfig.REALISTIC_BATTERY.get()) return true;
        if (battery.isEmpty()) return false;
        battery.tick(1.0f, powerFactor);
        return true;
    }

    /** Storage-module hook: modular drones override to open the cargo box. False = no storage. */
    protected boolean openStorage(Player player) { return false; }

    /** Firmware-driven right-click while autonomous. True = handled, skip the cargo box. */
    protected boolean autonomousInteract(Player player) { return false; }

    // -------------------------------------------------------------------------
    //  Payload (drop charge) — FPV_IDEAS §4
    // -------------------------------------------------------------------------

    public int getPayloadAmmo() { return entityData.get(DATA_PAYLOAD_AMMO); }
    public int getRocketAmmo()  { return entityData.get(DATA_ROCKET_AMMO); }
    public int getAckSeq()      { return entityData.get(DATA_ACK_SEQ); }
    public float getServerMspt(){ return entityData.get(DATA_SRV_MSPT) / 10f; }

    // How many charges this drone can carry. Preset drones have a built-in rack; the
    // modular drone overrides this — 0 without a Payload Bay module installed.
    protected int payloadCapacity() { return liftLimitedPayload(); }

    protected boolean hasPayloadBay() { return true; }

    // A rack is only as big as the lift behind it: charges are 100 g each, so a light airframe
    // that took the full four never got off the ground. Cap the load where the build still has
    // enough thrust in reserve to climb out.
    /** Spare lift in kg at the payload TWR margin — what a warhead may weigh. */
    public float warheadCapacityKg() {
        return Math.max(0f, effMotorPower() / (GRAVITY * MIN_PAYLOAD_TWR) - droneData.mass);
    }

    protected final int liftLimitedPayload() {
        float spare = effMotorPower() / (GRAVITY * MIN_PAYLOAD_TWR) - droneData.mass;
        return Math.max(0, Math.min(MAX_PAYLOAD, (int) (spare / CHARGE_MASS)));
    }

    // Loads as many charges as fit from the held stack (server-side).
    private void loadPayload(Player player, net.minecraft.world.item.ItemStack held) {
        if (payloadCapacity() <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                hasPayloadBay() ? "msg.fpv.payload_too_heavy" : "msg.fpv.payload_no_bay"), true);
            return;
        }
        int space = payloadCapacity() - getPayloadAmmo();
        if (space <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.payload_full"), true);
            return;
        }
        int take = Math.min(space, held.getCount());
        entityData.set(DATA_PAYLOAD_AMMO, getPayloadAmmo() + take);
        if (!player.getAbilities().instabuild) held.shrink(take);
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("msg.fpv.payload_loaded", getPayloadAmmo(), payloadCapacity()), true);
        playSound(org.restor.fpv.sound.ModSounds.PAYLOAD_RELEASE.get(), 0.6f, 1.4f);
    }

    // -------------------------------------------------------------------------
    //  Pilot-fired weapons — rocket pod and dart gun
    // -------------------------------------------------------------------------

    /** How many mini rockets the rails hold; 0 without a rocket-pod module. */
    public int rocketCapacity() { return 0; }

    /** True when a dart gun module is installed and the pilot may fire it by hand. */
    public boolean hasDartGun() { return false; }

    private void loadRockets(Player player, net.minecraft.world.item.ItemStack held) {
        if (rocketCapacity() <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.rocket_no_pod"), true);
            return;
        }
        int space = rocketCapacity() - getRocketAmmo();
        if (space <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.rocket_full"), true);
            return;
        }
        int take = Math.min(space, held.getCount());
        entityData.set(DATA_ROCKET_AMMO, getRocketAmmo() + take);
        if (!player.getAbilities().instabuild) held.shrink(take);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg.fpv.rocket_loaded", getRocketAmmo(), rocketCapacity()), true);
        playSound(org.restor.fpv.sound.ModSounds.PAYLOAD_RELEASE.get(), 0.6f, 1.2f);
    }

    // Sneak + empty hand strips the rails and the bay back into the pilot's hands; once
    // nothing is loaded the same click falls through to the mode cycle.
    private boolean unloadOrdnance(Player player) {
        int rockets = getRocketAmmo();
        int charges = getPayloadAmmo();
        if (rockets <= 0 && charges <= 0) return false;
        if (rockets > 0) {
            entityData.set(DATA_ROCKET_AMMO, 0);
            giveBack(player, new net.minecraft.world.item.ItemStack(
                org.restor.fpv.item.ModItems.MINI_ROCKET.get(), rockets));
        }
        if (charges > 0) {
            entityData.set(DATA_PAYLOAD_AMMO, 0);
            giveBack(player, new net.minecraft.world.item.ItemStack(
                org.restor.fpv.item.ModItems.DROP_CHARGE.get(), charges));
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg.fpv.ordnance_unloaded", rockets, charges), true);
        playSound(org.restor.fpv.sound.ModSounds.PAYLOAD_RELEASE.get(), 0.6f, 0.9f);
        return true;
    }

    private void giveBack(Player player, net.minecraft.world.item.ItemStack stack) {
        if (!player.addItem(stack)) player.drop(stack, false);
    }

    /** Weapon key: launches a rocket off the rails, or fires the dart gun straight ahead. */
    public void fireWeapon(ServerPlayer pilot) {
        if (level().isClientSide || !canPlayerAccess(pilot)) return;
        if (weaponCooldown > 0) return;
        if (rocketCapacity() > 0) {
            if (getRocketAmmo() <= 0) {
                pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.rocket_empty"), true);
                return;
            }
            entityData.set(DATA_ROCKET_AMMO, getRocketAmmo() - 1);
            weaponCooldown = WEAPON_COOLDOWN;
            launchProjectile(pilot, new org.restor.fpv.entity.MiniRocketEntity(level(), getX(), getY(), getZ()), 2.6f, 0.6f);
            playSound(org.restor.fpv.sound.ModSounds.WARHEAD_ARM.get(), 1.0f, 1.5f);
            return;
        }
        if (hasDartGun()) {
            if (!consumeCompanionWeaponEnergy(DART_SHOT_POWER)) return;
            weaponCooldown = WEAPON_COOLDOWN;
            net.minecraft.world.entity.projectile.Arrow dart =
                new net.minecraft.world.entity.projectile.Arrow(level(), getX(), getY(), getZ());
            dart.setBaseDamage(2.0);
            dart.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            launchProjectile(pilot, dart, 2.6f, 0.6f);
            playSound(org.restor.fpv.sound.ModSounds.DART.get(), 0.7f, 1.7f);
            return;
        }
        pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.no_weapon"), true);
    }

    // Spawns a projectile just ahead of the airframe, aimed down the camera axis and carrying
    // the drone's own velocity so a fast pass does not outrun its own shot.
    private void launchProjectile(ServerPlayer pilot,
                                  net.minecraft.world.entity.projectile.Projectile projectile,
                                  float speed, float inaccuracy) {
        Quaternionf aim = attitudeQuat(getYRot(), getXRot(), bodyRoll())
            .rotateX((float) Math.toRadians(-getCameraAngle()));
        Vector3f fwd = aim.transform(new Vector3f(0f, 0f, 1f));
        Vec3 dir = new Vec3(fwd.x, fwd.y, fwd.z).normalize();
        Vec3 muzzle = position().add(dir.scale(0.6)).add(0, getBbHeight() * 0.3, 0);
        projectile.setPos(muzzle.x, muzzle.y, muzzle.z);
        projectile.setOwner(pilot);
        projectile.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(getDeltaMovement()));
        level().addFreshEntity(projectile);
    }

    // Drops one charge (a primed TNT) from the belly, inheriting the drone's velocity so it
    // lands where the drone is heading. Costs mass while carried and has a short fire cooldown.
    public void firePayload(ServerPlayer pilot) {
        if (level().isClientSide) return;
        if (!canPlayerAccess(pilot)) return;
        if (getPayloadAmmo() <= 0) {
            pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.fpv.payload_empty"), true);
            return;
        }
        if (payloadCooldown > 0) return;
        payloadCooldown = FIRE_COOLDOWN;
        entityData.set(DATA_PAYLOAD_AMMO, getPayloadAmmo() - 1);

        double bx = getX(), by = getY() - getBbHeight() * 0.5 - 0.1, bz = getZ();
        DropChargeEntity charge = new DropChargeEntity(level(), bx, by, bz);
        charge.setOwner(pilot);
        Vec3 vel = getDeltaMovement();
        charge.setDeltaMovement(vel.x, Math.min(vel.y, 0) - 0.1, vel.z);
        level().addFreshEntity(charge);
        level().playSound(null, bx, by, bz, org.restor.fpv.sound.ModSounds.WARHEAD_ARM.get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }
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
    public java.util.UUID getRadioOwnerId() { return companionOwnerId; }
    public ServerPlayer getRadioOperator() { return getRacePilot(); }

    @Override public java.util.UUID radioOwnerId() { return getRadioOwnerId(); }
    @Override public ServerPlayer radioOperator() { return getRadioOperator(); }
    public boolean isArmed()         { return entityData.get(DATA_ARMED); }
    public RadioFailsafe.State getRadioState() {
        int ordinal = Mth.clamp(entityData.get(DATA_RADIO_STATE), 0, RadioFailsafe.State.values().length - 1);
        return RadioFailsafe.State.values()[ordinal];
    }
    public float getCameraAngle()    { return entityData.get(DATA_CAMERA_ANGLE); }
    public void readFromItemTag(CompoundTag tag) {
        if (tag.contains("Battery")) battery.load(tag.getCompound("Battery"));
        if (tag.hasUUID("CompanionOwner")) companionOwnerId = tag.getUUID("CompanionOwner");
    }

    public void adjustCameraAngle(float delta) {
        float max = isTailsitter() ? 120f : 60f;
        entityData.set(DATA_CAMERA_ANGLE, clamp(entityData.get(DATA_CAMERA_ANGLE) + delta, -75f, max));
    }

    /** Six motors and up: flown like a camera drone — sticks move it, the mouse aims the cam. */
    public boolean isHeavyLift() { return false; }

    /** Tailsitter airframes stand on their tail: thrust leaves through the nose. */
    public boolean isTailsitter() { return false; }

    /** A slender fuselage resists sideways travel far more than travel along its own axis. */
    protected float planarDragMult() { return isTailsitter() ? 2.4f : 1f; }

    /** Heavy-lift rigs: the throttle stick commands climb rate, not motor power. */
    public boolean altitudeHold() { return false; }

    protected float resolveThrottle(float stick) {
        return altitudeHold() ? altitudeHoldThrottle(stick) : clamp(stick, 0f, 1f);
    }

    // Turns a vertical-speed stick (0..1, 0.5 = hold) into the motor throttle that flies it,
    // undoing the throttle^EXP curve applyFlightPhysics puts back on and compensating the tilt
    // that steals vertical lift while the rig translates.
    private float altitudeHoldThrottle(float stick) {
        float cmd = clamp(stick, 0f, 1f) * 2f - 1f;
        if (Math.abs(cmd) < 0.06f) cmd = 0f;
        if (onGround() && cmd <= 0f) return 0f;
        float vy = (float) getDeltaMovement().y * 20f;
        float accel = clamp((cmd * MAX_CLIMB_RATE - vy) / CLIMB_TAU, -GRAVITY, GRAVITY);
        Quaternionf rot = attitudeQuat(getYRot(), getXRot(), bodyRoll());
        Vector3f up = new Vector3f(0, 1, 0);
        rot.transform(up);
        float needed = (GRAVITY + accel) / Math.max(0.25f, up.y);
        float ceiling = droneData.maxThrottle * getBatteryPowerFactor() * effMotorPower() / mass();
        if (ceiling <= 1.0e-4f) return 0f;
        return (float) Math.pow(clamp(needed / ceiling, 0f, 1f), 1f / THROTTLE_EXP);
    }

    /**
     * Minutes of hover a full pack is worth on this airframe. A whoop burns its pack in about
     * five minutes, a 7" cruiser holds fifteen, everything between scales with size; heavy-lift
     * rigs carry so much cell that they loiter for over an hour.
     */
    protected float enduranceMinutes() {
        return switch (droneData.type) {
            case TINY_WHOOP -> 5f;
            case CINEWHOOP -> 8f;
            case RACING -> 11f;
            case CUSTOM -> 10f;
        };
    }

    /** Stick that holds altitude: thrust = mass·g solved through the throttle² curve. */
    protected float hoverStick() {
        float ceiling = droneData.maxThrottle * droneData.motorPower / Math.max(0.01f, droneData.mass);
        if (ceiling <= GRAVITY) return 0.95f;
        return (float) Math.pow(GRAVITY / ceiling, 1f / THROTTLE_EXP);
    }

    protected void applyEnduranceProfile() {
        battery.setEnduranceProfile(enduranceMinutes(), hoverStick());
    }

    // Belly-camera gimbal of the heavy rigs, in the camera's own frame: yaw pans, pitch is an
    // uptilt. Client-only — it steers the view, never the physics.
    public static final float GIMBAL_YAW_LIMIT = 180f;  // pans the full circle, yaw wraps
    public static final float GIMBAL_PITCH_MIN = -90f;
    public static final float GIMBAL_PITCH_MAX = 0f;

    private float gimbalYaw, gimbalPitch;

    public float getGimbalYaw()   { return gimbalYaw; }
    public float getGimbalPitch() { return gimbalPitch; }

    public void setGimbal(float yaw, float pitch) {
        gimbalYaw = Mth.wrapDegrees(yaw);
        gimbalPitch = clamp(pitch, GIMBAL_PITCH_MIN, GIMBAL_PITCH_MAX);
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
        setControllerInput(throttle, pitch, roll, yaw, arm, modeSwitch, lastControlSeq);
    }

    public void setControllerInput(float throttle, float pitch, float roll, float yaw,
                                   boolean arm, boolean modeSwitch, int seq) {
        lastControlSeq = seq;
        if (!Float.isFinite(throttle) || !Float.isFinite(pitch)
            || !Float.isFinite(roll) || !Float.isFinite(yaw)) return;
        if (!level().isClientSide && isRadioReceptionBlocked()) return;
        useController   = true;
        ctrlThrottle    = clamp(throttle, 0, 1);
        ctrlPitch       = clamp(pitch, -1, 1);
        ctrlRoll        = clamp(roll, -1, 1);
        ctrlYaw         = clamp(yaw, -1, 1);
        ctrlModeSwitch  = modeSwitch;
        radioFailsafe.acceptPacket(ctrlThrottle, arm, radioPolicy());
    }

    public void clearControllerInput() {
        useController = false;
        ctrlThrottle = ctrlPitch = ctrlRoll = ctrlYaw = 0;
        radioFailsafe.forceDisarm();
    }
    public boolean hasControllerInput() { return useController; }
    public int getServerPacketAge() { return radioFailsafe.packetAge(); }

    // -------------------------------------------------------------------------
    //  Remote control (station + link code)
    // -------------------------------------------------------------------------

    /** True for drone types flown from the control station (no riding). */
    public boolean isRemoteControllable() { return false; }

    /** Any pilot: a riding passenger or an active remote session. */
    public boolean hasAnyPilot() {
        return remotePilotId != null || getControllingPassenger() != null;
    }

    @Override
    public boolean isActiveDummy(java.util.UUID dummy) {
        return dummyId != null && dummyId.equals(dummy);
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

    public void beginRemoteControl(ServerPlayer player) { beginRemoteControl(player, true); }

    public void beginRemoteControl(ServerPlayer player, boolean fpv) {
        if (level().isClientSide || !canPlayerAccess(player)) return;
        clearControllerInput();
        claimOwner(player);
        remotePilotId = player.getUUID();
        remoteFpv = fpv;
        stationReturnPos = safeReturnPos(player);
        if (!fpv) {
            // Line of sight: the pilot keeps their own body and camera and just watches
            // the drone fly, so none of the projection machinery applies.
            org.restor.fpv.compat.PacketDistributor.sendToPlayer(player,
                new org.restor.fpv.network.LineOfSightPacket(getId()));
            return;
        }
        // The pilot's body follows the drone (that's what streams chunks to the
        // client) — keep it invisible and invulnerable for the duration.
        PilotSessions.open(player, this);
        pilotWasInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        player.setInvisible(true);
        PilotSessions.syncFlagsToSelf(player);
        spawnDummy(player, stationReturnPos);
        // Vanilla camera-follow: teleports the pilot along with the drone and resets
        // the camera on sneak (wantsToStopRiding).
        player.setCamera(this);
    }

    public boolean isLineOfSightPilot() { return remotePilotId != null && !remoteFpv; }

    // Validates the session each tick; ends it (and teleports the pilot back to the
    // station) when the pilot left, disconnected, or pressed sneak (vanilla resets
    // the camera to the player itself).
    private ServerPlayer resolveRemotePilot() {
        if (remotePilotId == null) return null;
        ServerPlayer p = level().getServer() != null
            ? level().getServer().getPlayerList().getPlayer(remotePilotId) : null;
        if (p == null || p.isRemoved()) {
            endRemoteControl();
            return null;
        }
        if (!remoteFpv) {
            double range = org.restor.fpv.item.RcLink.range(org.restor.fpv.item.RcLink.heldTransmitter(p));
            if (!org.restor.fpv.item.RcLink.hasTransmitter(p) || p.level() != level() || distanceTo(p) > range) {
                endRemoteControl();
                return null;
            }
            return p;
        }
        if (p.getCamera() != this) {
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
        PilotSessions.forget(remotePilotId);
        remotePilotId = null;
        clearForcedChunks();
        clearChunkStreaming();
        discardDummy();
        if (p != null) {
            if (remoteFpv) {
                PilotSessions.close(p);
                if (stationReturnPos != null && p.level() == level()) {
                    p.teleportTo(stationReturnPos.x, stationReturnPos.y, stationReturnPos.z);
                }
                if (p.getCamera() == this) p.setCamera(p);
                p.setInvulnerable(pilotWasInvulnerable);
                p.setInvisible(false);
                PilotSessions.syncFlagsToSelf(p);
            } else {
                org.restor.fpv.compat.PacketDistributor.sendToPlayer(p,
                    new org.restor.fpv.network.LineOfSightPacket(-1));
            }
        }
        remoteFpv = true;
        stationReturnPos = null;
    }

    // Visible pilot stand-in (rendered with the pilot's skin) at the given spot.
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

    // Pushes chunk packets around the drone straight to the pilot's client:
    // re-centers the client chunk cache on the drone and sends every loaded chunk
    // in radius that the client doesn't have yet. Unloaded chunks get an async
    // load/generation ticket and are sent as soon as they're ready.
    private void streamChunksToPilot(ServerPlayer pilot) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int r = Mth.clamp(org.restor.fpv.Config.DEV_CHUNK_RADIUS.get(), 0, 12);
        if (r == 0) {
            clearChunkStreaming();
            return;
        }
        r = Math.max(r, 2);
        net.minecraft.world.level.ChunkPos center = chunkPosition();

        boolean centerMoved = center.toLong() != lastStreamCenter;
        if (centerMoved) {
            lastStreamCenter = center.toLong();
            pilot.connection.send(new net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket(center.x, center.z));
        } else if (streamTickets.isEmpty()) {
            // Nothing moved and nothing is still generating: every chunk in radius is already
            // on the client, so there is nothing for a rescan to find.
            lastStreamSentThisTick = 0;
            return;
        }

        // The budget is per server tick, but a slow tick is a whole second of flight: at
        // 4 chunks a tick the client runs out of world, its entity section goes untracked
        // and the picture freezes. Sending an already-loaded chunk is only serialisation,
        // so open the budget in proportion to how long the tick actually takes.
        int budget = MAX_STREAM_SENDS_PER_TICK;
        float mspt = org.restor.fpv.ServerTickMonitor.msptTenths() / 10f;
        if (mspt > 50f) {
            budget = Mth.clamp(Math.round(MAX_STREAM_SENDS_PER_TICK * mspt / 50f),
                MAX_STREAM_SENDS_PER_TICK, MAX_STREAM_SENDS_SLOW_TICK);
        }

        int sentThisTick = 0;
        int candidates = 0;
        // Nearest-first, with hard budgets: an unbounded ring would force-load and tick a
        // few hundred chunks at once, which stalls the server thread on generation.
        ring:
        for (int d = 0; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (d > 0 && Math.abs(dx) != d && Math.abs(dz) != d) continue;
                    if (sentThisTick >= budget) break ring;
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
                    } else if (streamTickets.size() < MAX_STREAM_TICKETS && streamTickets.add(key)) {
                        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(cx, cz);
                        sl.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
                    }
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
        int r = Math.min(1, org.restor.fpv.Config.DEV_CHUNK_RADIUS.get());
        if (r < 0) { clearForcedChunks(); return; }
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
        if (want.equals(forcedChunks)) return;
        for (long c : forcedChunks) {
            if (!want.contains(c)) setEntityTicking(sl, c, false);
        }
        for (long c : want) {
            if (!forcedChunks.contains(c)) setEntityTicking(sl, c, true);
        }
        forcedChunks.clear();
        forcedChunks.addAll(want);
    }

    // Entity-ticking ticket (level 31) around the drone. Deliberately not
    // ServerLevel#setChunkForced: that persists into ForcedChunksSavedData and marks it
    // dirty every time the bubble moves, which a flying drone does constantly.
    private static void setEntityTicking(net.minecraft.server.level.ServerLevel sl, long key, boolean add) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(
            net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key));
        if (add) sl.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
        else sl.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
    }

    private void clearForcedChunks() {
        lastForcedCenter = Long.MIN_VALUE;
        if (forcedChunks.isEmpty()) return;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (long c : forcedChunks) setEntityTicking(sl, c, false);
        }
        forcedChunks.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            if (!getPassengers().isEmpty()) ejectPassengers();
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
    // Server motion packets echo our own velocity back a full slow tick later; applying
    // them over the local simulation was the visible stutter.
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (level().isClientSide && predAge <= 5 && clientPhysics()) return;
        super.lerpMotion(x, y, z);
    }

    // Vanilla's ServerboundMoveVehiclePacket round trip answers with an absMoveTo snap
    // whenever the server disagrees. We send our own pose packet instead.
    @Override
    public boolean isControlledByLocalInstance() {
        return !clientPhysics() && super.isControlledByLocalInstance();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (level().isClientSide && (predAge <= 5 || entityData.get(DATA_POSE_LOCK))) {
            serverSyncPos = new Vec3(x, y, z);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    // Called client-side by ClientEventHandler with the same values it sends to the
    // server, enabling zero-latency rotation prediction in clientTick().
    public void setClientPredictedInputs(float throttle, float pitch, float roll, float yaw, boolean arm) {
        predThrottle = throttle;
        predPitch    = pitch;
        predRoll     = roll;
        predYaw      = yaw;
        predAge      = 0;

        // Disarm is always granted, arming only from idle throttle — same gate the server
        // applies, so the prediction matches unless the radio or battery refuses it.
        if (entityData.get(DATA_ARMED) == arm) {
            predArmTicks = 0;
        } else if (!arm || throttle <= 0.05f) {
            predArmed = arm;
            predArmTicks = 60;
        }
    }

    private boolean predictedArmed() {
        if (predArmTicks > 0) {
            predArmTicks--;
            return predArmed;
        }
        return entityData.get(DATA_ARMED);
    }
}
