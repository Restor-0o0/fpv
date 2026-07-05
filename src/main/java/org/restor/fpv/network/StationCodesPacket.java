package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.block.entity.ControlStationBlockEntity;

import java.util.function.Supplier;

// Adds or removes a saved link code on a control station block.
public class StationCodesPacket {
    private final BlockPos pos;
    private final String code;
    private final boolean add;

    public StationCodesPacket(BlockPos pos, String code, boolean add) {
        this.pos = pos;
        this.code = code;
        this.add = add;
    }

    public StationCodesPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.code = buf.readUtf(6);
        this.add = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(code, 6);
        buf.writeBoolean(add);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !code.matches("\\d{6}")) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
                if (add) be.addCode(code);
                else be.removeCode(code);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
