package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

public record CameraAnglePacket(int droneId, float delta) implements CustomPacketPayload {
    public static final Type<CameraAnglePacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "camera_angle"));

    public static final StreamCodec<FriendlyByteBuf, CameraAnglePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.droneId);
            buf.writeFloat(pkt.delta);
        },
        buf -> new CameraAnglePacket(buf.readInt(), buf.readFloat())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CameraAnglePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.droneId());
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.adjustCameraAngle(pkt.delta());
                }
            }
        });
    }
}
