package org.restor.fpv.track;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

public final class RoutePlanner {
    public static final int DEFAULT_TERRAIN_CHECK_BUDGET = 250_000;

    public interface Terrain {
        Surface surface(int x, int z);
        boolean isClear(int x, int y, int z);
        boolean canPlaceAnchor(int x, int y, int z);
    }

    public record Surface(int y, boolean safe) {}
    public record Point(int x, int y, int z) {}
    public record Center(double x, double y, double z) {}
    public record Node(Point anchor, Center center, int yaw) {}

    public record PlacementPlan(Node start, List<Node> gates) {
        public PlacementPlan {
            gates = List.copyOf(gates);
        }
    }

    public Optional<PlacementPlan> plan(long seed, int originX, int originZ, TrackProfile profile, Terrain terrain) {
        return plan(seed, originX, originZ, profile, terrain, DEFAULT_TERRAIN_CHECK_BUDGET);
    }

    public Optional<PlacementPlan> plan(long seed, int originX, int originZ, TrackProfile profile,
                                        Terrain terrain, int terrainCheckBudget) {
        if (terrainCheckBudget <= 0) return Optional.empty();
        BudgetTerrain budgetTerrain = new BudgetTerrain(terrain, terrainCheckBudget);
        SplittableRandom random = new SplittableRandom(seed);
        try {
            for (int attempt = 0; attempt < profile.candidateAttempts(); attempt++) {
                int shift = Math.max(1, profile.radius() / 3);
                int centerX = originX + random.nextInt(-shift, shift + 1);
                int centerZ = originZ + random.nextInt(-shift, shift + 1);
                double rotation = random.nextDouble(Math.PI * 2.0);
                Optional<PlacementPlan> candidate = candidate(random.split(), centerX, centerZ, rotation,
                    profile, budgetTerrain);
                if (candidate.isPresent()) return candidate;
            }
        } catch (BudgetExceeded ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<PlacementPlan> candidate(SplittableRandom random, int centerX, int centerZ, double rotation,
                                               TrackProfile profile, Terrain terrain) {
        int count = profile.gateCount() + 1;
        List<Point> horizontal = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = rotation + Math.PI * 2.0 * i / count
                + random.nextDouble(-profile.angularJitter(), profile.angularJitter());
            double radius = profile.radius() * (1.0 + random.nextDouble(-profile.radialJitter(), profile.radialJitter()));
            horizontal.add(new Point(
                centerX + (int) Math.round(Math.cos(angle) * radius),
                0,
                centerZ + (int) Math.round(Math.sin(angle) * radius)));
        }

        List<Surface> surfaces = new ArrayList<>(count);
        for (Point point : horizontal) {
            Surface surface = terrain.surface(point.x(), point.z());
            if (!surface.safe()) return Optional.empty();
            surfaces.add(surface);
        }
        if (!safeStart(horizontal.get(0), surfaces.get(0), profile, terrain)) return Optional.empty();

        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Point point = horizontal.get(i);
            Surface surface = surfaces.get(i);
            Point next = horizontal.get((i + 1) % count);
            int yaw = yaw(point, next);
            int anchorY;
            if (i == 0) {
                anchorY = surface.y() + 1;
            } else {
                double centerY = surface.y() + profile.height();
                anchorY = (int) Math.floor(centerY - 1.0 - profile.gateSize() / 2.0);
            }
            if (!terrain.canPlaceAnchor(point.x(), anchorY, point.z())) return Optional.empty();
            Center center = new Center(point.x() + 0.5, anchorY + 1.0 + profile.gateSize() / 2.0, point.z() + 0.5);
            if (!clearSphere(center, profile.clearanceRadius(), terrain)) return Optional.empty();
            nodes.add(new Node(new Point(point.x(), anchorY, point.z()), center, yaw));
        }

        for (int i = 0; i < count; i++) {
            Center from = nodes.get(i).center();
            Center to = nodes.get((i + 1) % count).center();
            double horizontalDistance = Math.hypot(to.x() - from.x(), to.z() - from.z());
            if (horizontalDistance < 1.0
                || Math.abs(surfaces.get((i + 1) % count).y() - surfaces.get(i).y()) / horizontalDistance > profile.maxSlope()) {
                return Optional.empty();
            }
            if (!clearSegment(from, to, profile, terrain)) return Optional.empty();
        }
        return Optional.of(new PlacementPlan(nodes.get(0), nodes.subList(1, nodes.size())));
    }

    private boolean safeStart(Point point, Surface center, TrackProfile profile, Terrain terrain) {
        int radius = profile.startPlatformRadius();
        for (int x = point.x() - radius; x <= point.x() + radius; x++) {
            for (int z = point.z() - radius; z <= point.z() + radius; z++) {
                Surface sample = terrain.surface(x, z);
                if (!sample.safe() || Math.abs(sample.y() - center.y()) > profile.startFlatness()) return false;
            }
        }
        return true;
    }

    private boolean clearSegment(Center from, Center to, TrackProfile profile, Terrain terrain) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(distance / profile.segmentSampleStep()));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Center point = new Center(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t);
            if (!clearSphere(point, profile.clearanceRadius(), terrain)) return false;
        }
        return true;
    }

    private boolean clearSphere(Center center, double radius, Terrain terrain) {
        int reach = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;
        int centerX = (int) Math.floor(center.x());
        int centerY = (int) Math.floor(center.y());
        int centerZ = (int) Math.floor(center.z());
        for (int x = centerX - reach; x <= centerX + reach; x++) {
            for (int y = centerY - reach; y <= centerY + reach; y++) {
                for (int z = centerZ - reach; z <= centerZ + reach; z++) {
                    double dx = x + 0.5 - center.x();
                    double dy = y + 0.5 - center.y();
                    double dz = z + 0.5 - center.z();
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared && !terrain.isClear(x, y, z)) return false;
                }
            }
        }
        return true;
    }

    private static int yaw(Point from, Point to) {
        return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-(to.x() - from.x()), to.z() - from.z()))), 360);
    }

    private static final class BudgetTerrain implements Terrain {
        private final Terrain delegate;
        private int remaining;

        private BudgetTerrain(Terrain delegate, int budget) {
            this.delegate = delegate;
            this.remaining = budget;
        }

        private void consume() {
            if (remaining-- <= 0) throw BudgetExceeded.INSTANCE;
        }

        @Override
        public Surface surface(int x, int z) {
            consume();
            return delegate.surface(x, z);
        }

        @Override
        public boolean isClear(int x, int y, int z) {
            consume();
            return delegate.isClear(x, y, z);
        }

        @Override
        public boolean canPlaceAnchor(int x, int y, int z) {
            consume();
            return delegate.canPlaceAnchor(x, y, z);
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private static final BudgetExceeded INSTANCE = new BudgetExceeded();

        private BudgetExceeded() {
            super(null, null, false, false);
        }
    }
}
