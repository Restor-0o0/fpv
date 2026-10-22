package org.restor.fpv.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.restor.fpv.Fpv;

public class ModDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, Fpv.MODID);

    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG = EntityDataSerializer.forValueType(ByteBufCodecs.COMPOUND_TAG);

    static {
        SERIALIZERS.register("compound_tag", () -> COMPOUND_TAG);
    }
}
