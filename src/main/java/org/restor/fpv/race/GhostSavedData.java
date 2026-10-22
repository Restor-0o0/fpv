package org.restor.fpv.race;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Per-dimension store of race ghost recordings, kept out of the RaceStartBlockEntity NBT so
// large telemetry never bloats the block entity (FPV_IDEAS.md §1 "Хранение"). Keyed by a
// course hash: reshaping the route or changing the lap count makes a new course.
public class GhostSavedData extends SavedData {
    public static final String NAME = "fpv_ghosts";

    // All recordings for one course.
    private static final class Course {
        GhostRecording bestOverall;
        final Map<UUID, GhostRecording> byPlayer = new HashMap<>();
    }

    private final Map<String, Course> courses = new HashMap<>();

    private static final SavedDataType<GhostSavedData> TYPE = new SavedDataType<>(NAME, GhostSavedData::new,
        CompoundTag.CODEC.xmap(GhostSavedData::load, GhostSavedData::save));

    public static GhostSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The personal best for this player on this course, or null. */
    public GhostRecording personalBest(String courseHash, UUID player) {
        Course c = courses.get(courseHash);
        return c == null ? null : c.byPlayer.get(player);
    }

    /** The best run by anyone on this course, or null. */
    public GhostRecording overallBest(String courseHash) {
        Course c = courses.get(courseHash);
        return c == null ? null : c.bestOverall;
    }

    // Records a completed run, replacing the player's personal best and/or the overall best
    // if it is faster. Returns a 2-bit result: bit 0 = new personal best, bit 1 = new overall.
    public int submit(String courseHash, UUID player, GhostRecording run) {
        Course c = courses.computeIfAbsent(courseHash, k -> new Course());
        int result = 0;
        GhostRecording prevPersonal = c.byPlayer.get(player);
        if (prevPersonal == null || run.timeTicks() < prevPersonal.timeTicks()) {
            c.byPlayer.put(player, run);
            result |= 1;
        }
        if (c.bestOverall == null || run.timeTicks() < c.bestOverall.timeTicks()) {
            c.bestOverall = run;
            result |= 2;
        }
        if (result != 0) setDirty();
        return result;
    }

    // -----------------------------------------------------------------
    //  Persistence
    // -----------------------------------------------------------------

    private static GhostSavedData load(CompoundTag tag) {
        GhostSavedData data = new GhostSavedData();
        ListTag list = tag.getListOrEmpty("Courses");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompoundOrEmpty(i);
            Course c = new Course();
            if (ct.contains("Overall")) c.bestOverall = GhostRecording.load(ct.getCompoundOrEmpty("Overall"));
            ListTag players = ct.getListOrEmpty("Players");
            for (int j = 0; j < players.size(); j++) {
                CompoundTag pt = players.getCompoundOrEmpty(j);
                pt.read("Id", UUIDUtil.CODEC).ifPresent(id ->
                    c.byPlayer.put(id, GhostRecording.load(pt.getCompoundOrEmpty("Run"))));
            }
            data.courses.put(ct.getStringOr("Hash", ""), c);
        }
        return data;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, Course> e : courses.entrySet()) {
            Course c = e.getValue();
            CompoundTag ct = new CompoundTag();
            ct.putString("Hash", e.getKey());
            if (c.bestOverall != null) ct.put("Overall", c.bestOverall.save());
            ListTag players = new ListTag();
            for (Map.Entry<UUID, GhostRecording> pe : c.byPlayer.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.store("Id", UUIDUtil.CODEC, pe.getKey());
                pt.put("Run", pe.getValue().save());
                players.add(pt);
            }
            ct.put("Players", players);
            list.add(ct);
        }
        tag.put("Courses", list);
        return tag;
    }
}
