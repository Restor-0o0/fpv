package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

// Sent from the station screen: find a loaded remote-controllable drone with this
// link code and start a control session.
public class StartRemoteControlPacket {
    private final String code;

    public StartRemoteControlPacket(String code) {
        this.code = code;
    }

    public StartRemoteControlPacket(FriendlyByteBuf buf) {
        this.code = buf.readUtf(6);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(code, 6);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !code.matches("\\d{6}")) return;

            for (Entity e : player.serverLevel().getAllEntities()) {
                if (e instanceof DroneEntity drone && drone.isRemoteControllable()
                    && code.equals(drone.getLinkCode())
                    && !drone.hasRemotePilot() && drone.getControllingPassenger() == null) {
                    drone.beginRemoteControl(player);
                    player.sendSystemMessage(Component.translatable("msg.fpv.control_started"));
                    return;
                }
            }
            player.sendSystemMessage(Component.translatable("msg.fpv.drone_not_found", code));
        });
        ctx.get().setPacketHandled(true);
    }
}
