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
    public static final int NONE = 0;
    public static final int ADJUST_YAW = 1;
    public static final int ADJUST_PITCH = 2;
    public static final int SET_OFFSET = 3;
    public static final int FLIP_NORMAL = 4;
    public static final int SET_SIZE = 5;

    private final BlockPos pos;
    private final int laps;
    private final boolean clearResults;
    private final boolean cycleColor;
    private final int action;
    private final int value;

    public RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults) {
        this(pos, laps, clearResults, false);
    }

    public RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults, boolean cycleColor) {
        this(pos, laps, clearResults, cycleColor, NONE, 0);
    }

    public RaceStartConfigPacket(BlockPos pos, int action, int value) {
        this(pos, 0, false, false, action, value);
    }

    private RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults, boolean cycleColor, int action, int value) {
        this.pos = pos;
        this.laps = laps;
        this.clearResults = clearResults;
        this.cycleColor = cycleColor;
        this.action = action;
        this.value = value;
    }

    public RaceStartConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.laps = buf.readInt();
        this.clearResults = buf.readBoolean();
        this.cycleColor = buf.readBoolean();
        this.action = buf.readInt();
        this.value = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(laps);
        buf.writeBoolean(clearResults);
        buf.writeBoolean(cycleColor);
        buf.writeInt(action);
        buf.writeInt(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof RaceStartBlockEntity be) {
                if (action == ADJUST_YAW) be.adjustYaw(value);
                else if (action == ADJUST_PITCH) be.adjustPitch(value);
                else if (action == SET_OFFSET) be.setOffset(value);
                else if (action == FLIP_NORMAL) be.flipNormal();
                else if (action == SET_SIZE) be.setSize(value);
                else if (cycleColor) be.cycleColor();
                else if (clearResults) be.clearResults();
                else be.setLaps(laps);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
