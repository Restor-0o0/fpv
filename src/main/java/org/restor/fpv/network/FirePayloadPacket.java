package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

// Client -> server: the piloting player pressed FIRE_PAYLOAD to drop one payload charge from
// the drone they are flying (ridden or via a control station).
public class FirePayloadPacket {
    private final int droneId;

    public FirePayloadPacket(int droneId) {
        this.droneId = droneId;
    }

    public FirePayloadPacket(FriendlyByteBuf buf) {
        this.droneId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(droneId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.level().getEntity(droneId);
            if (e instanceof DroneEntity drone && drone.getRacePilot() == player) {
                drone.firePayload(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
