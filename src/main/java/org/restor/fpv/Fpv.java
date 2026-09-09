package org.restor.fpv;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.server.level.ServerPlayer;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.entity.ModBlockEntities;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.menu.ModMenus;
import org.restor.fpv.network.ModNetworking;
import org.slf4j.Logger;

@Mod(Fpv.MODID)
public class Fpv {
    public static final String MODID = "fpv";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> FPV_TAB = CREATIVE_TABS.register("fpv_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fpv"))
            .icon(() -> new ItemStack(ModComponents.FRAME_5INCH.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.ASSEMBLY_STATION.get());
                output.accept(ModBlocks.CONTROL_STATION.get());
                output.accept(ModBlocks.KAMIKAZE_STATION.get());
                output.accept(ModBlocks.RACE_START.get());
                output.accept(ModBlocks.RACE_FINISH.get());
                output.accept(ModBlocks.RACE_GATE.get());
                output.accept(ModBlocks.CHARGING_STATION.get());
                output.accept(ModBlocks.DRONE_NEST.get());
                output.accept(ModBlocks.DRONE_DETECTOR.get());
                output.accept(ModBlocks.DRONE_JAMMER.get());
                output.accept(ModItems.RC_TRANSMITTER.get());
                output.accept(ModItems.RC_TRANSMITTER_LR.get());
                output.accept(ModItems.FPV_GOGGLES.get());
                for (var preset : org.restor.fpv.assembly.DronePresets.all()) output.accept(preset);
                for (var card : org.restor.fpv.assembly.DronePresets.cards()) output.accept(card);
                output.accept(ModItems.DROP_CHARGE.get());
                for (var holder : ModItems.WARHEADS.values()) output.accept(holder.get());
                output.accept(ModItems.HOSTILE_DRONE_SPAWN_EGG.get());
                output.accept(ModItems.PATROL_DRONE_SPAWN_EGG.get());
                output.accept(ModItems.INTERCEPTOR_DRONE_SPAWN_EGG.get());
                output.accept(ModComponents.FRAME_5INCH.get());
                output.accept(ModComponents.FRAME_7INCH.get());
                output.accept(ModComponents.FRAME_TINY_WHOOP.get());
                output.accept(ModComponents.FRAME_CINEWHOOP.get());
                output.accept(ModComponents.FRAME_HEXA_6INCH.get());
                output.accept(ModComponents.FRAME_OCTO_8INCH.get());
                output.accept(ModComponents.FRAME_INTERCEPTOR.get());
                output.accept(ModComponents.PLANE_FRAME_TRAINER.get());
                output.accept(ModComponents.PLANE_FRAME_DELTA.get());
                output.accept(ModComponents.PLANE_FRAME_GLIDER.get());
                output.accept(ModComponents.PLANE_FRAME_STRIKE.get());
                output.accept(ModComponents.PLANE_FRAME_CARGO.get());
                output.accept(ModComponents.PLANE_FRAME_WING.get());
                output.accept(ModComponents.PLANE_FRAME_SCOUT.get());
                output.accept(ModComponents.PLANE_FRAME_KAMIKAZE.get());
                output.accept(ModComponents.PLANE_FRAME_CARRIER.get());
                output.accept(ModComponents.PLANE_FRAME_LOITER.get());
                output.accept(ModComponents.MOTOR_2207_2450.get());
                output.accept(ModComponents.MOTOR_2306_1700.get());
                output.accept(ModComponents.MOTOR_1103_8000.get());
                output.accept(ModComponents.MOTOR_1404_4500.get());
                output.accept(ModComponents.MOTOR_2806_1300.get());
                output.accept(ModComponents.MOTOR_3115_900.get());
                output.accept(ModComponents.MOTOR_2814_1100.get());
                output.accept(ModComponents.MOTOR_3548_900.get());
                output.accept(ModComponents.MOTOR_4250_650.get());
                output.accept(ModComponents.BATTERY_1S_300.get());
                output.accept(ModComponents.BATTERY_4S_1300.get());
                output.accept(ModComponents.BATTERY_6S_1100.get());
                output.accept(ModComponents.BATTERY_4S_650.get());
                output.accept(ModComponents.BATTERY_6S_5000.get());
                output.accept(ModComponents.PROP_5X4X3.get());
                output.accept(ModComponents.PROP_5X4X2.get());
                output.accept(ModComponents.PROP_3X2X3.get());
                output.accept(ModComponents.PROP_65MM.get());
                output.accept(ModComponents.PROP_8X6.get());
                output.accept(ModComponents.PROP_10X5.get());
                output.accept(ModComponents.PROP_12X6.get());
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
                output.accept(ModComponents.MODULE_TARGETING.get());
                output.accept(ModComponents.FIRMWARE_COMPANION.get());
                output.accept(ModComponents.FIRMWARE_SCOUT.get());
                output.accept(ModComponents.FIRMWARE_GUARDIAN.get());
                output.accept(ModComponents.FIRMWARE_TRADER.get());
            })
            .build());

    public Fpv() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, TrackGenerationConfig.SPEC, "fpv-track-generation.toml");

        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        org.restor.fpv.sound.ModSounds.SOUNDS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading e) -> LogQuieter.apply());

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LogQuieter.apply();
        event.enqueueWork(ModNetworking::register);
        LOGGER.info("FPV Drones mod initialized!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("FPV Drones server starting!");
    }

    // The pilot's real (invisible) body follows the drone via riding or the server-side
    // camera-follow teleport, so it physically overlaps ground items along the flight path.
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity().getVehicle() instanceof DroneEntity
            || event.getEntity().getVehicle() instanceof org.restor.fpv.entity.PlaneEntity
            || (event.getEntity() instanceof ServerPlayer sp && sp.getCamera() instanceof DroneEntity)) {
            event.setCanceled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("FPV Drones client setup!");
            event.enqueueWork(org.restor.fpv.client.ModItemProperties::register);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
            event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id(),
                "fpv_overlay",
                (gui, graphics, partialTick, width, height) ->
                    org.restor.fpv.render.FPVOverlay.renderOverlay(graphics, partialTick));
        }
    }
}
