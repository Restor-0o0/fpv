package org.restor.fpv.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.menu.CargoBayMenu;

// Twin-boom hauler: steady and slow, with plenty of lift for the weight it carries. The
// bay between the booms is a real 15-slot hold, and everything in it is dead weight.
public class CargoPlaneEntity extends PlaneEntity implements MenuProvider {
    public static final int BAY_SLOTS = 15;
    public static final float MASS_PER_ITEM = 0.5f;

    private static final EntityDataAccessor<Float> DATA_CARGO =
        SynchedEntityData.defineId(CargoPlaneEntity.class, EntityDataSerializers.FLOAT);

    private final SimpleContainer bay = new SimpleContainer(BAY_SLOTS);

    public CargoPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 2.2f;
        this.maxThrust = 320f;
        this.stallSpeed = 3.2f;
        this.liftCoeff = 1.4f;
        this.maxLiftAccel = GRAVITY * 1.4f;
        this.dryMass = 34f;
        this.bay.addListener(c -> refreshCargo());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CARGO, 0f);
    }

    public SimpleContainer bay() { return bay; }

    public float cargoMass() {
        float sum = 0f;
        for (int i = 0; i < BAY_SLOTS; i++) sum += bay.getItem(i).getCount() * MASS_PER_ITEM;
        return sum;
    }

    private void refreshCargo() {
        if (!level().isClientSide) entityData.set(DATA_CARGO, cargoMass());
    }

    @Override
    protected float payloadMass() {
        return entityData.get(DATA_CARGO);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp) {
net.minecraftforge.network.NetworkHooks.openScreen(sp, this, buf -> buf.writeInt(getId()));
            }
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            Containers.dropContents(level(), blockPosition(), bay);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bay.fromTag(tag.getList("Cargo", net.minecraft.nbt.Tag.TAG_COMPOUND));
        refreshCargo();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Cargo", bay.createTag());
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_CARGO.get();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.fpv.plane_cargo");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CargoBayMenu(id, inv, this);
    }

    public static boolean isValidCargo(ItemStack stack) {
        return !stack.isEmpty();
    }
}
