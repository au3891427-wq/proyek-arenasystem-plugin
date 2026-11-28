package id.hadziq.arenasystem;

/**
 * Dependency-free geometry regression tests for Relic Rush team placement.
 * Run by the canonical GitHub Actions build before packaging the plugin.
 */
public final class TestRelicTeamLayout {
    private TestRelicTeamLayout() {
    }

    public static void main(String[] args) {
        verifyThreeTeamTriangle();
        verifyTwoTeamLayoutUnchanged();
        verifyFourTeamLayoutUnchanged();
        System.out.println("Relic Rush team layout tests passed.");
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

        double redRadius = Math.hypot(red[0], red[1]);
        double blueRadius = Math.hypot(blue[0], blue[1]);
        double greenRadius = Math.hypot(green[0], green[1]);
        assertNear("RED radius", redRadius, distance, 1.0);
        assertNear("BLUE radius", blueRadius, distance, 1.0);
        assertNear("GREEN radius", greenRadius, distance, 1.0);
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
