package org.restor.fpv;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Fpv.MODID)
public final class ServerTickMonitor {
    private static long tickStart;
    private static double msptAvg;

    private ServerTickMonitor() {}

    @SubscribeEvent
    public static void pre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void post(ServerTickEvent.Post event) {
        if (tickStart == 0L) return;
        double ms = (System.nanoTime() - tickStart) / 1_000_000.0;
        msptAvg = msptAvg <= 0 ? ms : msptAvg * 0.9 + ms * 0.1;
    }

    public static int msptTenths() {
        return (int) Math.min(60000L, Math.round(msptAvg * 10.0));
    }
}
