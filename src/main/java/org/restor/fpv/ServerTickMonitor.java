package org.restor.fpv;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = Fpv.MODID)
public final class ServerTickMonitor {
    private static long tickStart;
    private static double msptAvg;

    private ServerTickMonitor() {}

    @SubscribeEvent
    public static void pre(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void post(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (tickStart == 0L) return;
        double ms = (System.nanoTime() - tickStart) / 1_000_000.0;
        msptAvg = msptAvg <= 0 ? ms : msptAvg * 0.9 + ms * 0.1;
    }

    public static int msptTenths() {
        return (int) Math.min(60000L, Math.round(msptAvg * 10.0));
    }
}
