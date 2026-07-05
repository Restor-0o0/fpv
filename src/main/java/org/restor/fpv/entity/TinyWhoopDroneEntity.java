package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

public class TinyWhoopDroneEntity extends DroneEntity {
    public TinyWhoopDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.TINY_WHOOP);
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.TINY_WHOOP.get();
    }
}
