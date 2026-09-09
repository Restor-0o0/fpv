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

// Sets one per-drone rate multiplier (axis: 0 = pitch, 1 = roll, 2 = yaw).
public record DroneRatesPacket(int droneId, int axis, float value) implements CustomPacketPayload {
    public static final Type<DroneRatesPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "drone_rates"));

    public static final StreamCodec<FriendlyByteBuf, DroneRatesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.droneId);
            buf.writeByte(pkt.axis);
            buf.writeFloat(pkt.value);
        },
        buf -> new DroneRatesPacket(buf.readInt(), buf.readByte(), buf.readFloat())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DroneRatesPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.droneId());
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.setRateMultiplier(pkt.axis(), pkt.value());
                }
            }
        });
    }
}
