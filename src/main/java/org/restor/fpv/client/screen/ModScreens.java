package org.restor.fpv.client.screen;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.menu.ModMenus;

@Mod.EventBusSubscriber(modid = Fpv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {
    @SubscribeEvent
    public static void onRegisterScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.ASSEMBLY_STATION.get(), AssemblyStationScreen::new);
            MenuScreens.register(ModMenus.CHARGING_STATION.get(), ChargingStationScreen::new);
            MenuScreens.register(ModMenus.CARRIER.get(), CarrierScreen::new);
            MenuScreens.register(ModMenus.CARGO_BAY.get(), CargoBayScreen::new);
        });
    }
}
