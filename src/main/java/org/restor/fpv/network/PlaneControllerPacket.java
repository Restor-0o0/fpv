package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.PlaneEntity;

import java.util.function.Supplier;

public class PlaneControllerPacket {
    private final int planeId;
    private final float throttle;
    private final float pitch;
    private final float roll;
    private final float yaw;
    private final boolean arm;

    public PlaneControllerPacket(int planeId, float throttle, float pitch, float roll, float yaw, boolean arm) {
        this.planeId = planeId;
        this.throttle = throttle;
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.arm = arm;
    }

    public PlaneControllerPacket(FriendlyByteBuf buf) {
        this.planeId = buf.readInt();
        this.throttle = buf.readFloat();
        this.pitch = buf.readFloat();
        this.roll = buf.readFloat();
        this.yaw = buf.readFloat();
        this.arm = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(planeId);
        buf.writeFloat(throttle);
        buf.writeFloat(pitch);
        buf.writeFloat(roll);
        buf.writeFloat(yaw);
        buf.writeBoolean(arm);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(planeId);
                if (entity instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                    plane.setControllerInput(throttle, pitch, roll, yaw, arm);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
