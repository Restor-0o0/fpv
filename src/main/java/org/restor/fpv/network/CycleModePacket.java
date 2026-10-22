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

public record CycleModePacket(int droneId) implements CustomPacketPayload {
    public static final Type<CycleModePacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "cycle_mode"));

    public static final StreamCodec<FriendlyByteBuf, CycleModePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeInt(pkt.droneId),
        buf -> new CycleModePacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycleModePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(pkt.droneId());
                if (entity instanceof DroneEntity drone && drone.getRacePilot() == player) {
                    drone.cycleFlightMode(player);
                }
            }
        });
    }
}
