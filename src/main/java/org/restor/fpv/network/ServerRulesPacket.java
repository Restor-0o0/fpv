package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.restor.fpv.Fpv;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.codec.StreamCodec;

public record ServerRulesPacket(double[] values, double[] explosives) implements CustomPacketPayload {
    public static final Type<ServerRulesPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Fpv.MODID, "server_rules"));

    public static final StreamCodec<FriendlyByteBuf, ServerRulesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            write(buf, pkt.values);
            write(buf, pkt.explosives);
        },
        buf -> new ServerRulesPacket(read(buf), read(buf))
    );

    private static void write(FriendlyByteBuf buf, double[] values) {
        buf.writeVarInt(values.length);
        for (double v : values) buf.writeDouble(v);
    }

    private static double[] read(FriendlyByteBuf buf) {
        double[] values = new double[Math.min(buf.readVarInt(), 1024)];
        for (int i = 0; i < values.length; i++) values[i] = buf.readDouble();
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ServerRulesPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> org.restor.fpv.client.ClientRules.receive(pkt.values(), pkt.explosives()));
    }
}
