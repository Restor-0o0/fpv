package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

public class RemoteRacingDroneEntity extends RemoteDroneEntity {
    public RemoteRacingDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.RACING);
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.REMOTE_RACING_DRONE.get();
    }
}
