package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

public record SetDroneCodePacket(int droneId, String code) implements CustomPacketPayload {
    public static final Type<SetDroneCodePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "set_drone_code"));

    public static final StreamCodec<FriendlyByteBuf, SetDroneCodePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.droneId);
            buf.writeUtf(pkt.code, 6);
        },
        buf -> new SetDroneCodePacket(buf.readInt(), buf.readUtf(6))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetDroneCodePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer sp ? sp : null;
            String code = pkt.code();
            // Empty code = clear the drone's link code.
            if (player == null || !(code.isEmpty() || code.matches("\\d{6}"))) return;
            Entity entity = player.level().getEntity(pkt.droneId());
            if (entity instanceof DroneEntity drone && drone.isRemoteControllable()
                && drone.distanceTo(player) < 8) {
                drone.setLinkCode(code);
                player.sendSystemMessage(code.isEmpty()
                    ? Component.translatable("msg.fpv.code_cleared")
                    : Component.translatable("msg.fpv.code_set", code));
            }
        });
    }
}
