package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosionParticlesPacket {
    private final double x;
    private final double y;
    private final double z;
    private final float spread;

    public ExplosionParticlesPacket(double x, double y, double z, float spread) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.spread = spread;
    }

    public ExplosionParticlesPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.spread = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(spread);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.restor.fpv.client.ClientExplosionParticles.spawn(x, y, z, spread)));
        ctx.get().setPacketHandled(true);
    }
}
