package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

public class SetDroneCodePacket {
    private final int droneId;
    private final String code;

    public SetDroneCodePacket(int droneId, String code) {
        this.droneId = droneId;
        this.code = code;
    }

    public SetDroneCodePacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
        this.code = buf.readUtf(6);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeUtf(code, 6);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            // Empty code = clear the drone's link code.
            if (player == null || !(code.isEmpty() || code.matches("\\d{6}"))) return;
            Entity entity = player.level().getEntity(droneId);
            if (entity instanceof DroneEntity drone && drone.isRemoteControllable()
                && drone.distanceTo(player) < 8) {
                drone.setLinkCode(code);
                player.sendSystemMessage(code.isEmpty()
                    ? Component.translatable("msg.fpv.code_cleared")
                    : Component.translatable("msg.fpv.code_set", code));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
