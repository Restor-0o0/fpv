package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.entity.ControlStationBlockEntity;

// Adds or removes a saved link code on a control station block.
public record StationCodesPacket(BlockPos pos, String code, boolean add) implements CustomPacketPayload {
    public static final Type<StationCodesPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "station_codes"));

    public static final StreamCodec<FriendlyByteBuf, StationCodesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeBlockPos(pkt.pos);
            buf.writeUtf(pkt.code, 6);
            buf.writeBoolean(pkt.add);
        },
        buf -> new StationCodesPacket(buf.readBlockPos(), buf.readUtf(6), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StationCodesPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!pkt.code().matches("\\d{6}")) return;
            BlockPos pos = pkt.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof ControlStationBlockEntity be) {
                if (pkt.add()) be.addCode(pkt.code());
                else be.removeCode(pkt.code());
            }
        });
    }
}
