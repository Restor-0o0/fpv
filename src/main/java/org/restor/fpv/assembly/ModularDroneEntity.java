package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
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
    private float trimRoll, trimPitch, trimYaw; // °/s at full throttle
    // Warhead module state (mirrors KamikazeDroneEntity's impact logic).
    private int airborneTicks;
    private boolean exploded;
    // Dart gun (companion mode) fire cooldown.
    private int dartCooldown;
    // Cargo box contents (storage module). Kept even when the module is swapped out —
    // the items drop when the drone breaks.
    private final net.minecraft.world.SimpleContainer cargo = new net.minecraft.world.SimpleContainer(9);

    public ModularDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.CINEWHOOP);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ASSEMBLY, new CompoundTag());
    }

    public DroneAssembly getAssembly() {
        return assembly;
    }

    public void setAssembly(DroneAssembly a) {
        this.assembly = a;
        entityData.set(DATA_ASSEMBLY, a.save());
        applyAssembly();
    }

    // Derive flight characteristics from the installed components.
    private void applyAssembly() {
        this.droneData = assembly.buildDroneData();
        this.flightController = new FlightController(droneData);
        this.battery = new Battery(droneData.batteryCapacity, 4.2f);
        adjustCameraAngle(droneData.cameraAngle - getCameraAngle());
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
            applyAssembly();
        }
    }

    // Trim scales with throttle: no thrust = no imbalance moment.
    @Override protected float trimRollRate()  { return trimRoll * throttleInput; }
    @Override protected float trimPitchRate() { return trimPitch * throttleInput; }
    @Override protected float trimYawRate()   { return trimYaw * throttleInput; }

    // ------------------------------------------------------------------
    //  Module effects (FPV_IDEAS §4)
    // ------------------------------------------------------------------

    private boolean hasWarhead() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.WARHEAD;
    }

    // Drop charges only fit when a Payload Bay module is installed.
    @Override
    protected int payloadCapacity() {
        return assembly.moduleType() == org.restor.fpv.component.ModuleType.PAYLOAD_BAY ? MAX_PAYLOAD : 0;
    }

    // Companion combat: a dart gun module engages hostile mobs near the owner. Arrows are
    // owned by the player so damage/aggro credit goes to them, not to a non-living drone.
    @Override
    protected void companionAttackTick(net.minecraft.server.level.ServerPlayer owner) {
        if (assembly.moduleType() != org.restor.fpv.component.ModuleType.DART_GUN) return;
        if (dartCooldown > 0) { dartCooldown--; return; }

        net.minecraft.world.entity.monster.Monster target = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.monster.Monster m : level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                getBoundingBox().inflate(12.0),
                m -> m.isAlive() && hasLineOfSightTo(m))) {
            double d = distanceToSqr(m);
            if (d < best) { best = d; target = m; }
        }
        if (target == null) return;

        dartCooldown = 18;
        net.minecraft.world.entity.projectile.Arrow dart = new net.minecraft.world.entity.projectile.Arrow(
            level(), getX(), getY() - 0.1, getZ());
        dart.setOwner(owner);
        dart.setBaseDamage(2.0);
        dart.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        Vec3 to = target.getEyePosition().subtract(dart.position());
        dart.shoot(to.x, to.y, to.z, 2.4f, 2.0f);
        level().addFreshEntity(dart);
        playSound(net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT, 0.7f, 1.7f);
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
        if (level().isClientSide || !hasWarhead()) return;

        if (flightController.isArmed() && !onGround()) {
            airborneTicks++;
        } else if (!flightController.isArmed()) {
            airborneTicks = 0;
        }
        // Grace period so arming/takeoff from the ground doesn't detonate instantly.
        if (airborneTicks > 10 && (horizontalCollision || verticalCollision || onGround())) {
            explodeWarhead();
        }
    }

    @Override
    protected void onEntityCollision() {
        if (hasWarhead() && flightController.isArmed() && airborneTicks > 10) {
            explodeWarhead();
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
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
        float power = (float) org.restor.fpv.Config.DEV_EXPLOSION_POWER.get().doubleValue();
        double x = getX(), y = getY(), z = getZ();
        org.restor.fpv.entity.KamikazeExplosionEffects.flingBlocks(level(), x, y, z, power);
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> before =
            org.restor.fpv.entity.KamikazeExplosionEffects.captureBreakableBlocks(level(), x, y, z, power);
        level().explode(this, x, y, z, power, Level.ExplosionInteraction.TNT);
        org.restor.fpv.entity.KamikazeExplosionEffects.restoreUnbrokenBlocks(level(), before, x, y, z);
        org.restor.fpv.entity.KamikazeExplosionEffects.removeFloatingBlocks(level(), x, y, z, power);
        org.restor.fpv.entity.KamikazeExplosionEffects.damageMobs(level(), this, x, y, z, power);
        org.restor.fpv.entity.KamikazeExplosionEffects.spawn(level(), x, y, z, power);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Assembly")) {
            setAssembly(DroneAssembly.load(tag.getCompound("Assembly")));
        }
        if (tag.contains("Cargo")) {
            cargo.fromTag(tag.getList("Cargo", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Assembly", assembly.save());
        tag.put("Cargo", cargo.createTag());
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return ModItems.MODULAR_DRONE.get();
    }

    @Override
    protected ItemStack getDropStack() {
        ItemStack stack = new ItemStack(ModItems.MODULAR_DRONE.get());
        stack.getOrCreateTag().put("Assembly", assembly.save());
        return stack;
    }
}
