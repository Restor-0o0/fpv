package org.restor.fpv.entity;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.restor.fpv.Fpv;

// Attribute registration for the mod's living (Mob) entities. Vehicle drones are plain
// Entities and don't need attributes — only AI mobs like HostileDroneEntity do.
@EventBusSubscriber(modid = Fpv.MODID)
public class ModEntityAttributes {
    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HOSTILE_DRONE.get(), NpcDroneEntity.createAttributes().build());
        event.put(ModEntities.PATROL_DRONE.get(), PatrolDroneEntity.createAttributes().build());
        event.put(ModEntities.INTERCEPTOR_DRONE.get(), InterceptorDroneEntity.createAttributes().build());
        event.put(ModEntities.VILLAGE_DRONE.get(), VillageDroneEntity.createAttributes().build());
        event.put(ModEntities.BOMBER_DRONE.get(), BomberDroneEntity.createAttributes().build());
        event.put(ModEntities.ROCKET_DRONE.get(), RocketDroneEntity.createAttributes().build());
        event.put(ModEntities.SCOUT_DRONE.get(), ScoutDroneEntity.createAttributes().build());
        event.put(ModEntities.JAMMER_DRONE.get(), JammerDroneEntity.createAttributes().build());
    }
}
