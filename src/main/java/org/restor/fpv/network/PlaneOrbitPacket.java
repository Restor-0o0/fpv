package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.PlaneEntity;

// Client -> server: engage/disengage the plane's orbit autopilot around a world point.
public record PlaneOrbitPacket(int planeId, boolean active, double cx, double cy, double cz,
                                double radius) implements CustomPacketPayload {
    public static final Type<PlaneOrbitPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "plane_orbit"));

    public static final StreamCodec<FriendlyByteBuf, PlaneOrbitPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.planeId);
            buf.writeBoolean(pkt.active);
            buf.writeDouble(pkt.cx);
            buf.writeDouble(pkt.cy);
            buf.writeDouble(pkt.cz);
            buf.writeDouble(pkt.radius);
        },
        buf -> new PlaneOrbitPacket(buf.readInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlaneOrbitPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(pkt.planeId());
            if (e instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                if (!pkt.active()) {
                    plane.setOrbit(false, Vec3.ZERO, 0.0);
                    return;
                }
                if (!finite(pkt.cx(), pkt.cy(), pkt.cz()) || !Double.isFinite(pkt.radius())
                    || pkt.radius() < 0.0 || pkt.radius() > 1024.0) return;
                Vec3 center = new Vec3(pkt.cx(), pkt.cy(), pkt.cz());
                if (center.distanceToSqr(plane.position()) > 1024.0 * 1024.0) return;
                plane.setOrbit(true, center, pkt.radius());
            }
        });
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
