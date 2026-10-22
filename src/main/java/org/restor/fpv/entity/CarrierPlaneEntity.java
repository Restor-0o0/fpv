package org.restor.fpv.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.assembly.ModularDroneItem;
import org.restor.fpv.item.DroneItem;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.menu.CarrierMenu;
import org.restor.fpv.warhead.WarheadItem;
import org.restor.fpv.warhead.WarheadType;

// Big twin-boom mothership. Its hardpoints take either FPV strike drones or warheads, and
// the same weight budget covers both: one heavy warhead, or several light strike drones.
public class CarrierPlaneEntity extends PlaneEntity implements MenuProvider {
    public static final int HARDPOINTS = 6;
    public static final float MAX_PAYLOAD_MASS = 16f;
    private static final float DRONE_MASS = 2.4f;
    private static final int RELEASE_COOLDOWN = 30;

    private static final EntityDataAccessor<Float> DATA_LOAD =
        SynchedEntityData.defineId(CarrierPlaneEntity.class, EntityDataSerializers.FLOAT);

    private final SimpleContainer hardpoints = new SimpleContainer(HARDPOINTS);
    private int releaseCooldown;

    public CarrierPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 2.0f;
        this.maxThrust = 480f;
        this.stallSpeed = 3.6f;
        this.liftCoeff = 1.4f;
        this.maxLiftAccel = GRAVITY * 1.35f;
        this.dryMass = 58f;
        this.hardpoints.addListener(c -> refreshLoad());
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOAD, 0f);
    }

    public static float massOf(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        if (stack.getItem() instanceof WarheadItem w) return w.getType().mass() * stack.getCount();
        if (stack.is(ModItems.DROP_CHARGE.get())) return 1.2f * stack.getCount();
        if (stack.getItem() instanceof DroneItem || stack.getItem() instanceof ModularDroneItem) {
            return DRONE_MASS * stack.getCount();
        }
        return 0.5f * stack.getCount();
    }

    public static boolean isValidPayload(ItemStack stack) {
        return stack.getItem() instanceof WarheadItem
            || stack.getItem() instanceof DroneItem
            || stack.getItem() instanceof ModularDroneItem
            || stack.is(ModItems.DROP_CHARGE.get());
    }

    public SimpleContainer hardpoints() { return hardpoints; }

    public float loadedMass() {
        float sum = 0f;
        for (int i = 0; i < HARDPOINTS; i++) sum += massOf(hardpoints.getItem(i));
        return sum;
    }

    private void refreshLoad() {
        if (!level().isClientSide) entityData.set(DATA_LOAD, Math.min(loadedMass(), MAX_PAYLOAD_MASS * 1.5f));
    }

    @Override
    protected float payloadMass() {
        return entityData.get(DATA_LOAD);
    }

    /** True while another hardpoint can still take `extra` kilograms. */
    public boolean fitsWeight(ItemStack incoming, int slot) {
        float without = loadedMass() - massOf(hardpoints.getItem(slot));
        return without + massOf(incoming) <= MAX_PAYLOAD_MASS + 0.001f;
    }

    @Override
    public float getModelScale() { return 1.6f; }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp) openHardpoints(sp);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.interact(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && releaseCooldown > 0) releaseCooldown--;
    }

    public boolean canRelease() {
        return releaseCooldown <= 0 && nextLoadedSlot() >= 0;
    }

    private int nextLoadedSlot() {
        for (int i = 0; i < HARDPOINTS; i++) {
            if (!hardpoints.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Releases one hardpoint at the given ground point; returns what it sent, or null. */
    @Nullable
    public String releaseAt(Vec3 target) {
        if (!(level() instanceof ServerLevel sl) || !canRelease()) return null;
        int slot = nextLoadedSlot();
        ItemStack stack = hardpoints.getItem(slot);
        Vec3 drop = position().subtract(0, 1.2, 0);
        String label;

        if (stack.getItem() instanceof WarheadItem w) {
            KamikazeWingEntity bomb = ModEntities.PLANE_KAMIKAZE.get().create(sl);
            if (bomb == null) return null;
            bomb.setWarhead(w.getType());
            bomb.beginAttackRun(target, drop);
            sl.addFreshEntity(bomb);
            label = "warhead";
        } else {
            KamikazeDroneEntity fpv = ModEntities.KAMIKAZE.get().create(sl);
            if (fpv == null) return null;
            fpv.setPos(drop.x, drop.y, drop.z);
            fpv.setWarhead(pickWarhead(stack));
            sl.addFreshEntity(fpv);
            fpv.beginAttackRun(target);
            label = "drone";
        }

        stack.shrink(1);
        hardpoints.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        hardpoints.setChanged();
        releaseCooldown = RELEASE_COOLDOWN;
        sl.playSound(null, getX(), getY(), getZ(), org.restor.fpv.sound.ModSounds.PAYLOAD_RELEASE.get(), SoundSource.NEUTRAL, 1.2f, 0.7f);
        return label;
    }

    private WarheadType pickWarhead(ItemStack stack) {
        if (stack.is(ModItems.DROP_CHARGE.get())) return WarheadType.FRAG;
        if (stack.is(ModItems.MODULAR_DRONE.get())) return WarheadType.SHAPED;
        return WarheadType.FRAG;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            Containers.dropContents(level(), blockPosition(), hardpoints);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hardpoints.fromTag(tag.getList("Hardpoints", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
        refreshLoad();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Hardpoints", hardpoints.createTag(registryAccess()));
    }

    @Override
    protected Item getDropItem() {
        return ModItems.PLANE_CARRIER.get();
    }

    public void openHardpoints(ServerPlayer player) {
player.openMenu(this, buf -> buf.writeInt(getId()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.fpv.plane_carrier");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CarrierMenu(id, inv, this);
    }
}
