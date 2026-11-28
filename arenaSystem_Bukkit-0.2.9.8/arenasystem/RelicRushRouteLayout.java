package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, seed-independent Relic Rush route topology.
 * Main approaches and connectors are three blocks wide; terrain seeds only
 * change the safe decoration palette, never the navigation graph.
 */
final class RelicRushRouteLayout {
    enum Type {
        MAIN,
        ROTATION,
        CONTESTATION
    }

    record Point(int x, int z) {
    }

    record Segment(Point start, Point end, int width, Type type) {
        Segment {
            if (start == null || end == null || type == null) {
                throw new IllegalArgumentException("route segment fields must not be null");
            }
            if (width != 3) {
                throw new IllegalArgumentException("Relic Rush route width must remain 3 blocks");
            }
            if (start.equals(end)) {
                throw new IllegalArgumentException("route segment must have non-zero length");
            }
        }
    }

    private RelicRushRouteLayout() {
    }

    static List<Segment> segments(int teamCount, int spawnOffset, int arenaRadius) {
        int safeTeamCount = Math.max(2, Math.min(4, teamCount));
        if (spawnOffset < 40 || spawnOffset > arenaRadius - 2) {
            throw new IllegalArgumentException(
                "spawnOffset must be between 40 and arenaRadius - 2 for tactical Relic Rush routes"
            );
        }

        List<Segment> result = new ArrayList<>();
        if (safeTeamCount == 2) {
            addTwoTeamRoutes(result, spawnOffset);
        } else {
            addMultiTeamRoutes(result, safeTeamCount, spawnOffset);
        }
        // A secondary ring collects each team's two approaches and feeds four fair altar gates.
        addRing(result, Math.min(11, arenaRadius - 8), Type.ROTATION);
        addAltarGateSpurs(result);
        addContestationRing(result, Math.min((int)Math.round(spawnOffset * 2.0 / 3.0), arenaRadius - 8));

        return List.copyOf(result);
    }

    private static void addTwoTeamRoutes(List<Segment> out, int spawnOffset) {
        double scale = spawnOffset / 48.0;
        int[][] north = {
            {-42, 0}, {-42, -6}, {-37, -14}, {-31, -20}, {-23, -23}, {-17, -19}, {-13, -13}, {-9, -9}
        };
        int[][] middle = {
            {-42, 0}, {-43, 4}, {-38, 8}, {-33, 3}, {-28, -3},
            {-22, -3}, {-18, 5}, {-13, 8}, {-10, 3}, {-12, 0}
        };
        int[][] northToMiddle = {
            {-31, -20}, {-30, -15}, {-29, -9}, {-28, -3}
        };
        int[][] upperContestation = {
            {-23, -23}, {-16, -17}, {-8, -15}, {0, -16}, {8, -15}, {16, -17}, {23, -23}
        };

        for (int mirrorX : new int[]{1, -1}) {
            addPolyline(out, north, scale, mirrorX, 1, Type.MAIN);
            addPolyline(out, north, scale, mirrorX, -1, Type.MAIN);
            addPolyline(out, middle, scale, mirrorX, 1, Type.MAIN);
            addPolyline(out, northToMiddle, scale, mirrorX, 1, Type.ROTATION);
            addPolyline(out, northToMiddle, scale, mirrorX, -1, Type.ROTATION);
            addPolyline(out, upperContestation, scale, mirrorX, 1, Type.CONTESTATION);
            addPolyline(out, upperContestation, scale, mirrorX, -1, Type.CONTESTATION);
        }
    }

