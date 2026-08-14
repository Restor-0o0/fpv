package org.restor.fpv.network;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.item.ModItems;

// Client -> server: give the player a modular drone item carrying the assembly they built in
// the dev-drone screen. Reuses the normal MODULAR_DRONE item/entity path (place -> mount -> fly),
// so the dev drone flies through exactly the same module-derived physics as any built drone.
public record DevDroneGivePacket(CompoundTag assembly) implements CustomPacketPayload {
    public static final Type<DevDroneGivePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "dev_drone_give"));

    public static final StreamCodec<FriendlyByteBuf, DevDroneGivePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeNbt(pkt.assembly),
        buf -> new DevDroneGivePacket(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DevDroneGivePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || pkt.assembly() == null) return;
            ItemStack stack = new ItemStack(ModItems.MODULAR_DRONE.get());
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put("Assembly", pkt.assembly()));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }
}
