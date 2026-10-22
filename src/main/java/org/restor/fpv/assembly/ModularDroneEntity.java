package org.restor.fpv.assembly;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.data.Battery;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.flight.FlightController;
import org.restor.fpv.item.ModItems;

/**
 * The player-built drone: carries a DroneAssembly (frame, motors, props, camera,
 * battery), derives all flight characteristics from it, and applies constant
 * pitch/roll/yaw trim when the props are mismatched. The assembly is synced to the
 * client (renderer composes the part models at the frame's mount points).
 */
public class ModularDroneEntity extends DroneEntity {
    private static final EntityDataAccessor<CompoundTag> DATA_ASSEMBLY =
        SynchedEntityData.defineId(ModularDroneEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private DroneAssembly assembly = new DroneAssembly();
    private boolean flightReady;
    private float trimRoll, trimPitch, trimYaw; // °/s at full throttle
    private boolean clientAssemblyInitialized;
    // Scraping a wall damages parts several ticks in a row; re-serialising and
    // re-syncing the whole assembly NBT on each of those was pure overhead.
    private int assemblyResyncDelay = -1;
    private int stressWearTimer;
    // Warhead module state (mirrors KamikazeDroneEntity's impact logic).
    private final org.restor.fpv.warhead.ContactFuze fuze = new org.restor.fpv.warhead.ContactFuze();
    private final org.restor.fpv.warhead.ProximityFuze proximityFuze = new org.restor.fpv.warhead.ProximityFuze();
    private org.restor.fpv.warhead.WarheadType warhead = org.restor.fpv.warhead.WarheadType.FRAG;
    private boolean exploded;
    // Dart gun (companion mode) fire cooldown.
    private int dartCooldown;
    // Trader firmware: rolled lazily, saved with the drone.
    private final DroneTrader trader = new DroneTrader(this);
    private int traderDepartureTimer = -1;
    private static final float DART_SHOT_POWER_FACTOR = 8.0f;
    // Cargo box contents (storage module). Kept even when the module is swapped out —
    // the items drop when the drone breaks.
    private final net.minecraft.world.SimpleContainer cargo = new net.minecraft.world.SimpleContainer(9);

    public ModularDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.CINEWHOOP);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ASSEMBLY, new CompoundTag());
    }

    public DroneAssembly getAssembly() {
        return assembly;
    }

    @Override
    public boolean isFlightReady() {
        return flightReady;
    }

    @Override
    protected float radioRangeMultiplier() {
        return super.radioRangeMultiplier() * assembly.vtxRangeMultiplier();
    }

    @Override
    public int radioChannel() {
        return assembly.channel;
    }

    public void setAssembly(DroneAssembly a) {
        this.assembly = a;
        entityData.set(DATA_ASSEMBLY, a.save());
        applyAssembly();
        refreshDimensions();
    }

    // The hitbox follows the frame, so a whoop build isn't boxed like a 5".
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        FrameSpec spec = assembly == null ? null : assembly.frameSpec();
        if (spec == null) return super.getDimensions(pose);
        DroneVisual v = DroneVisual.of(spec);
        float s = (float) org.restor.fpv.Config.DEV_HITBOX_SCALE.get().doubleValue();
        float w = v.hitboxWidth() * s;
        float h = v.hitboxHeight() * s;
        if (spec.isTailsitter()) {
            w = v.hitboxWidth() * 0.72f * s;
            h = v.hitboxWidth() * 1.45f * s;
        }
        return withCameraEye(net.minecraft.world.entity.EntityDimensions.scalable(w, h));
    }

    // Derive flight characteristics from the installed components.
    private void applyAssembly() {
        flightReady = assembly != null && assembly.isComplete();
        updateWearEffects();
        this.flightController = controllerFor();
        this.battery = new Battery(droneData.batteryCapacity, 4.2f);
        applyEnduranceProfile();
        adjustCameraAngle(droneData.cameraAngle - getCameraAngle());
    }

    @Override
    public boolean isHeavyLift() {
        FrameSpec spec = assembly == null ? null : assembly.frameSpec();
        return spec != null && spec.isHeavyLift();
    }

    @Override
    public boolean isTailsitter() {
        FrameSpec spec = assembly == null ? null : assembly.frameSpec();
        return spec != null && spec.isTailsitter();
    }

    @Override
    protected float enduranceMinutes() {
        FrameSpec spec = assembly == null ? null : assembly.frameSpec();
        if (spec == null) return 10f;
        if (spec.isFixedWing()) return 120f;
        if (spec.isHeavyLift()) return spec.motorCount() >= 8 ? 110f : 75f;
        float size = assembly.frameSizeInches();
        return Math.max(4f, Math.min(20f, 5f + (size - 1.5f) * (10f / 5.5f)));
    }

    private FlightController controllerFor() {
        FlightController fc = new FlightController(droneData);
        fc.setMode(defaultFlightMode());
        return fc;
    }

    private void updateWearEffects() {
        this.droneData = assembly.buildDroneData();
        float[] trim = assembly.computeTrim();
        trimRoll = trim[0];
        trimPitch = trim[1];
        trimYaw = trim[2];
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // Client receives the assembly → rebuild derived stats + renderer data.
        if (DATA_ASSEMBLY.equals(key) && level().isClientSide) {
            assembly = DroneAssembly.load(entityData.get(DATA_ASSEMBLY));
            updateWearEffects();
            refreshDimensions();
            if (!clientAssemblyInitialized) {
                flightController = controllerFor();
                battery = new Battery(droneData.batteryCapacity, 4.2f);
                applyEnduranceProfile();
                clientAssemblyInitialized = true;
            }
        }
    }

    // Trim scales with throttle: no thrust = no imbalance moment.
    @Override protected float trimRollRate()  { return trimRoll * throttleInput; }
    @Override protected float trimPitchRate() { return trimPitch * throttleInput; }
    @Override protected float trimYawRate()   { return trimYaw * throttleInput; }

    public float getCameraGlitchSeverity() {
        return 1f - ComponentCondition.ratio(assembly.camera);
    }

    @Override
    protected void onMovementImpact(Vec3 velocityDelta) {
        if (level().isClientSide) return;
        fuze.reportImpact(velocityDelta.length());
        FrameSpec spec = assembly.frameSpec();
        if (spec == null || spec.motorCount() == 0) return;

        double dx = velocityDelta.x * 20.0;
        double dy = velocityDelta.y * 20.0;
        double dz = velocityDelta.z * 20.0;
        double yaw = Math.toRadians(getYRot());
        double localX = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double localZ = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        float[] mountX = new float[spec.motorCount()];
        float[] mountZ = new float[spec.motorCount()];
        for (int i = 0; i < spec.motorCount(); i++) {
            mountX[i] = spec.motorMounts().get(i).x();
            mountZ[i] = spec.motorMounts().get(i).z();
        }

        DroneImpactDamage.Allocation damage = DroneImpactDamage.allocate(
            assembly.totalMassKg(), localX, dy, localZ, mountX, mountZ);
        if (!damage.hasDamage()) return;
        int i = damage.motorIndex();
        float wear = 1f / Math.max(0.2f, assembly.tuning().toughness());
        ComponentCondition.damage(assembly.props[i], scaled(damage.propDamage(), wear));
        ComponentCondition.damage(assembly.motors[i], scaled(damage.motorDamage(), wear));
        ComponentCondition.damage(assembly.frame, scaled(damage.frameDamage(), wear));
        updateWearEffects();
        assemblyResyncDelay = 10;
    }

    private static int scaled(int amount, float factor) {
        return amount <= 0 ? amount : Math.max(1, Math.round(amount * factor));
    }

    /** Thrust-limited like every drone, but never above what the airframe is rated to carry. */
    @Override
    public float warheadCapacityKg() {
        return Math.min(super.warheadCapacityKg(), assembly.tuning().liftCapacityKg());
    }

    // Parts running outside their spec (wrong prop for the motor, over-volted pack) survive,
    // but burn themselves out over a flight instead of being rejected at the bench.
    private void tickStressWear() {
        float stress = assembly.stressFactor();
        if (stress <= 1.01f || !flightController.isArmed() || throttleInput <= 0.05f) return;
        if (++stressWearTimer < 40) return;
        stressWearTimer = 0;
        int amount = Math.max(1, Math.round((stress - 1f) * 8f * throttleInput));
        FrameSpec spec = assembly.frameSpec();
        int n = spec != null ? spec.motorCount() : 0;
        for (int i = 0; i < n; i++) {
            if (assembly.slotEfficiency(i) >= 1f && stress < 1.3f) continue;
            ComponentCondition.damage(assembly.motors[i], amount);
            ComponentCondition.damage(assembly.props[i], amount);
        }
        updateWearEffects();
        assemblyResyncDelay = 20;
    }

    // ------------------------------------------------------------------
    //  Module effects (FPV_IDEAS §4)
    // ------------------------------------------------------------------

    private boolean hasWarhead() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.WARHEAD;
    }

    public org.restor.fpv.warhead.WarheadType getWarhead() { return warhead; }

    public void setWarhead(org.restor.fpv.warhead.WarheadType type) {
        if (type != null) warhead = type;
    }

    // The warhead module is the mount; which head sits in it is chosen here, limited by
    // whatever lift the build has left over.
    @Override
    public net.minecraft.world.InteractionResult interact(Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && org.restor.fpv.warhead.WarheadLoading.isWarhead(held)) {
            if (!canPlayerAccess(player)) return net.minecraft.world.InteractionResult.FAIL;
            if (!level().isClientSide) {
                if (!hasWarhead()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.fpv.warhead_no_module"), true);
                } else {
                    org.restor.fpv.warhead.WarheadLoading.swap(player, held, warhead,
                        warheadCapacityKg(), this::setWarhead);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    // Drop charges only fit when a Payload Bay module is installed.
    @Override
    protected int payloadCapacity() {
        return hasPayloadBay() ? liftLimitedPayload() : 0;
    }

    @Override
    protected boolean hasPayloadBay() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.PAYLOAD_BAY;
    }

    @Override
    public int rocketCapacity() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.ROCKET_POD ? MAX_ROCKETS : 0;
    }

    @Override
    public boolean hasDartGun() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.DART_GUN;
    }

    @Override
    public org.restor.fpv.component.FirmwareType firmware() {
        return assembly.firmwareType();
    }

    @Override
    protected boolean autonomousInteract(net.minecraft.world.entity.player.Player player) {
        org.restor.fpv.component.FirmwareType fw = firmware();
        if (fw == null || !fw.trades() || trader.getTradingPlayer() != null) return false;
        trader.setTradingPlayer(player);
        trader.openTradingScreen(player, getDisplayName(), 1);
        return true;
    }

    // Companion combat: a dart gun module engages hostile mobs near the owner. Arrows are
    // owned by the player so damage/aggro credit goes to them, not to a non-living drone.
    @Override
    protected void companionAttackTick(net.minecraft.server.level.ServerPlayer owner) {
        if (assembly.moduleType() != org.restor.fpv.component.ModuleType.DART_GUN) return;
        if (dartCooldown > 0) { dartCooldown--; return; }

        net.minecraft.world.entity.LivingEntity target = autoTarget();
        if (target != null && !hasLineOfSightTo(target)) target = null;
        if (target == null) {
            double best = Double.MAX_VALUE;
            for (net.minecraft.world.entity.monster.Monster m : level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Monster.class,
                    getBoundingBox().inflate(12.0),
                    m -> m.isAlive() && !isCompanionAlly(owner, m) && hasLineOfSightTo(m))) {
                double d = distanceToSqr(m);
                if (d < best) { best = d; target = m; }
            }
        }
        if (target == null) return;
        if (!consumeCompanionWeaponEnergy(DART_SHOT_POWER_FACTOR)) return;

        dartCooldown = 18;
        net.minecraft.world.entity.projectile.Arrow dart = new net.minecraft.world.entity.projectile.Arrow(
            level(), getX(), getY() - 0.1, getZ(),
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        dart.setOwner(owner);
        dart.setBaseDamage(2.0);
        dart.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        Vec3 to = target.getEyePosition().subtract(dart.position());
        dart.shoot(to.x, to.y, to.z, 2.4f, 2.0f);
        level().addFreshEntity(dart);
        playSound(org.restor.fpv.sound.ModSounds.DART.get(), 0.7f, 1.7f);
        // Face the threat while engaging.
        setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
    }

    private boolean hasLineOfSightTo(net.minecraft.world.entity.LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.getEyePosition();
        return level().clip(new net.minecraft.world.level.ClipContext(from, to,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
            == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    // Storage module: right-click while following opens the 9-slot cargo box.
    @Override
    protected boolean openStorage(net.minecraft.world.entity.player.Player player) {
        if (assembly.moduleType() != org.restor.fpv.component.ModuleType.STORAGE) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
                    net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inv, cargo, 1),
                getName()));
        }
        return true;
    }

    // Cargo drops when the drone is destroyed (not on chunk unload).
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !cargo.isEmpty()) {
            net.minecraft.world.Containers.dropContents(level(), blockPosition(), cargo);
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (assemblyResyncDelay >= 0 && assemblyResyncDelay-- == 0) {
            entityData.set(DATA_ASSEMBLY, assembly.save());
        }
        tickStressWear();
        tickTraderDeparture();
        if (!hasWarhead()) return;

        boolean powered = flightController.isArmed();
        if (warhead.proximity()
            && proximityFuze.tick(this, powered, org.restor.fpv.warhead.ProximityFuze.TRIGGER_RADIUS,
                this::isProximityTarget)) {
            explodeWarhead();
            return;
        }
        if (fuze.tick(this, powered)) explodeWarhead();
    }

    // Sold out, the courier packs up and leaves the way a wandering trader does: it climbs
    // clear for a few seconds, drops the card it was built from, then vanishes.
    private void tickTraderDeparture() {
        if (traderDepartureTimer < 0) {
            org.restor.fpv.component.FirmwareType fw = firmware();
            if (fw == null || !fw.trades() || !trader.isSoldOut()
                || trader.getTradingPlayer() != null || isVehicle()) return;
            traderDepartureTimer = 60;
            playSound(net.minecraft.sounds.SoundEvents.WANDERING_TRADER_DISAPPEARED, 1.0f, 1.0f);
            return;
        }
        setDeltaMovement(getDeltaMovement().add(0, 0.03, 0));
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                getX(), getY() + 0.3, getZ(), 4, 0.3, 0.3, 0.3, 0.05);
        }
        if (--traderDepartureTimer > 0) return;
        spawnAtLocation(getDropStack());
        discard();
    }

    // What an airburst head is allowed to go off next to: hostiles and other aircraft, never
    // the pilot, its own passengers or anything friendly to the owner.
    private boolean isProximityTarget(net.minecraft.world.entity.Entity e) {
        if (e == this || hasPassenger(e) || e.isSpectator()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p
            && (p.isCreative() || p == getRacePilot())) return false;
        if (!canHitCompanionTarget(e)) return false;
        return e instanceof net.minecraft.world.entity.LivingEntity
            || e instanceof org.restor.fpv.entity.DroneEntity
            || e instanceof org.restor.fpv.entity.PlaneEntity;
    }

    @Override
    protected void onEntityCollision() {
        if (hasWarhead() && flightController.isArmed() && fuze.isArmed()) {
            explodeWarhead();
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!isAuthorizedPlayerDamage(source)) return false;
        // A warhead build is a flying bomb: explosions and fire set it off.
        if (hasWarhead() && !level().isClientSide
            && (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE))) {
            explodeWarhead();
            return true;
        }
        return super.hurt(source, amount);
    }

    private void explodeWarhead() {
        if (exploded) return;
        exploded = true;
        // Eject first: removePassenger teleports the pilot back to the boarding spot,
        // so they watch the hit from where they launched instead of dying in the blast.
        if (!getPassengers().isEmpty()) ejectPassengers();
        endRemoteControl();
        org.restor.fpv.entity.WarheadDetonation.detonate(level(), this, position(), warhead);
        discard();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!hasPassenger(passenger) || !showPropsInFpv()) {
            super.positionRider(passenger, moveFunction);
            return;
        }
        double yaw = Math.toRadians(getYRot());
        double forward = getBbWidth() * 0.05;
        double x = getX() - Math.sin(yaw) * forward;
        double z = getZ() + Math.cos(yaw) * forward;
        double cameraY = getY() + getBbHeight() * 1.05 - passenger.getEyeHeight();
        moveFunction.accept(passenger, x, cameraY, z);
    }

    private boolean showPropsInFpv() {
        if (!(assembly.frame.getItem() instanceof ComponentItem ci)) return false;
        float size = ci.getStats().frameSize();
        return size >= 5.0f && size <= 8.5f;
    }

    @Override
    public void readFromItemTag(CompoundTag tag) {
        if (tag.contains("Assembly")) {
            setAssembly(DroneAssembly.load(tag.getCompound("Assembly")));
        }
        if (tag.contains("Warhead")) warhead = org.restor.fpv.warhead.WarheadType.byId(tag.getString("Warhead"));
        super.readFromItemTag(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Assembly")) {
            setAssembly(DroneAssembly.load(tag.getCompound("Assembly")));
        }
        if (tag.contains("Warhead")) warhead = org.restor.fpv.warhead.WarheadType.byId(tag.getString("Warhead"));
        super.readAdditionalSaveData(tag);
        if (tag.contains("Cargo")) {
            cargo.fromTag(tag.getList("Cargo", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
        }
        trader.load(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Assembly", assembly.save());
        tag.putString("Warhead", warhead.id());
        tag.put("Cargo", cargo.createTag(registryAccess()));
        trader.save(tag);
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return ModItems.MODULAR_DRONE.get();
    }

    @Override
    protected ItemStack getDropStack() {
        ItemStack stack = super.getDropStack();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.put("Assembly", assembly.save());
            if (hasWarhead()) t.putString("Warhead", warhead.id());
        });
        return stack;
    }
}