    private static void addMultiTeamRoutes(List<Segment> out, int teamCount, int spawnOffset) {
        double scale = spawnOffset / 48.0;
        double[] radial = {41.0, 36.0, 32.0, 27.0, 22.0, 17.0, 11.0, 8.5};
        double[] left = {0.0, -2.0, -7.0, -8.0, -4.0, -7.0, -8.0, -7.0};
        double[] right = {0.0, 2.0, 7.0, 8.0, 4.0, 7.0, 8.0, 7.0};
        double[][] connector = {
            {-8.0, 27.0},
            {-10.0, 24.0},
            {-5.0, 22.0},
            {0.0, 21.0},
            {5.0, 22.0},
            {10.0, 24.0},
            {8.0, 27.0}
        };
        double[] connectorLateral = new double[connector.length];
        double[] connectorRadial = new double[connector.length];
        for (int i = 0; i < connector.length; i++) {
            connectorLateral[i] = connector[i][0];
            connectorRadial[i] = connector[i][1];
        }

        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, spawnOffset);
            double length = Math.hypot(spawn[0], spawn[1]);
            double outwardX = spawn[0] / length;
            double outwardZ = spawn[1] / length;
            double tangentX = -outwardZ;
            double tangentZ = outwardX;

            addLocalPolyline(out, radial, left, scale, outwardX, outwardZ, tangentX, tangentZ, Type.MAIN);
            addLocalPolyline(out, radial, right, scale, outwardX, outwardZ, tangentX, tangentZ, Type.MAIN);
            addLocalPolyline(
                out, connectorRadial, connectorLateral, scale,
                outwardX, outwardZ, tangentX, tangentZ, Type.ROTATION
            );
        }
    }

    static List<Point> altarAccessPoints() {
        return List.of(
            new Point(0, -7),
            new Point(7, 0),
            new Point(0, 7),
            new Point(-7, 0)
        );
    }

    private static void addAltarGateSpurs(List<Segment> out) {
        addPath(out, new Point(0, -11), new Point(0, -8), Type.MAIN);
        addPath(out, new Point(11, 0), new Point(8, 0), Type.MAIN);
        addPath(out, new Point(0, 11), new Point(0, 8), Type.MAIN);
        addPath(out, new Point(-11, 0), new Point(-8, 0), Type.MAIN);
    }

    private static void addPath(List<Segment> out, Point start, Point end, Type type) {
        out.add(new Segment(start, end, 3, type));
    }

    private static void addContestationRing(List<Segment> out, int ringRadius) {
        addRing(out, ringRadius, Type.CONTESTATION);
    }

    private static void addRing(List<Segment> out, int ringRadius, Type type) {
        int steps = Math.max(64, ringRadius * 4);
        Point previous = null;
        Point first = null;

        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            Point current = new Point(
                (int)Math.round(Math.cos(angle) * ringRadius),
                (int)Math.round(Math.sin(angle) * ringRadius)
            );
            if (first == null) {
                first = current;
            }
            if (previous != null && !previous.equals(current)) {
                out.add(new Segment(previous, current, 3, type));
            }
            previous = current;
        }
    }

    private static void addPolyline(
        List<Segment> out, int[][] points, double scale, int mirrorX, int mirrorZ, Type type
    ) {
        Point previous = null;
        for (int[] point : points) {
            Point current = new Point(
                (int)Math.round(point[0] * scale * mirrorX),
                (int)Math.round(point[1] * scale * mirrorZ)
            );
            if (previous != null && !previous.equals(current)) {
                out.add(new Segment(previous, current, 3, type));
            }
            previous = current;
        }
    }

    private static void addLocalPolyline(
        List<Segment> out,
        double[] radial,
        double[] lateral,
        double scale,
        double outwardX,
        double outwardZ,
        double tangentX,
        double tangentZ,
        Type type
    ) {
        if (radial.length != lateral.length) {
            throw new IllegalArgumentException("radial and lateral route arrays must have equal length");
        }

        Point previous = null;
        for (int i = 0; i < radial.length; i++) {
            double r = radial[i] * scale;
            double l = lateral[i] * scale;
            Point current = new Point(
                (int)Math.round(outwardX * r + tangentX * l),
                (int)Math.round(outwardZ * r + tangentZ * l)
            );
            if (previous != null && !previous.equals(current)) {
                out.add(new Segment(previous, current, 3, type));
            }
            previous = current;
        }
    }
}
