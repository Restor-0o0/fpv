package org.restor.fpv.client.ui.screen;

import net.minecraft.client.Minecraft;

// Client-only entry points so common code (blocks/entities) can open screens
// without directly loading client classes on a dedicated server.
public class ClientScreens {
    public static void openSetCode(int droneId, String currentCode) {
        Minecraft.getInstance().setScreen(new DroneCodeScreen(droneId, currentCode));
    }

    public static void openStation(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new StationScreen(pos));
    }

    public static void openRaceStart(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new RaceStartScreen(pos));
    }

    public static void openRaceGate(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new RaceGateScreen(pos));
    }
}
