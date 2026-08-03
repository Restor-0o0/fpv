package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.PlaneEntity;

import java.util.function.Supplier;

public class PlaneTargetStreamPacket {
    private final int planeId;
    private final boolean active;
    private final double x;
    private final double y;
    private final double z;

    public PlaneTargetStreamPacket(int planeId, boolean active, double x, double y, double z) {
        this.planeId = planeId;
        this.active = active;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PlaneTargetStreamPacket(FriendlyByteBuf buf) {
        planeId = buf.readInt();
        active = buf.readBoolean();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(planeId);
        buf.writeBoolean(active);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.level().getEntity(planeId);
            if (e instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                plane.setTargetStreaming(active ? new Vec3(x, y, z) : null);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
