package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

public class CycleModePacket {
    private final int droneId;

    public CycleModePacket(int droneId) {
        this.droneId = droneId;
    }

    public CycleModePacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(droneId);
                if (entity instanceof DroneEntity drone && drone.hasPassenger(player)
                    && drone.flightController.isArmed()) {
                    drone.flightController.cycleMode();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
