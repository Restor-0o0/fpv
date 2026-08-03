package org.restor.fpv.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.item.ModItems;

import java.util.function.Supplier;

/**
 * Client → server: give the player a modular drone item carrying the assembly they built in
 * the dev-drone screen. Reuses the normal MODULAR_DRONE item/entity path (place → mount → fly),
 * so the dev drone flies through exactly the same module-derived physics as any built drone.
 */
public class DevDroneGivePacket {
    private final CompoundTag assembly;

    public DevDroneGivePacket(CompoundTag assembly) {
        this.assembly = assembly;
    }

    public DevDroneGivePacket(FriendlyByteBuf buf) {
        this.assembly = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(assembly);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || assembly == null) return;
            ItemStack stack = new ItemStack(ModItems.MODULAR_DRONE.get());
            stack.getOrCreateTag().put("Assembly", assembly);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
