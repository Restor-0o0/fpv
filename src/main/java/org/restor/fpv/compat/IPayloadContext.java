package org.restor.fpv.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public final class IPayloadContext {
    private final NetworkEvent.Context context;

    public IPayloadContext(NetworkEvent.Context context) {
        this.context = context;
    }

    public void enqueueWork(Runnable work) {
        context.enqueueWork(work);
    }

    public Player player() {
        ServerPlayer sender = context.getSender();
        if (sender != null) return sender;
        return FMLEnvironment.dist.isClient() ? ClientHolder.player() : null;
    }

    private static final class ClientHolder {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
