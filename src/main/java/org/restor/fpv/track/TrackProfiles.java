package org.restor.fpv.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrackProfiles extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<Map.Entry<ResourceLocation, TrackProfile>> profiles = List.of();

    public TrackProfiles() {
        super(GSON, "track_profiles");
    }

    public static TrackProfile select(long seed) {
        List<Map.Entry<ResourceLocation, TrackProfile>> current = profiles;
        if (current.isEmpty()) return null;
        return current.get(Math.floorMod(mix(seed), current.size())).getValue();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, TrackProfile> loaded = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
            .forEach(entry -> {
                try {
                    loaded.put(entry.getKey(), TrackProfile.fromJson(entry.getValue().getAsJsonObject()));
                } catch (RuntimeException e) {
                    LOGGER.error("Ignoring invalid track profile {}: {}", entry.getKey(), message(e));
                }
            });
        profiles = List.copyOf(loaded.entrySet());
        LOGGER.info("Loaded {} FPV track profile(s)", profiles.size());
    }

    private static String message(RuntimeException exception) {
        if (exception instanceof JsonParseException && exception.getMessage() != null) return exception.getMessage();
        return exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
    }

    private static int mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdl;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53l;
        return (int) (value ^ (value >>> 33));
    }
}
