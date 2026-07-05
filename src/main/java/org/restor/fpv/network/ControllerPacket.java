package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

public class ControllerPacket {
    private final int droneId;
    private final float throttle;
    private final float pitch;
    private final float roll;
    private final float yaw;
    private final boolean arm;
    private final boolean modeSwitch;

    public ControllerPacket(int droneId, float throttle, float pitch, float roll, float yaw,
                            boolean arm, boolean modeSwitch) {
        this.droneId = droneId;
        this.throttle = throttle;
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.arm = arm;
        this.modeSwitch = modeSwitch;
    }

    public ControllerPacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
        this.throttle = buf.readFloat();
        this.pitch = buf.readFloat();
        this.roll = buf.readFloat();
        this.yaw = buf.readFloat();
        this.arm = buf.readBoolean();
        this.modeSwitch = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
        buf.writeFloat(throttle);
        buf.writeFloat(pitch);
        buf.writeFloat(roll);
        buf.writeFloat(yaw);
        buf.writeBoolean(arm);
        buf.writeBoolean(modeSwitch);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(droneId);
                if (entity instanceof DroneEntity drone
                    && (drone.hasPassenger(player) || drone.isRemotePilot(player))) {
                    drone.setControllerInput(throttle, pitch, roll, yaw, arm, modeSwitch);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
