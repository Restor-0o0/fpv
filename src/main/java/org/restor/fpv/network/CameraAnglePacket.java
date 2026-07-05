package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

public class CameraAnglePacket {
    private final int droneId;
    private final float delta;

    public CameraAnglePacket(int droneId, float delta) {
        this.droneId = droneId;
        this.delta = delta;
    }

    public CameraAnglePacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
        this.delta = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeFloat(delta);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(droneId);
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.adjustCameraAngle(delta);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
