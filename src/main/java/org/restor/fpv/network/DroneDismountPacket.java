package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;

// Vanilla dismount is a sneak *state* read once per server tick; on a slow tick a tap is
// gone before the tick runs. This latches the request instead.
public record DroneDismountPacket(int droneId) implements CustomPacketPayload {
    public static final Type<DroneDismountPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "drone_dismount"));

    public static final StreamCodec<FriendlyByteBuf, DroneDismountPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeInt(pkt.droneId),
        buf -> new DroneDismountPacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DroneDismountPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.getVehicle() != null && player.getVehicle().getId() == pkt.droneId()) {
                player.stopRiding();
                player.setShiftKeyDown(false);
                return;
            }
            if (player.level().getEntity(pkt.droneId()) instanceof org.restor.fpv.entity.DroneEntity drone
                && drone.isRemotePilot(player)) {
                drone.endRemoteControl();
            }
        });
    }
}
