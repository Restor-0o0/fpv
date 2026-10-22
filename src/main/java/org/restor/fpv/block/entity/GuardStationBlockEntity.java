package org.restor.fpv.block.entity;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.DroneMode;
import org.restor.fpv.item.BatteryCharge;
import org.restor.fpv.item.DroneItem;
import org.restor.fpv.menu.GuardStationMenu;

import java.util.UUID;

/**
 * Home base for guard drones: launches whatever is docked in it, calls each one back when its
 * pack runs low, and sends it out again only once a neighbouring charger has topped it up.
 */
public class GuardStationBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 4;
    private static final int TICK_INTERVAL = 20;
    private static final double CHARGER_RADIUS = 6.0;
    private static final double DOCK_RADIUS_SQR = 12.0;
    private static final float LAUNCH_CHARGE = 0.85f;
    private static final float RECALL_CHARGE = 0.22f;
    private static final float CHARGE_RATE = TICK_INTERVAL / 700f;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final UUID[] launched = new UUID[SLOTS];
    private UUID ownerId;
    private int cooldown;

    public GuardStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUARD_STATION.get(), pos, state);
    }

    public void setOwner(Player player) {
        ownerId = player.getUUID();
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuardStationBlockEntity be) {
        if (++be.cooldown < TICK_INTERVAL) return;
        be.cooldown = 0;
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        boolean charger = hasCharger(level);
        for (int i = 0; i < SLOTS; i++) {
            Entity flying = launched[i] == null ? null : level.getEntity(launched[i]);
            if (flying instanceof DroneEntity drone && drone.isAlive()) {
                serviceFlight(i, drone);
                continue;
            }
            launched[i] = null;

            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            if (BatteryCharge.get(stack) < LAUNCH_CHARGE) {
                if (charger && BatteryCharge.add(stack, CHARGE_RATE)) setChanged();
                continue;
            }
            if (owner != null && owner.level() == level) launch(level, i, stack, owner);
        }
    }

    private void serviceFlight(int slot, DroneEntity drone) {
        boolean low = drone.batteryCharge() < RECALL_CHARGE;
        drone.setRecalled(low);
        if (!low) return;
        Vec3 pad = Vec3.atCenterOf(worldPosition.above());
        if (drone.distanceToSqr(pad) > DOCK_RADIUS_SQR) return;
        items.set(slot, drone.toItemStack());
        launched[slot] = null;
        drone.discard();
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, org.restor.fpv.sound.ModSounds.STATION_CHARGE.get(),
                SoundSource.BLOCKS, 0.4f, 1.2f);
        }
    }

    private void launch(ServerLevel level, int slot, ItemStack stack, ServerPlayer owner) {
        if (!(stack.getItem() instanceof DroneItem item)) return;
        Vec3 pad = Vec3.atCenterOf(worldPosition.above(2));
        DroneEntity drone = item.spawn(level, pad, owner.getYRot(), stack, owner);
        if (drone == null) return;
        drone.setAutoMode(DroneMode.PATROL, owner);
        drone.setGuardAnchor(worldPosition);
        drone.setRecalled(false);
        launched[slot] = drone.getUUID();
        items.set(slot, ItemStack.EMPTY);
        setChanged();
        level.playSound(null, worldPosition, org.restor.fpv.sound.ModSounds.NEST_DEPLOY.get(),
            SoundSource.BLOCKS, 0.7f, 1.4f);
    }

    private boolean hasCharger(Level level) {
        int r = (int) Math.ceil(CHARGER_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -2, -r), worldPosition.offset(r, 2, r))) {
            if (level.getBlockState(p).is(org.restor.fpv.block.ModBlocks.CHARGING_STATION.get())) return true;
        }
        return false;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        for (int i = 0; i < SLOTS; i++) launched[i] = null;
        for (ValueInput entry : input.childrenListOrEmpty("Flying")) {
            int slot = entry.getIntOr("Slot", 0);
            if (slot >= 0 && slot < SLOTS) launched[slot] = entry.read("Id", UUIDUtil.CODEC).orElse(null);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.storeNullable("Owner", UUIDUtil.CODEC, ownerId);
        var flying = output.childrenList("Flying");
        for (int i = 0; i < SLOTS; i++) {
            if (launched[i] == null) continue;
            ValueOutput entry = flying.addChild();
            entry.putInt("Slot", i);
            entry.store("Id", UUIDUtil.CODEC, launched[i]);
        }
    }

    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return ContainerHelper.removeItem(items, slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof DroneItem;
    }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64;
    }

    @Override public void clearContent() { items.clear(); }

    @Override public Component getDisplayName() { return Component.translatable("block.fpv.guard_station"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new GuardStationMenu(id, inv, this);
    }

    public void openFor(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }
}
