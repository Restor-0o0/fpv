package org.restor.fpv.client;

// Pushed from the server via RaceTimerPacket. lap == 0 = not racing;
// finishTicks >= 0 = a run just finished (show the final time briefly).
public final class ClientRaceState {
    static int lap, laps, gatesHit, gatesTotal;
    static long startTime;
    static int finishTicks = -1;
    static long finishAtMs;

    private ClientRaceState() {}

    public static void set(int lap, int laps, long startTime, int gatesHit, int gatesTotal, int finishTicks) {
        ClientRaceState.lap = lap;
        ClientRaceState.laps = laps;
        ClientRaceState.startTime = startTime;
        ClientRaceState.gatesHit = gatesHit;
        ClientRaceState.gatesTotal = gatesTotal;
        ClientRaceState.finishTicks = finishTicks;
        if (finishTicks >= 0) finishAtMs = System.currentTimeMillis();
    }

    public static boolean active() { return lap > 0; }
    public static int gatesHit() { return lap > 0 ? gatesHit : 0; }
    public static int lap() { return lap; }
    public static int laps() { return laps; }
    public static int gatesTotal() { return gatesTotal; }
    public static long startTime() { return startTime; }

    public static int finishTicks() {
        if (finishTicks >= 0 && System.currentTimeMillis() - finishAtMs > 5000) finishTicks = -1;
        return finishTicks;
    }
}
