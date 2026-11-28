package id.hadziq.arenasystem;

import java.util.List;

public final class TestRelicTeamLayout {
    private TestRelicTeamLayout() {
    }

    public static void main(String[] args) {
        verifyThreeTeamTriangle();
        verifyTwoTeamLayoutUnchanged();
        verifyFourTeamLayoutUnchanged();
        verifyTacticalRouteNetworks();
        verifyRouteInputBounds();
        System.out.println("Relic Rush team layout and route geometry tests passed.");
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
                if (Math.abs(segment.start().x()) > 54 || Math.abs(segment.start().z()) > 54
                    || Math.abs(segment.end().x()) > 54 || Math.abs(segment.end().z()) > 54) {
                    throw new AssertionError("route segment exceeds the arena safety margin");
                }
                if (Math.hypot(segment.start().x(), segment.start().z()) < 7.0
                    || Math.hypot(segment.end().x(), segment.end().z()) < 7.0) {
                    throw new AssertionError("route segment enters the altar exclusion zone");
                }
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
        }

        List<RelicRushRouteLayout.Segment> twoTeam = RelicRushRouteLayout.segments(2, 48, 56);
        assertHasEndpoint("RED sanctum exit", twoTeam, RelicRushRouteLayout.Type.MAIN, -42, 0);
        assertHasEndpoint("BLUE sanctum exit", twoTeam, RelicRushRouteLayout.Type.MAIN, 42, 0);
        assertHasEndpoint("RED altar-side gate", twoTeam, RelicRushRouteLayout.Type.MAIN, -12, 0);
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
