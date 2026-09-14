package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;

public record ExplosionParticlesPacket(double x, double y, double z, float spread) implements CustomPacketPayload {
    public static final Type<ExplosionParticlesPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "explosion_particles"));

    public static final StreamCodec<FriendlyByteBuf, ExplosionParticlesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
            buf.writeFloat(pkt.spread);
        },
        buf -> new ExplosionParticlesPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ExplosionParticlesPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> org.restor.fpv.client.render.ClientExplosionParticles.spawn(
            pkt.x(), pkt.y(), pkt.z(), pkt.spread()));
    }
}
