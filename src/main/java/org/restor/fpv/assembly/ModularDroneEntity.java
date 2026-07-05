package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Assembly", assembly.save());
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
