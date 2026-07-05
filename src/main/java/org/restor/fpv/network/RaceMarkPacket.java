package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// Client -> server race-route marking. BEGIN clears and opens the route on a start block,
// ADD appends the given gate, FINISH saves it.
public class RaceMarkPacket {
    public static final int BEGIN = 0;
    public static final int ADD = 1;
    public static final int FINISH = 2;

    private static final double INTERACTION_DISTANCE_SQR = 4096.0;
    private static final Map<UUID, MarkingSession> MARKING_SESSIONS = new HashMap<>();

    private static final class MarkingSession {
        private final BlockPos startPos;
        private final List<BlockPos> route = new ArrayList<>();

        private MarkingSession(BlockPos startPos) {
            this.startPos = startPos;
        }
    }

    private final BlockPos startPos;
    private final int action;
    private final BlockPos gatePos;

    public RaceMarkPacket(BlockPos startPos, int action, BlockPos gatePos) {
        this.startPos = startPos;
        this.action = action;
        this.gatePos = gatePos;
    }

    public RaceMarkPacket(FriendlyByteBuf buf) {
        this.startPos = buf.readBlockPos();
        this.action = buf.readInt();
        this.gatePos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(startPos);
        buf.writeInt(action);
        buf.writeBlockPos(gatePos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (action) {
                case BEGIN -> begin(player);
                case ADD -> add(player);
                case FINISH -> finish(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void begin(ServerPlayer player) {
        if (!isCloseEnough(player, startPos)) return;
        if (!(player.level().getBlockEntity(startPos) instanceof RaceStartBlockEntity be)) return;
        MARKING_SESSIONS.put(player.getUUID(), new MarkingSession(startPos));
        be.beginMarking(player);
    }

    private void add(ServerPlayer player) {
        MarkingSession session = MARKING_SESSIONS.get(player.getUUID());
        if (session == null || !session.startPos.equals(startPos)) return;
        if (!isCloseEnough(player, gatePos)) return;
        if (!(player.level().getBlockEntity(gatePos) instanceof RaceGateBlockEntity gate)) return;
        if (!session.route.isEmpty() && session.route.get(session.route.size() - 1).equals(gatePos)) return;
        if (!session.route.isEmpty()
            && player.level().getBlockEntity(session.route.get(session.route.size() - 1)) instanceof RaceGateBlockEntity prev) {
            prev.setNextRoutePos(gatePos);
        }
        session.route.add(gatePos);
        gate.setRouteIndex(session.route.size());
        gate.clearNextRoutePos();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_add", session.route.size()));
    }

    private void finish(ServerPlayer player) {
        if (!isCloseEnough(player, gatePos)) return;
        MarkingSession session = MARKING_SESSIONS.remove(player.getUUID());
        if (session == null || !session.startPos.equals(startPos)) return;
        if (!gatePos.equals(startPos) && !player.level().getBlockState(gatePos).is(ModBlocks.RACE_FINISH.get())) return;
        player.serverLevel().getChunkAt(startPos);
        if (player.level().getBlockEntity(startPos) instanceof RaceStartBlockEntity be) {
            be.finishMarking(player, session.route, gatePos);
        }
    }

    private static boolean isCloseEnough(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= INTERACTION_DISTANCE_SQR;
    }
}
