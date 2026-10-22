package org.restor.fpv.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class TrackGenerationSavedData extends SavedData {
    private static final String NAME = "fpv_track_generation";


    private final Set<Long> attemptedRegions = new HashSet<>();

    public static TrackGenerationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrackGenerationSavedData::new, TrackGenerationSavedData::load, null), NAME);
    }

    public boolean markAttempted(long region) {
        if (!attemptedRegions.add(region)) return false;
        setDirty();
        return true;
    }

    public boolean wasAttempted(long region) {
        return attemptedRegions.contains(region);
    }

    private static TrackGenerationSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        TrackGenerationSavedData data = new TrackGenerationSavedData();
        ListTag regions = tag.getList("AttemptedRegions", Tag.TAG_LONG);
        for (int i = 0; i < regions.size(); i++) data.attemptedRegions.add(((LongTag) regions.get(i)).getAsLong());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag regions = new ListTag();
        attemptedRegions.stream().sorted().forEach(region -> regions.add(LongTag.valueOf(region)));
        tag.put("AttemptedRegions", regions);
        return tag;
    }
}
