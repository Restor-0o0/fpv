package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.PlaneEntity;

import java.util.function.Supplier;

// Client -> server: engage/disengage the plane's orbit autopilot around a world point.
public class PlaneOrbitPacket {
    private final int planeId;
    private final boolean active;
    private final double cx, cy, cz;
    private final double radius;

    public PlaneOrbitPacket(int planeId, boolean active, double cx, double cy, double cz, double radius) {
        this.planeId = planeId;
        this.active = active;
        this.cx = cx; this.cy = cy; this.cz = cz;
        this.radius = radius;
    }

    public PlaneOrbitPacket(FriendlyByteBuf buf) {
        this.planeId = buf.readInt();
        this.active = buf.readBoolean();
        this.cx = buf.readDouble();
        this.cy = buf.readDouble();
        this.cz = buf.readDouble();
        this.radius = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(planeId);
        buf.writeBoolean(active);
        buf.writeDouble(cx);
        buf.writeDouble(cy);
        buf.writeDouble(cz);
        buf.writeDouble(radius);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.level().getEntity(planeId);
            if (e instanceof PlaneEntity plane && plane.hasPassenger(player)) {
                plane.setOrbit(active, new Vec3(cx, cy, cz), radius);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
