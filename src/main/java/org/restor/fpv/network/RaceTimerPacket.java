package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;

// Server -> client race HUD state for the piloting player. lap == 0 means "not racing"
// (clear the HUD). finishTicks >= 0 means the run just finished (show the final time).
public record RaceTimerPacket(int lap, int laps, long startTime, int gatesHit, int gatesTotal,
                               int finishTicks) implements CustomPacketPayload {
    public static final Type<RaceTimerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "race_timer"));

    public static final StreamCodec<FriendlyByteBuf, RaceTimerPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.lap);
            buf.writeInt(pkt.laps);
            buf.writeLong(pkt.startTime);
            buf.writeInt(pkt.gatesHit);
            buf.writeInt(pkt.gatesTotal);
            buf.writeInt(pkt.finishTicks);
        },
        buf -> new RaceTimerPacket(buf.readInt(), buf.readInt(), buf.readLong(), buf.readInt(),
            buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaceTimerPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> org.restor.fpv.client.ClientRaceState.set(
            pkt.lap(), pkt.laps(), pkt.startTime(), pkt.gatesHit(), pkt.gatesTotal(), pkt.finishTicks()));
    }
}
