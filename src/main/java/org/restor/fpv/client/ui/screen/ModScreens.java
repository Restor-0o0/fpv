package org.restor.fpv.client.ui.screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.menu.ModMenus;

@EventBusSubscriber(modid = Fpv.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {
    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLY_STATION.get(), AssemblyStationScreen::new);
        event.register(ModMenus.CHARGING_STATION.get(), ChargingStationScreen::new);
        event.register(ModMenus.GUARD_STATION.get(), GuardStationScreen::new);
        event.register(ModMenus.CARRIER.get(), CarrierScreen::new);
        event.register(ModMenus.CARGO_BAY.get(), CargoBayScreen::new);
    }
}
