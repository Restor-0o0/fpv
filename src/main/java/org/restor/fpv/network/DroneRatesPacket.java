package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

// Sets one per-drone rate multiplier (axis: 0 = pitch, 1 = roll, 2 = yaw).
public class DroneRatesPacket {
    private final int droneId;
    private final int axis;
    private final float value;

    public DroneRatesPacket(int droneId, int axis, float value) {
        this.droneId = droneId;
        this.axis = axis;
        this.value = value;
    }

    public DroneRatesPacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
        this.axis = buf.readByte();
        this.value = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeByte(axis);
        buf.writeFloat(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(droneId);
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.setRateMultiplier(axis, value);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
