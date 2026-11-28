package id.hadziq.arenasystem;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TestRelicTeamLayout {
    private TestRelicTeamLayout() {
    }

    public static void main(String[] args) {
        verifyThreeTeamTriangle();
        verifyTwoTeamLayoutUnchanged();
        verifyFourTeamLayoutUnchanged();
        verifyTacticalRouteNetworks();
        verifyRouteInputBounds();
        System.out.println("Relic Rush team layout and connected route geometry tests passed.");
    }

    private static void verifyThreeTeamTriangle() {
        int distance = 48;
        int[] red = RelicTeamLayout.offset("red", 3, distance);
        int[] blue = RelicTeamLayout.offset("blue", 3, distance);
        int[] green = RelicTeamLayout.offset("green", 3, distance);
        assertPoint("RED", red, -42, 24);
        assertPoint("BLUE", blue, 42, 24);
        assertPoint("GREEN", green, 0, -48);
        assertEquals("RED/BLUE mirror X", red[0], -blue[0]);
        assertEquals("RED/BLUE mirror Z", red[1], blue[1]);
        assertNear("RED radius", Math.hypot(red[0], red[1]), distance, 1.0);
        assertNear("BLUE radius", Math.hypot(blue[0], blue[1]), distance, 1.0);
        assertNear("GREEN radius", Math.hypot(green[0], green[1]), distance, 1.0);
    }

    private static void verifyTwoTeamLayoutUnchanged() {
        assertPoint("2-team RED", RelicTeamLayout.offset("red", 2, 48), -48, 0);
        assertPoint("2-team BLUE", RelicTeamLayout.offset("blue", 2, 48), 48, 0);
    }

    private static void verifyFourTeamLayoutUnchanged() {
        assertPoint("4-team RED", RelicTeamLayout.offset("red", 4, 48), -48, 0);
        assertPoint("4-team BLUE", RelicTeamLayout.offset("blue", 4, 48), 48, 0);
        assertPoint("4-team GREEN", RelicTeamLayout.offset("green", 4, 48), 0, -48);
        assertPoint("4-team YELLOW", RelicTeamLayout.offset("yellow", 4, 48), 0, 48);
    }

    private static void verifyTacticalRouteNetworks() {
        for (int teamCount = 2; teamCount <= 4; teamCount++) {
            List<RelicRushRouteLayout.Segment> segments =
                RelicRushRouteLayout.segments(teamCount, 48, 56);
            Set<Long> cells = rasterize(segments);
            int main = 0;
            int rotation = 0;
            int contestation = 0;
            if (segments.size() < 100) {
                throw new AssertionError(teamCount + "-team route network is too small: " + segments.size());
            }

            for (RelicRushRouteLayout.Segment segment : segments) {
                if (segment.width() != 3) {
                    throw new AssertionError("all tactical corridors must be exactly 3 blocks wide");
                }
                assertInsideArena(segment.start(), 54);
                assertInsideArena(segment.end(), 54);
                switch (segment.type()) {
                    case MAIN -> main++;
                    case ROTATION -> rotation++;
                    case CONTESTATION -> contestation++;
                }
            }

            if (main < 20 || rotation < teamCount * 3 || contestation < 80) {
                throw new AssertionError(
                    teamCount + "-team network lacks main/rotation/contestation links: "
                        + main + "/" + rotation + "/" + contestation
                );
            }
            if (cells.stream().anyMatch(TestRelicTeamLayout::insideAltarFootprint)) {
                throw new AssertionError(teamCount + "-team corridor overlaps the altar's 13x13 footprint");
            }
            assertFullyConnected(teamCount, cells);
        }

        List<RelicRushRouteLayout.Segment> twoTeam = RelicRushRouteLayout.segments(2, 48, 56);
        assertHasEndpoint("RED sanctum exit", twoTeam, RelicRushRouteLayout.Type.MAIN, -42, 0);
        assertHasEndpoint("BLUE sanctum exit", twoTeam, RelicRushRouteLayout.Type.MAIN, 42, 0);
        assertHasEndpoint("RED altar-side gate", twoTeam, RelicRushRouteLayout.Type.MAIN, -12, 0);
    }

    private static Set<Long> rasterize(List<RelicRushRouteLayout.Segment> segments) {
        Set<Long> cells = new HashSet<>();
        for (RelicRushRouteLayout.Segment segment : segments) {
            RelicRushRouteLayout.Point start = segment.start();
            RelicRushRouteLayout.Point end = segment.end();
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            steps = Math.max(1, steps);
            for (int step = 0; step <= steps; step++) {
                int x = (int)Math.round(start.x() + (end.x() - start.x()) * ((double)step / steps));
                int z = (int)Math.round(start.z() + (end.z() - start.z()) * ((double)step / steps));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        cells.add(key(x + dx, z + dz));
                    }
                }
            }
        }
        return cells;
    }

    private static void assertFullyConnected(int teamCount, Set<Long> cells) {
        if (cells.isEmpty()) {
            throw new AssertionError(teamCount + "-team route graph is empty");
        }

        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long first = cells.iterator().next();
        visited.add(first);
        queue.add(first);
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = (int)(current >> 32);
            int z = (int)current;
            for (int i = 0; i < 4; i++) {
                long next = key(x + dx[i], z + dz[i]);
                if (cells.contains(next) && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        if (visited.size() != cells.size()) {
            throw new AssertionError(
                teamCount + "-team route graph is disconnected: " + visited.size() + "/" + cells.size()
            );
        }
    }

    private static long key(int x, int z) {
        return ((long)x << 32) ^ (z & 0xffffffffL);
    }

    private static boolean insideAltarFootprint(long cell) {
        int x = (int)(cell >> 32);
        int z = (int)cell;
        return Math.abs(x) <= 6 && Math.abs(z) <= 6;
    }

    private static void assertInsideArena(RelicRushRouteLayout.Point point, int maxCoordinate) {
        if (Math.abs(point.x()) > maxCoordinate || Math.abs(point.z()) > maxCoordinate) {
            throw new AssertionError("route point outside safety margin: " + point);
        }
    }

    private static void verifyRouteInputBounds() {
        assertThrows("too-small spawn offset", () -> RelicRushRouteLayout.segments(3, 20, 56));
        assertThrows("spawn beyond arena", () -> RelicRushRouteLayout.segments(4, 56, 56));
    }

    private static void assertHasEndpoint(
        String label, List<RelicRushRouteLayout.Segment> segments,
        RelicRushRouteLayout.Type type, int x, int z
    ) {
        for (RelicRushRouteLayout.Segment segment : segments) {
            if (segment.type() == type
                && (segment.start().equals(new RelicRushRouteLayout.Point(x, z))
                    || segment.end().equals(new RelicRushRouteLayout.Point(x, z)))) {
                return;
            }
        }
        throw new AssertionError(label + " was not connected to the route graph");
    }

    private static void assertThrows(String label, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + " should have been rejected");
    }

    private static void assertPoint(String label, int[] actual, int expectedX, int expectedZ) {
        if (actual.length != 2 || actual[0] != expectedX || actual[1] != expectedZ) {
            throw new AssertionError(label + " expected (" + expectedX + "," + expectedZ
                + ") but was (" + actual[0] + "," + actual[1] + ")");
        }
    }

    private static void assertEquals(String label, int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertNear(String label, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(label + " expected " + expected + " ± " + tolerance + " but was " + actual);
        }
    }
}
