package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.PlaneEntity;

public record PlaneTargetStreamPacket(int planeId, boolean active, double x, double y,
                                       double z) implements CustomPacketPayload {
    public static final Type<PlaneTargetStreamPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "plane_target_stream"));

    public static final StreamCodec<FriendlyByteBuf, PlaneTargetStreamPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.planeId);
            buf.writeBoolean(pkt.active);
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
        },
        buf -> new PlaneTargetStreamPacket(buf.readInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
            buf.readDouble())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlaneTargetStreamPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(pkt.planeId());
            if (e instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                if (!pkt.active()) {
                    plane.setTargetStreaming(null);
                    return;
                }
                if (!Double.isFinite(pkt.x()) || !Double.isFinite(pkt.y()) || !Double.isFinite(pkt.z())) return;
                Vec3 center = new Vec3(pkt.x(), pkt.y(), pkt.z());
                if (center.distanceToSqr(plane.position()) <= 1024.0 * 1024.0) {
                    plane.setTargetStreaming(center);
                }
            }
        });
    }
}
