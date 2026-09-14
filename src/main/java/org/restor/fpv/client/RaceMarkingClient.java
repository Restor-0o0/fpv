package org.restor.fpv.client;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.client.ui.screen.ClientScreens;
import org.restor.fpv.network.RaceMarkPacket;

// Client-side race-course marking session. While active, right-clicking gate blocks adds
// them to the route in order and right-clicking the start block finishes/saves it, instead
// of opening the config screens.
public class RaceMarkingClient {
    private static BlockPos activeStart;

    public static boolean isActive() { return activeStart != null; }

    // Called from the start-block config screen's "Mark route" button.
    public static void startMarking(BlockPos startPos) {
        activeStart = startPos;
        PacketDistributor.sendToServer(new RaceMarkPacket(startPos, RaceMarkPacket.BEGIN, startPos));
    }

    public static void onGateClicked(BlockPos gatePos) {
        if (activeStart != null) {
            PacketDistributor.sendToServer(new RaceMarkPacket(activeStart, RaceMarkPacket.ADD, gatePos));
        } else {
            ClientScreens.openRaceGate(gatePos);
        }
    }

    public static void onStartClicked(BlockPos startPos) {
        if (activeStart != null) {
            PacketDistributor.sendToServer(new RaceMarkPacket(activeStart, RaceMarkPacket.FINISH, startPos));
            activeStart = null;
        } else {
            ClientScreens.openRaceStart(startPos);
        }
    }

    public static void onFinishClicked(BlockPos finishPos) {
        if (activeStart != null) {
            PacketDistributor.sendToServer(new RaceMarkPacket(activeStart, RaceMarkPacket.FINISH, finishPos));
            activeStart = null;
        }
    }

    public static void cancel() { activeStart = null; }
}
