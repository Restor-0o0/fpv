package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

// Sent from the station screen: find a loaded remote-controllable drone with this
// link code and start a control session.
public record StartRemoteControlPacket(String code) implements CustomPacketPayload {
    public static final Type<StartRemoteControlPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "start_remote_control"));

    public static final StreamCodec<FriendlyByteBuf, StartRemoteControlPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeUtf(pkt.code, 6),
        buf -> new StartRemoteControlPacket(buf.readUtf(6))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StartRemoteControlPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String code = pkt.code();
            if (!code.matches("\\d{6}")) return;

            for (Entity e : player.serverLevel().getAllEntities()) {
                if (e instanceof DroneEntity drone && drone.isRemoteControllable()
                    && code.equals(drone.getLinkCode())
                    && drone.canPlayerAccess(player)
                    && !drone.hasRemotePilot() && drone.getControllingPassenger() == null) {
                    boolean fpv = org.restor.fpv.item.RcLink.hasGoggles(player);
                    drone.beginRemoteControl(player, fpv);
                    player.sendSystemMessage(Component.translatable(
                        fpv ? "msg.fpv.control_started" : "msg.fpv.tx_link_los"));
                    return;
                }
            }
            player.sendSystemMessage(Component.translatable("msg.fpv.drone_not_found", code));
        });
    }
}
