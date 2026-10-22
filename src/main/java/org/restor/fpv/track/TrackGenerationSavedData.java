package org.restor.fpv.track;

import java.util.List;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class TrackGenerationSavedData extends SavedData {
    private static final String NAME = "fpv_track_generation";

    private final Set<Long> attemptedRegions = new HashSet<>();

    private static final SavedDataType<TrackGenerationSavedData> TYPE = new SavedDataType<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(org.restor.fpv.Fpv.MODID, NAME), TrackGenerationSavedData::new,
        Codec.LONG.listOf().fieldOf("AttemptedRegions").codec().xmap(TrackGenerationSavedData::load, TrackGenerationSavedData::save));

    public static TrackGenerationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean markAttempted(long region) {
        if (!attemptedRegions.add(region)) return false;
        setDirty();
        return true;
    }

    public boolean wasAttempted(long region) {
        return attemptedRegions.contains(region);
    }

    private static TrackGenerationSavedData load(List<Long> regions) {
        TrackGenerationSavedData data = new TrackGenerationSavedData();
        data.attemptedRegions.addAll(regions);
        return data;
    }

    private List<Long> save() {
        return attemptedRegions.stream().sorted().toList();
    }
}
