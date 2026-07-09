package org.restor.fpv.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.ModEntities;

@Mod.EventBusSubscriber(modid = Fpv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CINEWHOOP.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.RACING.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.TINY_WHOOP.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPONENT.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.REMOTE_RACING.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.KAMIKAZE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.OPERATOR_DUMMY.get(), OperatorDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.MODULAR.get(), ModularDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_SCOUT.get(), PlaneRenderer::new);
        event.registerEntityRenderer(ModEntities.PLANE_KAMIKAZE.get(), PlaneRenderer::new);
        event.registerBlockEntityRenderer(org.restor.fpv.block.entity.ModBlockEntities.RACE_GATE.get(),
            RaceGateRenderer::new);
        event.registerBlockEntityRenderer(org.restor.fpv.block.entity.ModBlockEntities.RACE_START.get(),
            RaceStartRenderer::new);
    }
}
