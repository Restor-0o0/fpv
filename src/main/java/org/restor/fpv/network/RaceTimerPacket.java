package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client race HUD state for the piloting player. lap == 0 means "not racing"
// (clear the HUD). finishTicks >= 0 means the run just finished (show the final time).
public class RaceTimerPacket {
    private final int lap;
    private final int laps;
    private final long startTime;
    private final int gatesHit;
    private final int gatesTotal;
    private final int finishTicks;

    public RaceTimerPacket(int lap, int laps, long startTime, int gatesHit, int gatesTotal, int finishTicks) {
        this.lap = lap;
        this.laps = laps;
        this.startTime = startTime;
        this.gatesHit = gatesHit;
        this.gatesTotal = gatesTotal;
        this.finishTicks = finishTicks;
    }

    public RaceTimerPacket(FriendlyByteBuf buf) {
        this.lap = buf.readInt();
        this.laps = buf.readInt();
        this.startTime = buf.readLong();
        this.gatesHit = buf.readInt();
        this.gatesTotal = buf.readInt();
        this.finishTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(lap);
        buf.writeInt(laps);
        buf.writeLong(startTime);
        buf.writeInt(gatesHit);
        buf.writeInt(gatesTotal);
        buf.writeInt(finishTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.restor.fpv.render.FPVOverlay.setRaceState(
                lap, laps, startTime, gatesHit, gatesTotal, finishTicks)));
        ctx.get().setPacketHandled(true);
    }
}
