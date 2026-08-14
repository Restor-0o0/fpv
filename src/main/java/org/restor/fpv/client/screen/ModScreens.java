package org.restor.fpv.client.screen;

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
        event.register(ModMenus.WORKBENCH.get(), WorkbenchScreen::new);
        event.register(ModMenus.ASSEMBLY_STATION.get(), AssemblyStationScreen::new);
    }
}
