package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.antidrone.AntiDronePolicy;
import org.restor.fpv.antidrone.RadioInterferenceReceiver;

import java.util.UUID;

abstract class OwnedAntiDroneBlockEntity extends BlockEntity {
    private UUID ownerId;
    private String ownerTeam = "";

    protected OwnedAntiDroneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setOwner(Player player) {
        ownerId = player.getUUID();
        ownerTeam = teamName(player);
        setChanged();
    }

    protected boolean canTarget(ServerLevel level, RadioInterferenceReceiver craft) {
        ServerPlayer deviceOwner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        ServerPlayer operator = craft.radioOperator();
        UUID targetOwnerId = craft.radioOwnerId();
        ServerPlayer targetOwner = targetOwnerId == null
            ? null : level.getServer().getPlayerList().getPlayer(targetOwnerId);
        if (targetOwnerId == null && operator != null) targetOwnerId = operator.getUUID();

        String sourceTeam = deviceOwner == null ? ownerTeam : teamName(deviceOwner);
        String targetTeam = operator != null ? teamName(operator) : teamName(targetOwner);
        boolean privileged = isPrivileged(operator) || isPrivileged(targetOwner);
        return AntiDronePolicy.canTarget(
            new AntiDronePolicy.Owner(ownerId, sourceTeam, false),
            new AntiDronePolicy.Owner(targetOwnerId, targetTeam, privileged));
    }

    private static String teamName(Player player) {
        return player == null || player.getTeam() == null ? "" : player.getTeam().getName();
    }

    private static boolean isPrivileged(ServerPlayer player) {
        return player != null && (player.isCreative() || player.hasPermissions(2));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerTeam = tag.getString("OwnerTeam");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (!ownerTeam.isBlank()) tag.putString("OwnerTeam", ownerTeam);
    }
}
