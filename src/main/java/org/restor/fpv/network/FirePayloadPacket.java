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

// Client -> server: the piloting player pressed FIRE_PAYLOAD to drop one payload charge from
// the drone they are flying (ridden or via a control station).
public record FirePayloadPacket(int droneId) implements CustomPacketPayload {
    public static final Type<FirePayloadPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "fire_payload"));

    public static final StreamCodec<FriendlyByteBuf, FirePayloadPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeInt(pkt.droneId),
        buf -> new FirePayloadPacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FirePayloadPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(pkt.droneId());
            if (e instanceof DroneEntity drone && drone.getRacePilot() == player) {
                drone.firePayload(player);
            }
        });
    }
}
