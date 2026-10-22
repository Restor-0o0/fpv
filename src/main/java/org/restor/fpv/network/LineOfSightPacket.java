package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;

/** Tells the client which aircraft it is flying by eye; -1 ends the session. */
public record LineOfSightPacket(int entityId) implements CustomPacketPayload {
    public static final Type<LineOfSightPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "line_of_sight"));

    public static final StreamCodec<FriendlyByteBuf, LineOfSightPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeInt(pkt.entityId),
        buf -> new LineOfSightPacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LineOfSightPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> org.restor.fpv.client.LineOfSightSession.set(pkt.entityId()));
    }
}
