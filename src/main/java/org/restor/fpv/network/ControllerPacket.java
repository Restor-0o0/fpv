package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

public record ControllerPacket(int droneId, float throttle, float pitch, float roll, float yaw,
                                boolean arm, boolean modeSwitch, int seq) implements CustomPacketPayload {
    public static final Type<ControllerPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "controller"));

    public static final StreamCodec<FriendlyByteBuf, ControllerPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.droneId);
            buf.writeFloat(pkt.throttle);
            buf.writeFloat(pkt.pitch);
            buf.writeFloat(pkt.roll);
            buf.writeFloat(pkt.yaw);
            buf.writeBoolean(pkt.arm);
            buf.writeBoolean(pkt.modeSwitch);
            buf.writeInt(pkt.seq);
        },
        buf -> new ControllerPacket(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ControllerPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.droneId());
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.setControllerInput(pkt.throttle(), pkt.pitch(), pkt.roll(), pkt.yaw(), pkt.arm(), pkt.modeSwitch(), pkt.seq());
                }
            }
        });
    }
}
