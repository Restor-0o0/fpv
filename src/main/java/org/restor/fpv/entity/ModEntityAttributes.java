package org.restor.fpv.entity;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.restor.fpv.Fpv;

// Attribute registration for the mod's living (Mob) entities. Vehicle drones are plain
// Entities and don't need attributes — only AI mobs like HostileDroneEntity do.
@Mod.EventBusSubscriber(modid = Fpv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {
    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HOSTILE_DRONE.get(), NpcDroneEntity.createAttributes().build());
        event.put(ModEntities.PATROL_DRONE.get(), PatrolDroneEntity.createAttributes().build());
        event.put(ModEntities.INTERCEPTOR_DRONE.get(), InterceptorDroneEntity.createAttributes().build());
    }
}
