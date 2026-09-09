package org.restor.fpv.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {
    private static final TrackProfile PROFILE = new TrackProfile(
        8, 42, 8, 6, 3, 12, 0.14, 0.12, 0.45, 3, 1, 2.0, 1.5);
    private final RoutePlanner planner = new RoutePlanner();

    @Test
    void sameSeedAndTerrainProduceSamePlan() {
        RoutePlanner.Terrain terrain = flatTerrain();
        RoutePlanner.PlacementPlan first = planner.plan(918273645L, 100, -200, PROFILE, terrain).orElseThrow();
        RoutePlanner.PlacementPlan second = planner.plan(918273645L, 100, -200, PROFILE, terrain).orElseThrow();
        assertEquals(first, second);
        assertEquals(PROFILE.gateCount(), first.gates().size());
    }

    @Test
    void rejectsFluidSurface() {
        RoutePlanner.Terrain terrain = new TestTerrain() {
            @Override
            public RoutePlanner.Surface surface(int x, int z) {
                return new RoutePlanner.Surface(0, false);
            }
        };
        assertTrue(planner.plan(1L, 0, 0, PROFILE, terrain).isEmpty());
    }

    @Test
    void rejectsTerrainOverSlopeLimit() {
        RoutePlanner.Terrain terrain = new TestTerrain() {
            @Override
            public RoutePlanner.Surface surface(int x, int z) {
                return new RoutePlanner.Surface(x, true);
            }
        };
        assertTrue(planner.plan(2L, 0, 0, PROFILE, terrain).isEmpty());
    }

    @Test
    void rejectsSolidObstructionAcrossRouteSegments() {
        RoutePlanner.Terrain terrain = new TestTerrain() {
            @Override
            public boolean isClear(int x, int y, int z) {
                return x != 0;
            }
        };
        assertTrue(planner.plan(3L, 0, 0, PROFILE, terrain).isEmpty());
    }

    @Test
    void generatedAnchorsAreReplaceableAndDistinct() {
        RoutePlanner.PlacementPlan plan = planner.plan(4L, 0, 0, PROFILE, flatTerrain()).orElseThrow();
        assertTrue(plan.start().anchor().y() > 0);
        assertEquals(PROFILE.gateCount(), plan.gates().stream().map(RoutePlanner.Node::anchor).distinct().count());
        assertFalse(plan.gates().contains(plan.start()));
    }

    @Test
    void stopsWhenTerrainBudgetIsExhausted() {
        int[] checks = {0};
        RoutePlanner.Terrain terrain = new TestTerrain() {
            @Override
            public boolean isClear(int x, int y, int z) {
                checks[0]++;
                return true;
            }
        };
        assertTrue(planner.plan(4L, 0, 0, PROFILE, terrain, 10).isEmpty());
        assertTrue(checks[0] <= 10);
    }

    private static RoutePlanner.Terrain flatTerrain() {
        return new TestTerrain() {};
    }

    private static class TestTerrain implements RoutePlanner.Terrain {
        @Override
        public RoutePlanner.Surface surface(int x, int z) {
            return new RoutePlanner.Surface(0, true);
        }

        @Override
        public boolean isClear(int x, int y, int z) {
            return y > 0;
        }

        @Override
        public boolean canPlaceAnchor(int x, int y, int z) {
            return y > 0;
        }
    }
}
