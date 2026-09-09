package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.compat.CustomData;
import net.minecraft.world.level.Level;
import org.restor.fpv.component.ModuleType;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.entity.WarheadDetonation;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.ContactFuze;
import org.restor.fpv.warhead.ProximityFuze;
import org.restor.fpv.warhead.WarheadType;

/**
 * The player-built airframe: same bench, same parts, but a fixed-wing frame, so the assembly
 * drives the aerodynamic model in PlaneEntity instead of a multirotor flight controller.
 */
public class ModularPlaneEntity extends PlaneEntity {
    private static final EntityDataAccessor<CompoundTag> DATA_ASSEMBLY =
        SynchedEntityData.defineId(ModularPlaneEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // A bench assembly weighs 1-4 kg and pushes 15-40 N; the airframe physics works in
    // full-size aircraft numbers, so both are lifted onto that scale here.
    private static final float MASS_SCALE = 20f;
    private static final float THRUST_SCALE = 15.5f;

    private DroneAssembly assembly = new DroneAssembly();
    private boolean flightReady;
    private final ContactFuze fuze = new ContactFuze();
    private final ProximityFuze proximityFuze = new ProximityFuze();
    private WarheadType warhead = WarheadType.HE;
    private static final int RELEASE_COOLDOWN = 40;
    private int releaseCooldown;
    private boolean exploded;
    private final SimpleContainer cargo = new SimpleContainer(9);

    public ModularPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ASSEMBLY, new CompoundTag());
    }

    public DroneAssembly getAssembly() {
        return assembly;
    }

    public void setAssembly(DroneAssembly a) {
        this.assembly = a;
        entityData.set(DATA_ASSEMBLY, a.save());
        applyAssembly();
    }

    private void applyAssembly() {
        flightReady = assembly.isComplete();
        FrameTuning t = assembly.tuning();
        dryMass = Math.max(4f, assembly.totalMassKg() * MASS_SCALE);
        maxThrust = Math.max(40f, assembly.totalThrustN() * THRUST_SCALE);
        wingArea = t.wingArea() > 0f ? t.wingArea() : 6f;
        liftCoeff = t.liftCoeff() > 0f ? t.liftCoeff() : 0.12f;
        stallSpeed = t.stallSpeed() > 0f ? t.stallSpeed() : 6f;
        maxSpeedBt = (t.maxSpeed() > 0f ? t.maxSpeed() : 80f) / 20f;
        maxRollRate = 180f * t.agility();
        maxPitchRate = 95f * t.agility();
        maxYawRate = 55f * t.agility();
        rotationResponsiveness = 9f * t.responsiveness();
        dragCoeff = 0.045f * t.dragFactor();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ASSEMBLY.equals(key) && level().isClientSide) {
            assembly = DroneAssembly.load(entityData.get(DATA_ASSEMBLY));
            applyAssembly();
        }
    }

    @Override
    public boolean isFlightReady() {
        return flightReady;
    }

    private boolean hasWarhead() {
        return assembly.moduleType() == ModuleType.WARHEAD;
    }

    public WarheadType getWarhead() {
        return warhead;
    }

    public void setWarhead(WarheadType type) {
        if (type != null) warhead = type;
    }

    public float warheadCapacityKg() {
        return assembly.tuning().liftCapacityKg();
    }

    @Override
    protected float payloadMass() {
        return hasWarhead() ? warhead.mass() * MASS_SCALE : 0f;
    }

    // ------------------------------------------------------------ strike bay

    public boolean hasStrikeBay() {
        return assembly.bayKind() != DroneAssembly.BayKind.NONE;
    }

    public boolean canRelease() {
        return releaseCooldown <= 0 && nextBaySlot() >= 0;
    }

    private int nextBaySlot() {
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            if (!assembly.bay[i].isEmpty()) return i;
        }
        return -1;
    }

    /** Sends the next stowed aircraft at the point. Returns what was launched, or null. */
    public String releaseAt(net.minecraft.world.phys.Vec3 target) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl) || !canRelease()) return null;
        int slot = nextBaySlot();
        ItemStack card = assembly.bay[slot];
        DroneAssembly build = DroneCardItem.assembly(card);
        if (build == null) return null;
        WarheadType head = DroneCardItem.warhead(card);

        String label;
        if (DroneAssembly.isPlaneFrame(build.frame)) {
            // A wing needs run-up room, so it appears high and far out on the approach line.
            net.minecraft.world.phys.Vec3 toTarget = target.subtract(position());
            net.minecraft.world.phys.Vec3 approach =
                new net.minecraft.world.phys.Vec3(toTarget.x, 0, toTarget.z);
            if (approach.lengthSqr() < 1.0e-4) {
                var look = getLookAngle();
                approach = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
            }
            if (approach.lengthSqr() < 1.0e-4) approach = new net.minecraft.world.phys.Vec3(0, 0, 1);
            approach = approach.normalize();
            net.minecraft.world.phys.Vec3 spawn = target.subtract(approach.scale(180.0)).add(0, 180.0, 0);
            var wing = org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE.get().create(sl);
            if (wing == null) return null;
            if (head != null) wing.setWarhead(head);
            wing.beginAttackRun(target, spawn);
            sl.addFreshEntity(wing);
            label = "wing";
        } else {
            net.minecraft.world.phys.Vec3 drop = position().subtract(0, 1.4, 0);
            var fpv = org.restor.fpv.entity.ModEntities.KAMIKAZE.get().create(sl);
            if (fpv == null) return null;
            fpv.setPos(drop.x, drop.y, drop.z);
            if (head != null) fpv.setWarhead(head);
            sl.addFreshEntity(fpv);
            fpv.beginAttackRun(target);
            label = "drone";
        }

        card.shrink(1);
        if (card.isEmpty()) assembly.bay[slot] = ItemStack.EMPTY;
        entityData.set(DATA_ASSEMBLY, assembly.save());
        releaseCooldown = RELEASE_COOLDOWN;
        sl.playSound(null, getX(), getY(), getZ(), org.restor.fpv.sound.ModSounds.PAYLOAD_RELEASE.get(),
            net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 0.7f);
        return label;
    }

    @Override
    public void tick() {
        super.tick();
        if (releaseCooldown > 0) releaseCooldown--;
        if (level().isClientSide || !hasWarhead()) return;

        boolean powered = isArmed();
        if (warhead.proximity()
            && proximityFuze.tick(this, powered, ProximityFuze.TRIGGER_RADIUS, this::isProximityTarget)) {
            explodeWarhead();
            return;
        }
        if (fuze.tick(this, powered)) {
            explodeWarhead();
            return;
        }
        if (fuze.isArmed()) {
            var hits = level().getEntities(this, getBoundingBox().inflate(0.2),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && !hasPassenger(e));
            if (!hits.isEmpty()) explodeWarhead();
        }
    }

    private boolean isProximityTarget(net.minecraft.world.entity.Entity e) {
        if (e == this || hasPassenger(e) || e.isSpectator()) return false;
        if (e instanceof Player p && p.isCreative()) return false;
        return e instanceof net.minecraft.world.entity.LivingEntity
            || e instanceof org.restor.fpv.entity.DroneEntity
            || e instanceof PlaneEntity;
    }

    private void explodeWarhead() {
        if (exploded) return;
        exploded = true;
        if (!getPassengers().isEmpty()) ejectPassengers();
        WarheadDetonation.detonate(level(), this, position(), warhead);
        discard();
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (hasWarhead() && !level().isClientSide
            && (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE))) {
            explodeWarhead();
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (org.restor.fpv.warhead.WarheadLoading.isWarhead(held)) {
            if (!level().isClientSide) {
                if (!hasWarhead()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.fpv.warhead_no_module"), true);
                } else {
                    org.restor.fpv.warhead.WarheadLoading.swap(player, held, warhead,
                        warheadCapacityKg(), this::setWarhead);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (DroneCardItem.isCard(held)) {
            if (!level().isClientSide) loadCard(player, held);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() && held.isEmpty() && hasStrikeBay()) {
            if (!level().isClientSide) unloadCard(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() && assembly.moduleType() == ModuleType.STORAGE) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inv, cargo, 1),
                    getName()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!flightReady) {
            if (!level().isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.fpv.airframe_incomplete"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    private void loadCard(Player player, ItemStack held) {
        if (!hasStrikeBay()) {
            msg(player, "msg.fpv.bay_none");
            return;
        }
        if (!assembly.bayAccepts(held)) {
            msg(player, "msg.fpv.bay_rejects");
            return;
        }
        int slot = -1;
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            if (assembly.bay[i].isEmpty()) { slot = i; break; }
        }
        if (slot < 0) {
            msg(player, "msg.fpv.bay_full");
            return;
        }
        assembly.bay[slot] = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) held.shrink(1);
        setAssembly(assembly);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg.fpv.bay_loaded", assembly.bayCount(), DroneAssembly.BAY_SIZE), true);
    }

    private void unloadCard(Player player) {
        int slot = -1;
        for (int i = DroneAssembly.BAY_SIZE - 1; i >= 0; i--) {
            if (!assembly.bay[i].isEmpty()) { slot = i; break; }
        }
        if (slot < 0) {
            msg(player, "msg.fpv.bay_empty");
            return;
        }
        ItemStack card = assembly.bay[slot];
        assembly.bay[slot] = ItemStack.EMPTY;
        setAssembly(assembly);
        org.restor.fpv.util.CraftPickup.giveTo(player, card);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "msg.fpv.bay_loaded", assembly.bayCount(), DroneAssembly.BAY_SIZE), true);
    }

    private static void msg(Player player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }

    @Override
    public boolean mountPilot(net.minecraft.server.level.ServerPlayer player) {
        return flightReady && super.mountPilot(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !cargo.isEmpty()) {
            net.minecraft.world.Containers.dropContents(level(), blockPosition(), cargo);
        }
        super.remove(reason);
    }

    @Override
    public float getModelScale() {
        FrameSpec spec = assembly.frameSpec();
        if (spec == null) return super.getModelScale();
        return switch (spec.role()) {
            case CARRIER -> 1.6f;
            case LOITER -> 1.35f;
            case SPOTTER -> 1.25f;
            default -> super.getModelScale();
        };
    }

    @Override
    public Item getModelItem() {
        if (!assembly.frame.isEmpty()) return assembly.frame.getItem();
        return ModItems.PLANE.get();
    }

    @Override
    protected Item getDropItem() {
        return ModItems.MODULAR_PLANE.get();
    }

    @Override
    protected ItemStack getDropStack() {
        if (assembly.isBareFrame()) return assembly.frame.copy();
        ItemStack stack = super.getDropStack();
        CustomData.update(stack, t -> {
            t.put("Assembly", assembly.save());
            if (hasWarhead()) t.putString("Warhead", warhead.id());
        });
        return stack;
    }

    public void readFromItemTag(CompoundTag tag) {
        if (tag.contains("Assembly")) setAssembly(DroneAssembly.load(tag.getCompound("Assembly")));
        if (tag.contains("Warhead")) warhead = WarheadType.byId(tag.getString("Warhead"));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Assembly")) setAssembly(DroneAssembly.load(tag.getCompound("Assembly")));
        if (tag.contains("Warhead")) warhead = WarheadType.byId(tag.getString("Warhead"));
        if (tag.contains("Cargo")) {
            cargo.fromTag(tag.getList("Cargo", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Assembly", assembly.save());
        tag.putString("Warhead", warhead.id());
        tag.put("Cargo", cargo.createTag());
    }
}
