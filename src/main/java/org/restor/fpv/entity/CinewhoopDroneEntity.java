package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

public class CinewhoopDroneEntity extends DroneEntity {
    public CinewhoopDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.CINEWHOOP);
    }
}
