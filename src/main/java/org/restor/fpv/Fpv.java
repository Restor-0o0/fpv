package org.restor.fpv;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.TriState;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.entity.ModBlockEntities;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.menu.ModMenus;
import org.restor.fpv.network.ModNetworking;
import org.restor.fpv.render.FPVOverlay;
import org.slf4j.Logger;

@Mod(Fpv.MODID)
public class Fpv {
    public static final String MODID = "fpv";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FPV_TAB = CREATIVE_TABS.register("fpv_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fpv"))
            .icon(() -> new ItemStack(ModItems.CINEWHOOP.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.WORKBENCH.get());
                output.accept(ModBlocks.ASSEMBLY_STATION.get());
                output.accept(ModBlocks.CONTROL_STATION.get());
                output.accept(ModBlocks.KAMIKAZE_STATION.get());
                output.accept(ModBlocks.RACE_START.get());
                output.accept(ModBlocks.RACE_FINISH.get());
                output.accept(ModBlocks.RACE_GATE.get());
                output.accept(ModBlocks.DRONE_NEST.get());
                output.accept(ModItems.CINEWHOOP.get());
                output.accept(ModItems.RACING_DRONE.get());
                output.accept(ModItems.TINY_WHOOP.get());
                output.accept(ModItems.REMOTE_RACING_DRONE.get());
                output.accept(ModItems.KAMIKAZE_DRONE.get());
                output.accept(ModItems.MODULAR_DRONE.get());
                output.accept(ModItems.DROP_CHARGE.get());
                output.accept(ModItems.PLANE.get());
                output.accept(ModItems.PLANE_SCOUT.get());
                output.accept(ModItems.PLANE_KAMIKAZE.get());
                output.accept(ModItems.HOSTILE_DRONE_SPAWN_EGG.get());
                output.accept(ModItems.PATROL_DRONE_SPAWN_EGG.get());
                output.accept(ModItems.INTERCEPTOR_DRONE_SPAWN_EGG.get());
                output.accept(ModComponents.FRAME_5INCH.get());
                output.accept(ModComponents.FRAME_7INCH.get());
                output.accept(ModComponents.FRAME_TINY_WHOOP.get());
                output.accept(ModComponents.FRAME_CINEWHOOP.get());
                output.accept(ModComponents.FRAME_HEXA_6INCH.get());
                output.accept(ModComponents.FRAME_OCTO_8INCH.get());
                output.accept(ModComponents.MOTOR_2207_2450.get());
                output.accept(ModComponents.MOTOR_2306_1700.get());
                output.accept(ModComponents.MOTOR_1103_8000.get());
                output.accept(ModComponents.MOTOR_1404_4500.get());
                output.accept(ModComponents.MOTOR_2806_1300.get());
                output.accept(ModComponents.MOTOR_3115_900.get());
                output.accept(ModComponents.BATTERY_1S_300.get());
                output.accept(ModComponents.BATTERY_4S_1300.get());
                output.accept(ModComponents.BATTERY_6S_1100.get());
                output.accept(ModComponents.BATTERY_4S_650.get());
                output.accept(ModComponents.PROP_5X4X3.get());
                output.accept(ModComponents.PROP_5X4X2.get());
                output.accept(ModComponents.PROP_3X2X3.get());
                output.accept(ModComponents.PROP_65MM.get());
                output.accept(ModComponents.VTX_25MW.get());
                output.accept(ModComponents.VTX_200MW.get());
                output.accept(ModComponents.VTX_800MW.get());
                output.accept(ModComponents.CAMERA_STANDARD.get());
                output.accept(ModComponents.CAMERA_WIDE.get());
                output.accept(ModComponents.FC_F411.get());
                output.accept(ModComponents.FC_H7.get());
                output.accept(ModComponents.MODULE_PAYLOAD_BAY.get());
                output.accept(ModComponents.MODULE_WARHEAD.get());
                output.accept(ModComponents.MODULE_DART_GUN.get());
                output.accept(ModComponents.MODULE_STORAGE.get());
            })
            .build());

    public Fpv(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegisterOverlays);
        modEventBus.addListener(ModNetworking::register);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("FPV Drones mod initialized!");
    }

    private void onRegisterOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(MODID, "fpv_overlay"),
            (graphics, deltaTracker) ->
                FPVOverlay.renderOverlay(graphics, deltaTracker.getGameTimeDeltaPartialTick(true)));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("FPV Drones server starting!");
    }

    // The pilot's real (invisible) body follows the drone via riding or the server-side
    // camera-follow teleport, so it physically overlaps ground items along the flight path.
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().getVehicle() instanceof DroneEntity
            || event.getPlayer().getVehicle() instanceof org.restor.fpv.entity.PlaneEntity
            || (event.getPlayer() instanceof ServerPlayer sp && sp.getCamera() instanceof DroneEntity)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("FPV Drones client setup!");
        }
    }
}
