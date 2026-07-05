package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

import java.util.function.Supplier;

// Client -> server config for a race start block. clearResults=true wipes the leaderboard,
// otherwise laps is applied.
public class RaceStartConfigPacket {
    private final BlockPos pos;
    private final int laps;
    private final boolean clearResults;

    public RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults) {
        this.pos = pos;
        this.laps = laps;
        this.clearResults = clearResults;
    }

    public RaceStartConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.laps = buf.readInt();
        this.clearResults = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(laps);
        buf.writeBoolean(clearResults);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof RaceStartBlockEntity be) {
                if (clearResults) be.clearResults();
                else be.setLaps(laps);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
