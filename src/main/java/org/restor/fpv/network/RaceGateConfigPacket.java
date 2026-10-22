package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.entity.RaceGateBlockEntity;

// Client -> server config for a race gate.
public record RaceGateConfigPacket(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SET_SIZE = 0;
    public static final int CYCLE_COLOR = 1;
    public static final int ADJUST_YAW = 2;
    public static final int ADJUST_PITCH = 3;
    public static final int SET_OFFSET = 4;
    public static final int FLIP_NORMAL = 5;

    public static final Type<RaceGateConfigPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "race_gate_config"));

    public static final StreamCodec<FriendlyByteBuf, RaceGateConfigPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeBlockPos(pkt.pos);
            buf.writeInt(pkt.action);
            buf.writeInt(pkt.value);
        },
        buf -> new RaceGateConfigPacket(buf.readBlockPos(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaceGateConfigPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = pkt.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof RaceGateBlockEntity be) {
                switch (pkt.action()) {
                    case SET_SIZE -> be.setSize(pkt.value());
                    case CYCLE_COLOR -> be.cycleColor();
                    case ADJUST_YAW -> be.adjustYaw(pkt.value());
                    case ADJUST_PITCH -> be.adjustPitch(pkt.value());
                    case SET_OFFSET -> be.setOffset(pkt.value());
                    case FLIP_NORMAL -> be.flipNormal();
                }
            }
        });
    }
}
