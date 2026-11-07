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
    public record Node(Point anchor, Center center, int yaw, int pitch) {}

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
        SplittableRandom random = new SplittableRandom(seed);
        BudgetTerrain budgetTerrain = new BudgetTerrain(terrain, terrainCheckBudget);
        PlacementPlan best = null;
        double bestLength = Double.POSITIVE_INFINITY;
        try {
            for (int attempt = 0; attempt < Math.min(8, profile.candidateAttempts()); attempt++) {
                int shift = Math.max(1, profile.radius() / 3);
                int centerX = originX + random.nextInt(-shift, shift + 1);
                int centerZ = originZ + random.nextInt(-shift, shift + 1);
                Optional<PlacementPlan> candidate = candidate(random.split(), centerX, centerZ,
                    profile, budgetTerrain, false);
                if (candidate.isPresent()) {
                    double length = routeLength(candidate.get());
                    if (length < bestLength) {
                        best = candidate.get();
                        bestLength = length;
                    }
                }
            }
        } catch (BudgetExceeded ignored) {
            // Try the terrain-following fallback with a fresh, bounded inspection budget.
        }
        if (best != null) return Optional.of(best);

        BudgetTerrain fallbackTerrain = new BudgetTerrain(terrain, terrainCheckBudget);
        try {
            int fallbackAttempts = Math.min(16, Math.max(8, profile.candidateAttempts() * 2));
            best = null;
            bestLength = Double.POSITIVE_INFINITY;
            for (int attempt = 0; attempt < fallbackAttempts; attempt++) {
                int shift = Math.max(1, profile.radius() / 3);
                int centerX = originX + random.nextInt(-shift, shift + 1);
                int centerZ = originZ + random.nextInt(-shift, shift + 1);
                Optional<PlacementPlan> candidate = candidate(random.split(), centerX, centerZ,
                    profile, fallbackTerrain, true);
                if (candidate.isPresent()) {
                    double length = routeLength(candidate.get());
                    if (length < bestLength) {
                        best = candidate.get();
                        bestLength = length;
                    }
                }
            }
        } catch (BudgetExceeded ignored) {
            if (best != null) return Optional.of(best);
        }
        return Optional.ofNullable(best);
    }

    private Optional<PlacementPlan> candidate(SplittableRandom random, int centerX, int centerZ,
                                               TrackProfile profile, Terrain terrain, boolean terrainFollow) {
        List<Point> horizontal = randomWalk(random, centerX, centerZ, profile);
        if (horizontal.isEmpty()) return Optional.empty();
        int count = horizontal.size();

        List<Surface> surfaces = new ArrayList<>(count);
        for (Point point : horizontal) {
            Surface surface = terrain.surface(point.x(), point.z());
            if (!surface.safe()) return Optional.empty();
            surfaces.add(surface);
        }
        if (!terrainFollow && !safeStart(horizontal.get(0), surfaces.get(0), profile, terrain)) return Optional.empty();

        List<Integer> anchorYs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Surface surface = surfaces.get(i);
            anchorYs.add(i == 0 ? surface.y() + 1
                : (int) Math.floor(surface.y() + profile.height() - 1.0 - profile.gateSize() / 2.0));
        }

        List<Node> nodes = buildNodes(horizontal, anchorYs, profile, terrain);
        if (nodes.isEmpty()) return Optional.empty();

        for (int i = 0; i < count; i++) {
            Center from = nodes.get(i).center();
            Center to = nodes.get((i + 1) % count).center();
            double horizontalDistance = Math.hypot(to.x() - from.x(), to.z() - from.z());
            if (horizontalDistance < 1.0 || !terrainFollow
                && Math.abs(surfaces.get((i + 1) % count).y() - surfaces.get(i).y()) / horizontalDistance > profile.maxSlope()) {
                return Optional.empty();
            }
        }
        if (terrainFollow) {
            boolean clear = false;
            for (int lift = 0; lift <= 64; lift += 4) {
                List<Node> lifted = lift == 0 ? nodes : liftNodes(nodes, lift, profile, terrain);
                if (lifted.isEmpty()) continue;
                if (clearSmoothRoute(lifted, profile, terrain)) {
                    nodes = lifted;
                    clear = true;
                    break;
                }
            }
            if (!clear) return Optional.empty();
        } else if (!clearSmoothRoute(nodes, profile, terrain)) {
            return Optional.empty();
        }
        return Optional.of(new PlacementPlan(nodes.get(0), nodes.subList(1, nodes.size())));
    }

    private List<Node> buildNodes(List<Point> points, List<Integer> anchorYs, TrackProfile profile, Terrain terrain) {
        int count = points.size();
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int prev = i == 0 ? 0 : Math.floorMod(i - 1, count);
            int next = (i + 1) % count;
            int tangentFrom = i == 0 ? 0 : prev;
            Point point = points.get(i);
            int anchorY = anchorYs.get(i);
            if (!terrain.canPlaceAnchor(point.x(), anchorY, point.z())) return List.of();
            Center center = center(point, anchorY, profile);
            int tangentYaw = yaw(points.get(tangentFrom), points.get(next));
            double horizontal = distance(points.get(tangentFrom), points.get(next));
            double tangentY = centerY(anchorYs.get(next), profile) - centerY(anchorYs.get(tangentFrom), profile);
            int tangentPitch = (int) Math.round(Math.toDegrees(Math.atan2(tangentY, horizontal)));
            nodes.add(new Node(new Point(point.x(), anchorY, point.z()), center, tangentYaw, tangentPitch));
        }
        for (Node node : nodes) if (!clearSphere(node.center(), profile.clearanceRadius(), terrain)) return List.of();
        return nodes;
    }

    private List<Node> liftNodes(List<Node> nodes, int lift, TrackProfile profile, Terrain terrain) {
        List<Node> lifted = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            Point anchor = new Point(node.anchor().x(), node.anchor().y() + lift, node.anchor().z());
            if (!terrain.canPlaceAnchor(anchor.x(), anchor.y(), anchor.z())) return List.of();
            Center center = new Center(node.center().x(), node.center().y() + lift, node.center().z());
            if (!clearSphere(center, profile.clearanceRadius(), terrain)) return List.of();
            lifted.add(new Node(anchor, center, node.yaw(), node.pitch()));
        }
        return lifted;
    }

    private static Center center(Point point, int anchorY, TrackProfile profile) {
        return new Center(point.x() + 0.5, centerY(anchorY, profile), point.z() + 0.5);
    }

    private static double centerY(int anchorY, TrackProfile profile) {
        return anchorY + 1.0 + profile.gateSize() / 2.0;
    }

    private boolean clearSmoothRoute(List<Node> nodes, TrackProfile profile, Terrain terrain) {
        for (int i = 0; i < nodes.size(); i++) {
            if (!clearSmoothSegment(nodes, i, profile, terrain)) return false;
        }
        return true;
    }

    private boolean clearSmoothSegment(List<Node> nodes, int index, TrackProfile profile, Terrain terrain) {
        int count = nodes.size();
        Vector from = vector(nodes.get(index).center());
        Vector to = vector(nodes.get((index + 1) % count).center());
        Vector prev = index == 0 ? from : vector(nodes.get(Math.floorMod(index - 1, count)).center());
        Vector next = index == count - 1 ? to : vector(nodes.get((index + 2) % count).center());
        double outHandle = curveHandle(from, prev, to);
        double inHandle = curveHandle(to, from, next);
        Vector p1 = from.add(tangent(from, prev, to, outHandle));
        Vector p2 = to.subtract(tangent(to, from, next, inHandle));
        double distance = from.distance(to);
        int samples = Math.max(2, (int) Math.ceil(distance / profile.segmentSampleStep()));
        double pathRadius = Math.min(1.25, profile.clearanceRadius());
        for (int i = 0; i <= samples; i++) {
            Vector point = cubic(from, p1, p2, to, i / (double) samples);
            if (!clearSphere(new Center(point.x, point.y, point.z), pathRadius, terrain)) return false;
        }
        return true;
    }

    private static Vector vector(Center center) { return new Vector(center.x(), center.y(), center.z()); }

    private static Vector tangent(Vector point, Vector previous, Vector next, double handle) {
        Vector incoming = point.subtract(previous);
        Vector outgoing = next.subtract(point);
        Vector direction = next.subtract(previous);
        if (incoming.length() > 1e-6 && outgoing.length() > 1e-6
            && incoming.normalize().dot(outgoing.normalize()) < -0.75) {
            Vector normal = incoming.cross(outgoing);
            if (normal.length() < 1e-6) {
                Vector axis = Math.abs(incoming.y) < incoming.length() * 0.9
                    ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
                normal = axis.cross(incoming);
            }
            direction = normal.cross(incoming);
        }
        if (direction.length() < 1e-6) direction = outgoing;
        return direction.length() < 1e-6 ? new Vector(0, 0, 0) : direction.normalize().scale(handle);
    }

    private static double curveHandle(Vector point, Vector previous, Vector next) {
        double inLength = point.distance(previous);
        double outLength = point.distance(next);
        double span = Math.min(inLength, outLength);
        if (span < 1e-6) span = Math.max(inLength, outLength);
        if (span < 1e-6) return 0;
        Vector in = point.subtract(previous).normalize();
        Vector out = next.subtract(point).normalize();
        double turn = (1.0 - Math.max(-1.0, Math.min(1.0, in.dot(out)))) * 0.5;
        return Math.min(span * 0.85, Math.max(1.2, span * (0.28 + 0.55 * turn)));
    }

    private static Vector cubic(Vector a, Vector b, Vector c, Vector d, double t) {
        double inv = 1.0 - t, inv2 = inv * inv, t2 = t * t;
        return a.scale(inv2 * inv).add(b.scale(3.0 * inv2 * t))
            .add(c.scale(3.0 * inv * t2)).add(d.scale(t2 * t));
    }

    private static double routeLength(PlacementPlan plan) {
        List<Node> nodes = new ArrayList<>(plan.gates().size() + 1);
        nodes.add(plan.start());
        nodes.addAll(plan.gates());
        double length = 0;
        for (int i = 0; i < nodes.size(); i++) {
            length += vector(nodes.get(i).center()).distance(vector(nodes.get((i + 1) % nodes.size()).center()));
        }
        return length;
    }

    private record Vector(double x, double y, double z) {
        Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        Vector subtract(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        Vector scale(double scale) { return new Vector(x * scale, y * scale, z * scale); }
        double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        Vector normalize() { double length = length(); return length < 1e-9 ? new Vector(0, 0, 0) : scale(1.0 / length); }
        double distance(Vector other) { return subtract(other).length(); }
    }

    private List<Point> randomWalk(SplittableRandom random, int centerX, int centerZ, TrackProfile profile) {
        int minStep = Math.max(24, profile.gateSize() * 3);
        int maxStep = Math.min(50, Math.max(minStep + 8, (int) Math.round(profile.radius() * 0.9)));
        int count = profile.gateCount() + 1;
        double maxSpread = profile.radius();
        double baseGap = Math.PI * 2.0 / count;

        for (int routeAttempt = 0; routeAttempt < 160; routeAttempt++) {
            List<Point> points = new ArrayList<>(count);
            double rotation = random.nextDouble(Math.PI * 2.0);
            for (int i = 0; i < count; i++) {
                double angleJitter = random.nextDouble(-baseGap * 0.045, baseGap * 0.045);
                double angle = rotation + baseGap * i + angleJitter;
                double radialScale = random.nextDouble(0.88, 1.0);
                int x = centerX + (int) Math.round(Math.cos(angle) * maxSpread * radialScale);
                int z = centerZ + (int) Math.round(Math.sin(angle) * maxSpread * radialScale);
                points.add(new Point(x, 0, z));
            }

            boolean valid = true;
            for (int i = 0; i < count; i++) {
                Point current = points.get(i);
                Point next = points.get((i + 1) % count);
                double gap = distance(current, next);
                if (gap < minStep || gap > maxStep
                    || Math.hypot(current.x() - centerX, current.z() - centerZ) > maxSpread) {
                    valid = false;
                    break;
                }
                for (int j = 0; j < i; j++) {
                    if (j == i - 1 || j == 0 && i == count - 1) continue;
                    if (distance(current, points.get(j)) < minStep) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) break;
            }
            if (valid) return points;
        }
        return List.of();
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
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
