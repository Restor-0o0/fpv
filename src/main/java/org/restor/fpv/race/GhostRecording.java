package org.restor.fpv.race;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// A single completed race run stored as flight telemetry (see FPV_IDEAS.md §1-2). Not video:
// 20 frames/s (one per server tick) is enough to reproduce the motion, and the client
// interpolates between frames at render time. Frames are stored as flat arrays so a long run
// stays compact both in NBT and on the wire.
//
// modelType identifies the vehicle shape the ghost renderer should draw:
//   0 = generic quad (5"), 1 = tiny whoop, 2 = cinewhoop, 3 = plane.
public final class GhostRecording {
    // A run beyond this many ticks (~5 min) simply stops recording — a ghost that long is
    // pointless and keeps packet/NBT size bounded.
    public static final int MAX_FRAMES = 6000;

    private final String player;
    private final UUID playerId;
    private final int timeTicks;
    private final int modelType;
    // Parallel arrays, one entry per tick from run start.
    private final double[] x, y, z;
    private final float[] yaw, pitch, roll, throttle;

    private GhostRecording(String player, UUID playerId, int timeTicks, int modelType,
                           double[] x, double[] y, double[] z,
                           float[] yaw, float[] pitch, float[] roll, float[] throttle) {
        this.player = player;
        this.playerId = playerId;
        this.timeTicks = timeTicks;
        this.modelType = modelType;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.roll = roll; this.throttle = throttle;
    }

    public static GhostRecording of(String player, UUID playerId, int timeTicks, int modelType,
                                    List<GhostFrame> frames) {
        int n = Math.min(frames.size(), MAX_FRAMES);
        double[] x = new double[n], y = new double[n], z = new double[n];
        float[] yaw = new float[n], pitch = new float[n], roll = new float[n], throttle = new float[n];
        for (int i = 0; i < n; i++) {
            GhostFrame f = frames.get(i);
            x[i] = f.x(); y[i] = f.y(); z[i] = f.z();
            yaw[i] = f.yaw(); pitch[i] = f.pitch(); roll[i] = f.roll(); throttle[i] = f.throttle();
        }
        return new GhostRecording(player, playerId, timeTicks, modelType, x, y, z, yaw, pitch, roll, throttle);
    }

    public String player()    { return player; }
    public UUID playerId()    { return playerId; }
    public int timeTicks()    { return timeTicks; }
    public int modelType()    { return modelType; }
    public int frameCount()   { return x.length; }

    public GhostFrame frame(int i) {
        return new GhostFrame(x[i], y[i], z[i], yaw[i], pitch[i], roll[i], throttle[i]);
    }

    public boolean sameRecording(GhostRecording other) {
        return other != null
            && timeTicks == other.timeTicks
            && modelType == other.modelType
            && java.util.Objects.equals(playerId, other.playerId)
            && Arrays.equals(x, other.x)
            && Arrays.equals(y, other.y)
            && Arrays.equals(z, other.z)
            && Arrays.equals(yaw, other.yaw)
            && Arrays.equals(pitch, other.pitch)
            && Arrays.equals(roll, other.roll)
            && Arrays.equals(throttle, other.throttle);
    }

    // -----------------------------------------------------------------
    //  NBT (world saved data)
    // -----------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Player", player);
        if (playerId != null) tag.store("PlayerId", UUIDUtil.CODEC, playerId);
        tag.putInt("Time", timeTicks);
        tag.putInt("Model", modelType);
        int n = x.length;
        tag.putInt("N", n);
        // Interleaved double list keeps the array count small; floats packed separately.
        ListTag pos = new ListTag();
        for (int i = 0; i < n; i++) {
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(x[i]));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(y[i]));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(z[i]));
        }
        tag.put("Pos", pos);
        ListTag rot = new ListTag();
        for (int i = 0; i < n; i++) {
            rot.add(net.minecraft.nbt.FloatTag.valueOf(yaw[i]));
            rot.add(net.minecraft.nbt.FloatTag.valueOf(pitch[i]));
            rot.add(net.minecraft.nbt.FloatTag.valueOf(roll[i]));
            rot.add(net.minecraft.nbt.FloatTag.valueOf(throttle[i]));
        }
        tag.put("Rot", rot);
        return tag;
    }

    public static GhostRecording load(CompoundTag tag) {
        ListTag pos = tag.getListOrEmpty("Pos");
        ListTag rot = tag.getListOrEmpty("Rot");
        int n = boundedFrameCount(tag.getIntOr("N", 0), pos.size() / 3, rot.size() / 4);
        double[] x = new double[n], y = new double[n], z = new double[n];
        float[] yaw = new float[n], pitch = new float[n], roll = new float[n], throttle = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = pos.getDoubleOr(i * 3, 0.0);
            y[i] = pos.getDoubleOr(i * 3 + 1, 0.0);
            z[i] = pos.getDoubleOr(i * 3 + 2, 0.0);
            yaw[i]      = rot.getFloatOr(i * 4, 0f);
            pitch[i]    = rot.getFloatOr(i * 4 + 1, 0f);
            roll[i]     = rot.getFloatOr(i * 4 + 2, 0f);
            throttle[i] = rot.getFloatOr(i * 4 + 3, 0f);
        }
        UUID id = tag.read("PlayerId", UUIDUtil.CODEC).orElse(null);
        return new GhostRecording(tag.getStringOr("Player", ""), id, tag.getIntOr("Time", 0), tag.getIntOr("Model", 0),
            x, y, z, yaw, pitch, roll, throttle);
    }

    // -----------------------------------------------------------------
    //  Network
    // -----------------------------------------------------------------

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(player);
        buf.writeInt(timeTicks);
        buf.writeInt(modelType);
        int n = x.length;
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeDouble(x[i]); buf.writeDouble(y[i]); buf.writeDouble(z[i]);
            buf.writeFloat(yaw[i]); buf.writeFloat(pitch[i]); buf.writeFloat(roll[i]); buf.writeFloat(throttle[i]);
        }
    }

    public static GhostRecording read(FriendlyByteBuf buf) {
        String player = buf.readUtf();
        int time = buf.readInt();
        int model = buf.readInt();
        int n = requireNetworkFrameCount(buf.readVarInt());
        double[] x = new double[n], y = new double[n], z = new double[n];
        float[] yaw = new float[n], pitch = new float[n], roll = new float[n], throttle = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = buf.readDouble(); y[i] = buf.readDouble(); z[i] = buf.readDouble();
            yaw[i] = buf.readFloat(); pitch[i] = buf.readFloat(); roll[i] = buf.readFloat(); throttle[i] = buf.readFloat();
        }
        return new GhostRecording(player, null, time, model, x, y, z, yaw, pitch, roll, throttle);
    }

    public static List<GhostFrame> emptyFrames() { return new ArrayList<>(); }

    static int boundedFrameCount(int declared, int positionFrames, int rotationFrames) {
        return Math.max(0, Math.min(Math.min(declared, MAX_FRAMES), Math.min(positionFrames, rotationFrames)));
    }

    static int requireNetworkFrameCount(int count) {
        if (count < 0 || count > MAX_FRAMES) {
            throw new IllegalArgumentException("Ghost frame count out of range: " + count);
        }
        return count;
    }
}
