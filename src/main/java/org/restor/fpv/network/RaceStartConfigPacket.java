package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

// Client -> server config for a race start block. clearResults=true wipes the leaderboard,
// otherwise laps is applied.
public record RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults, boolean cycleColor,
                                     int action, int value) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int ADJUST_YAW = 1;
    public static final int ADJUST_PITCH = 2;
    public static final int SET_OFFSET = 3;
    public static final int FLIP_NORMAL = 4;
    public static final int SET_SIZE = 5;

    public RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults) {
        this(pos, laps, clearResults, false);
    }

    public RaceStartConfigPacket(BlockPos pos, int laps, boolean clearResults, boolean cycleColor) {
        this(pos, laps, clearResults, cycleColor, NONE, 0);
    }

    public RaceStartConfigPacket(BlockPos pos, int action, int value) {
        this(pos, 0, false, false, action, value);
    }

    public static final Type<RaceStartConfigPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "race_start_config"));

    public static final StreamCodec<FriendlyByteBuf, RaceStartConfigPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeBlockPos(pkt.pos);
            buf.writeInt(pkt.laps);
            buf.writeBoolean(pkt.clearResults);
            buf.writeBoolean(pkt.cycleColor);
            buf.writeInt(pkt.action);
            buf.writeInt(pkt.value);
        },
        buf -> new RaceStartConfigPacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean(),
            buf.readBoolean(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaceStartConfigPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = pkt.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof RaceStartBlockEntity be) {
                if (pkt.action() == ADJUST_YAW) be.adjustYaw(pkt.value());
                else if (pkt.action() == ADJUST_PITCH) be.adjustPitch(pkt.value());
                else if (pkt.action() == SET_OFFSET) be.setOffset(pkt.value());
                else if (pkt.action() == FLIP_NORMAL) be.flipNormal();
                else if (pkt.action() == SET_SIZE) be.setSize(pkt.value());
                else if (pkt.cycleColor()) be.cycleColor();
                else if (pkt.clearResults()) be.clearResults();
                else be.setLaps(pkt.laps());
            }
        });
    }
}
