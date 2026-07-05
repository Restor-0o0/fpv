package org.restor.fpv.assembly;

// A component attachment point on a frame, in block units relative to the frame
// center. +X = right, +Y = up, -Z = forward (nose).
public record MountPoint(float x, float y, float z) {
    /**
     * Convert Blockbench coordinates to a mount point.
     * In Blockbench a block model spans 0..16 pixels per axis and (8, 8, 8) is the
     * block center — take the pixel position of the mount from your model and pass
     * it here as-is.
     */
    public static MountPoint fromBlockbench(float px, float py, float pz) {
        return new MountPoint((px - 8f) / 16f, (py - 8f) / 16f, (pz - 8f) / 16f);
    }
}
