package org.restor.fpv.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

// Base for station-controlled drones: no riding — right-click opens the link-code
// screen instead. Flown remotely via the control station (matching 6-digit code).
public abstract class RemoteDroneEntity extends DroneEntity {
    protected RemoteDroneEntity(EntityType<? extends DroneEntity> type, Level level, DroneData data) {
        super(type, level, data);
    }

    @Override
    public boolean isRemoteControllable() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
            org.restor.fpv.client.ui.screen.ClientScreens.openSetCode(getId(), getLinkCode());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity entity) { return false; }
}
