package org.restor.fpv.client.render;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.ModEntities;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public class ModRenderers {
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new GogglesLayer(renderer, event.getContext().getItemRenderer()));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CINEWHOOP.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.RACING.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.TINY_WHOOP.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.REMOTE_RACING.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.KAMIKAZE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.OPERATOR_DUMMY.get(), OperatorDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.MODULAR.get(), ModularDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.MODULAR_PLANE.get(), ModularPlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_SCOUT.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_KAMIKAZE.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_DELTA.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_GLIDER.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_CARGO.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_CARRIER.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_LOITER.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_LONGRANGE.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_INTERCEPTOR.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.DROP_CHARGE.get(),
            ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 0.9f, false));
        event.registerEntityRenderer(ModEntities.MINI_ROCKET.get(),
            ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0f, false));
        event.registerEntityRenderer(ModEntities.HOSTILE_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.PATROL_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.INTERCEPTOR_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGE_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.BOMBER_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCKET_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.SCOUT_DRONE.get(), NpcDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.JAMMER_DRONE.get(), NpcDroneRenderer::new);
        event.registerBlockEntityRenderer(org.restor.fpv.block.entity.ModBlockEntities.RACE_GATE.get(),
            RaceGateRenderer::new);
        event.registerBlockEntityRenderer(org.restor.fpv.block.entity.ModBlockEntities.RACE_START.get(),
            RaceStartRenderer::new);
        event.registerBlockEntityRenderer(org.restor.fpv.block.entity.ModBlockEntities.PARTS_PILE.get(),
            PartsPileRenderer::new);
    }
}
