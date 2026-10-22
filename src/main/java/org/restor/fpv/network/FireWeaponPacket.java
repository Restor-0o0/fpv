package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

// Client -> server: the piloting player pressed the weapon key to fire the drone's rocket pod
// or dart gun.
public record FireWeaponPacket(int droneId) implements CustomPacketPayload {
    public static final Type<FireWeaponPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "fire_weapon"));

    public static final StreamCodec<FriendlyByteBuf, FireWeaponPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeInt(pkt.droneId),
        buf -> new FireWeaponPacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FireWeaponPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(pkt.droneId());
            if (e instanceof DroneEntity drone && drone.getRacePilot() == player) {
                drone.fireWeapon(player);
            }
        });
    }
}
