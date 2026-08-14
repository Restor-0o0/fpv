package org.restor.fpv.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

// Visible stand-in for the pilot: rendered with the pilot's own player skin. The
// real player body is projected along with the drone (that's what streams chunks),
// so this dummy stands where the pilot entered/connected for the whole session.
public class OperatorDummyEntity extends Entity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_PILOT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(OperatorDummyEntity.class,
            net.minecraft.network.syncher.EntityDataSerializers.STRING);

    private UUID vehicleUuid;
    private int checkTimer;

    public OperatorDummyEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void bindTo(Entity vehicle, UUID pilot) {
        this.vehicleUuid = vehicle.getUUID();
        entityData.set(DATA_PILOT, pilot.toString());
    }

    /** Pilot UUID for skin lookup on the client; null until synced. */
    @org.jetbrains.annotations.Nullable
    public UUID getPilotId() {
        try {
            String s = entityData.get(DATA_PILOT);
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Self-cleanup: only exists while its vehicle has a pilot (rider or remote).
        if (!level().isClientSide && ++checkTimer >= 20) {
            checkTimer = 0;
            if (!(level() instanceof ServerLevel sl) || vehicleUuid == null
                || !(sl.getEntity(vehicleUuid) instanceof Pilotable p) || !p.hasAnyPilot()) {
                discard();
            }
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_PILOT, "");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) discard();
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }
}
