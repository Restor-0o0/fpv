package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.PlaneEntity;

public record PlaneControllerPacket(int planeId, float throttle, float pitch, float roll, float yaw,
                                     boolean arm) implements CustomPacketPayload {
    public static final Type<PlaneControllerPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "plane_controller"));

    public static final StreamCodec<FriendlyByteBuf, PlaneControllerPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.planeId);
            buf.writeFloat(pkt.throttle);
            buf.writeFloat(pkt.pitch);
            buf.writeFloat(pkt.roll);
            buf.writeFloat(pkt.yaw);
            buf.writeBoolean(pkt.arm);
        },
        buf -> new PlaneControllerPacket(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlaneControllerPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.planeId());
                if (entity instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                    plane.setControllerInput(pkt.throttle(), pkt.pitch(), pkt.roll(), pkt.yaw(), pkt.arm());
                }
            }
        });
    }
}
