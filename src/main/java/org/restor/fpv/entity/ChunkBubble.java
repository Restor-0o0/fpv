package org.restor.fpv.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

// Keeps the chunks along a long-range strike loaded so the munition does not freeze
// halfway when it leaves every player's view distance.
public final class ChunkBubble {
    private final Set<Long> forced = new HashSet<>();

    public void update(ServerLevel level, Vec3 from, Vec3 to) {
        Set<Long> want = new HashSet<>();
        add(want, from, 2);
        add(want, to, 2);
        add(want, from.add(to).scale(0.5), 1);
        if (want.equals(forced)) return;
        for (long c : forced) {
            if (!want.contains(c)) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        }
        for (long c : want) {
            if (!forced.contains(c)) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        }
        forced.clear();
        forced.addAll(want);
    }

    public void release(ServerLevel level) {
        for (long c : forced) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        forced.clear();
    }

    private static void add(Set<Long> out, Vec3 pos, int radius) {
        int cx = ((int) Math.floor(pos.x)) >> 4;
        int cz = ((int) Math.floor(pos.z)) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) out.add(ChunkPos.pack(cx + dx, cz + dz));
        }
    }
}
