package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Client -> server race-route marking. BEGIN clears and opens the route on a start block,
// ADD appends the given gate, FINISH saves it.
public record RaceMarkPacket(BlockPos startPos, int action, BlockPos gatePos) implements CustomPacketPayload {
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

    public static final Type<RaceMarkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "race_mark"));

    public static final StreamCodec<FriendlyByteBuf, RaceMarkPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeBlockPos(pkt.startPos);
            buf.writeInt(pkt.action);
            buf.writeBlockPos(pkt.gatePos);
        },
        buf -> new RaceMarkPacket(buf.readBlockPos(), buf.readInt(), buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaceMarkPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (pkt.action()) {
                case BEGIN -> begin(player, pkt.startPos());
                case ADD -> add(player, pkt.startPos(), pkt.gatePos());
                case FINISH -> finish(player, pkt.startPos(), pkt.gatePos());
            }
        });
    }

    private static void begin(ServerPlayer player, BlockPos startPos) {
        if (!isCloseEnough(player, startPos)) return;
        if (!(player.level().getBlockEntity(startPos) instanceof RaceStartBlockEntity be)) return;
        MARKING_SESSIONS.put(player.getUUID(), new MarkingSession(startPos));
        be.beginMarking(player);
    }

    private static void add(ServerPlayer player, BlockPos startPos, BlockPos gatePos) {
        MarkingSession session = MARKING_SESSIONS.get(player.getUUID());
        if (session == null || !session.startPos.equals(startPos)) return;
        if (!isCloseEnough(player, gatePos)) return;
        if (!(player.level().getBlockEntity(gatePos) instanceof RaceGateBlockEntity gate)) return;
        if (!session.route.isEmpty() && session.route.get(session.route.size() - 1).equals(gatePos)) return;
        if (!session.route.isEmpty()
            && player.level().getBlockEntity(session.route.get(session.route.size() - 1)) instanceof RaceGateBlockEntity prev) {
            prev.setNextRoutePos(gatePos);
        }
        gate.setPrevRoutePos(session.route.isEmpty() ? startPos : session.route.get(session.route.size() - 1));
        session.route.add(gatePos);
        gate.setRouteIndex(session.route.size());
        gate.clearNextRoutePos();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_add", session.route.size()));
    }

    private static void finish(ServerPlayer player, BlockPos startPos, BlockPos gatePos) {
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
