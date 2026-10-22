package org.restor.fpv.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.client.ui.screen.settings.FpvSettingsScreen;
import org.restor.fpv.rules.ExplosiveParam;
import org.restor.fpv.rules.ServerRule;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class ClientRules {
    private ClientRules() {}

    public static void receive(double[] values, double[] explosives) {
        Minecraft mc = Minecraft.getInstance();
        // The integrated server shares this JVM's config, so only a remote server's values need mirroring.
        if (mc.hasSingleplayerServer()) {
            clear();
        } else {
            ServerRule.setRemote(values);
            ExplosiveParam.setRemote(explosives);
        }
        if (mc.screen instanceof FpvSettingsScreen screen) screen.rulesChanged();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        ServerRule.clearRemote();
        ExplosiveParam.clearRemote();
    }
}
