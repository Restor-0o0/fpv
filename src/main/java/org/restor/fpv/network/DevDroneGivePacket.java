package org.restor.fpv.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.compat.CustomData;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.item.ModItems;

// Client -> server: give the player a modular drone item carrying the assembly they built in
// the dev-drone screen. Reuses the normal MODULAR_DRONE item/entity path (place -> mount -> fly),
// so the dev drone flies through exactly the same module-derived physics as any built drone.
public record DevDroneGivePacket(CompoundTag assembly) implements CustomPacketPayload {
    public static final Type<DevDroneGivePacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "dev_drone_give"));

    public static final StreamCodec<FriendlyByteBuf, DevDroneGivePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeNbt(pkt.assembly),
        buf -> new DevDroneGivePacket(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DevDroneGivePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || pkt.assembly() == null
                || !(player.isCreative() || player.hasPermissions(2))) return;
            DroneAssembly assembly = DroneAssembly.load(pkt.assembly());
            if (!assembly.validate().isComplete()) return;
            ItemStack stack = new ItemStack(ModItems.MODULAR_DRONE.get());
            CustomData.update(stack, t -> t.put("Assembly", assembly.save()));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }
}
