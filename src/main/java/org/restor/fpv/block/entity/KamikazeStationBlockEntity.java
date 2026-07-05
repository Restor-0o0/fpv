package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.entity.KamikazeDroneEntity;
import org.restor.fpv.entity.ModEntities;

import java.util.UUID;

// Kamikaze launch station: use → spawns a kamikaze drone and puts the player in
// control. When the drone detonates, the next one spawns automatically and control
// re-engages — an endless launch loop until the pilot exits with sneak.
public class KamikazeStationBlockEntity extends BlockEntity {
    private UUID pilotId;
    private UUID droneId;
    private int cooldown;

    public KamikazeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KAMIKAZE_STATION.get(), pos, state);
    }

    public void startSession(ServerPlayer player) {
        if (pilotId != null && !player.getUUID().equals(pilotId)) return; // occupied
        pilotId = player.getUUID();
        spawnAndControl((ServerLevel) level, player);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, KamikazeStationBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel sl) {
        if (pilotId == null) return;
        if (cooldown > 0) cooldown--;

        ServerPlayer pilot = sl.getServer().getPlayerList().getPlayer(pilotId);
        if (pilot == null || pilot.isRemoved()) {
            reset();
            return;
        }

        Entity e = droneId != null ? sl.getEntity(droneId) : null;
        if (e instanceof KamikazeDroneEntity drone && drone.isAlive()) {
            // Pilot pressed sneak (session ended, drone survived) → stop the loop.
            if (!drone.hasRemotePilot()) reset();
            return;
        }

        // Drone gone (detonated) — pilot was returned to the station; launch the next
        // one after a short delay, as long as they're still standing at the station.
        if (cooldown > 0) return;
        if (pilot.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > 36) {
            reset();
            return;
        }
        spawnAndControl(sl, pilot);
    }

    private void spawnAndControl(ServerLevel sl, ServerPlayer pilot) {
        KamikazeDroneEntity drone = ModEntities.KAMIKAZE.get().create(sl);
        if (drone == null) return;
        drone.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5);
        drone.setYRot(pilot.getYRot());
        String def = org.restor.fpv.Config.DEFAULT_LINK_CODE.get();
        if (def.matches("\\d{6}")) drone.setLinkCode(def);
        sl.addFreshEntity(drone);
        drone.beginRemoteControl(pilot);
        droneId = drone.getUUID();
        cooldown = 30; // ~1.5s between waves
    }

    private void reset() {
        pilotId = null;
        droneId = null;
        cooldown = 0;
    }
}
