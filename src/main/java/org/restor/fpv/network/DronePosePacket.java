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
import org.restor.fpv.entity.DroneEntity;

public record DronePosePacket(int droneId, double x, double y, double z,
                              float yaw, float pitch, float roll,
                              float velX, float velY, float velZ,
                              float angVelRoll, float angVelPitch, float angVelYaw,
                              float impactX, float impactY, float impactZ,
                              byte collisionFlags) implements CustomPacketPayload {
    public static final byte FLAG_HORIZONTAL = 1;
    public static final byte FLAG_VERTICAL   = 2;
    public static final byte FLAG_GROUND     = 4;

    public static final Type<DronePosePacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "drone_pose"));

    public static final StreamCodec<FriendlyByteBuf, DronePosePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.droneId);
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
            buf.writeFloat(pkt.yaw);
            buf.writeFloat(pkt.pitch);
            buf.writeFloat(pkt.roll);
            buf.writeFloat(pkt.velX);
            buf.writeFloat(pkt.velY);
            buf.writeFloat(pkt.velZ);
            buf.writeFloat(pkt.angVelRoll);
            buf.writeFloat(pkt.angVelPitch);
            buf.writeFloat(pkt.angVelYaw);
            buf.writeFloat(pkt.impactX);
            buf.writeFloat(pkt.impactY);
            buf.writeFloat(pkt.impactZ);
            buf.writeByte(pkt.collisionFlags);
        },
        buf -> new DronePosePacket(buf.readInt(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DronePosePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.droneId());
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.applyClientPose(pkt.x(), pkt.y(), pkt.z(), pkt.yaw(), pkt.pitch(), pkt.roll(),
                        pkt.velX(), pkt.velY(), pkt.velZ(),
                        pkt.angVelRoll(), pkt.angVelPitch(), pkt.angVelYaw(),
                        new Vec3(pkt.impactX(), pkt.impactY(), pkt.impactZ()),
                        (pkt.collisionFlags() & FLAG_HORIZONTAL) != 0,
                        (pkt.collisionFlags() & FLAG_VERTICAL) != 0,
                        (pkt.collisionFlags() & FLAG_GROUND) != 0);
                }
            }
        });
    }
}
