package org.restor.fpv.race;

public final class RacePlaneCrossing {
    private static final double EPSILON = 1.0e-9;

    private RacePlaneCrossing() {}

    public static double crossingParameter(
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        double centerX, double centerY, double centerZ,
        double normalX, double normalY, double normalZ,
        double halfAperture
    ) {
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength <= EPSILON || halfAperture < 0.0) return Double.NaN;

        double nx = normalX / normalLength;
        double ny = normalY / normalLength;
        double nz = normalZ / normalLength;
        double fromDistance = signedDistance(fromX, fromY, fromZ, centerX, centerY, centerZ, nx, ny, nz);
        double toDistance = signedDistance(toX, toY, toZ, centerX, centerY, centerZ, nx, ny, nz);
        if (fromDistance >= -EPSILON || toDistance < -EPSILON) return Double.NaN;

        double denominator = fromDistance - toDistance;
        if (Math.abs(denominator) <= EPSILON) return Double.NaN;
        double t = fromDistance / denominator;
        if (t < -EPSILON || t > 1.0 + EPSILON) return Double.NaN;

        double x = fromX + (toX - fromX) * t - centerX;
        double y = fromY + (toY - fromY) * t - centerY;
        double z = fromZ + (toZ - fromZ) * t - centerZ;
        double refX = 0.0;
        double refY = Math.abs(ny) > 0.99 ? 0.0 : 1.0;
        double refZ = Math.abs(ny) > 0.99 ? 1.0 : 0.0;
        double rightX = refY * nz - refZ * ny;
        double rightY = refZ * nx - refX * nz;
        double rightZ = refX * ny - refY * nx;
        double rightLength = Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        rightX /= rightLength;
        rightY /= rightLength;
        rightZ /= rightLength;
        double upX = ny * rightZ - nz * rightY;
        double upY = nz * rightX - nx * rightZ;
        double upZ = nx * rightY - ny * rightX;
        double horizontal = x * rightX + y * rightY + z * rightZ;
        double vertical = x * upX + y * upY + z * upZ;
        return Math.abs(horizontal) <= halfAperture + EPSILON
            && Math.abs(vertical) <= halfAperture + EPSILON ? t : Double.NaN;
    }

    private static double signedDistance(
        double x, double y, double z,
        double centerX, double centerY, double centerZ,
        double normalX, double normalY, double normalZ
    ) {
        return (x - centerX) * normalX + (y - centerY) * normalY + (z - centerZ) * normalZ;
    }
}
