package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.item.DroneItem;

import java.util.UUID;

// Kamikaze launch station: use → pulls an armed drone out of an adjacent container and
// puts the player in control. When it detonates, the next one is drawn from the same
// magazine — the loop runs until the pilot sneaks out or the container runs dry.
public class KamikazeStationBlockEntity extends BlockEntity {
    private UUID pilotId;
    private UUID droneId;
    private int cooldown;

    public KamikazeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KAMIKAZE_STATION.get(), pos, state);
    }

    public void startSession(ServerPlayer player) {
        if (pilotId != null && !player.getUUID().equals(pilotId)) return; // occupied
        if (!org.restor.fpv.item.RcLink.hasGoggles(player)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.need_goggles"), true);
            return;
        }
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
        if (e instanceof DroneEntity drone && drone.isAlive()) {
            // Pilot pressed sneak (session ended, drone survived) → stop the loop.
            if (!drone.hasPassenger(pilot)) reset();
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
        Container magazine = null;
        int slot = -1;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            Container c = HopperBlockEntity.getContainerAt(sl, worldPosition.relative(dir));
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (DroneItem.isArmed(c.getItem(i))) { magazine = c; slot = i; break; }
            }
            if (magazine != null) break;
        }
        if (magazine == null) {
            pilot.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.fpv.station_empty"), true);
            reset();
            return;
        }

        ItemStack stack = magazine.getItem(slot);
        DroneEntity drone = ((DroneItem) stack.getItem()).spawn(sl,
            new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5),
            pilot.getYRot(), stack, pilot);
        if (drone == null) { reset(); return; }
        if (!drone.beginRidingControl(pilot)) {
            drone.discard();
            reset();
            return;
        }
        magazine.removeItem(slot, 1);
        magazine.setChanged();
        droneId = drone.getUUID();
        cooldown = 30; // ~1.5s between waves
    }

    private void reset() {
        pilotId = null;
        droneId = null;
        cooldown = 0;
    }
}
