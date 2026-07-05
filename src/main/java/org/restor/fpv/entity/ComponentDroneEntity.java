package org.restor.fpv.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.Battery;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.flight.FlightController;
import org.restor.fpv.recipe.DroneBuilder;

public class ComponentDroneEntity extends DroneEntity {
    public ComponentDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.CINEWHOOP);
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.ASSEMBLED_DRONE.get();
    }

    @Override
    public void readFromItemTag(CompoundTag tag) {
        if (tag.contains(DroneBuilder.TAG_COMPONENTS)) {
            float frameSize = tag.getFloat("FrameSize");
            float motorPower = tag.getFloat("MotorPower");
            int batteryCells = tag.getInt("BatteryCells");
            int batteryCapacity = tag.getInt("BatteryCapacity");
            float propEfficiency = tag.getFloat("PropEfficiency");

            if (frameSize > 0) {
                float mass = 0.3f + frameSize * 0.05f;
                this.droneData = new DroneData(
                    DroneData.DroneType.CUSTOM, "Custom", 1.0f,
                    720f, 720f, 480f,
                    mass, 0.03f,
                    batteryCapacity,
                    30f,
                    motorPower, 0.65f,
                    10.0f
                );
                if (batteryCapacity > 0) {
                    this.battery = new Battery(batteryCapacity, 4.2f * Math.max(batteryCells, 1));
                }
                this.flightController = new FlightController(this.droneData);
                adjustCameraAngle(30f - getCameraAngle());
            }
        }
    }
}
