package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;

// $VF: Compiled from SeededTerrainBlender.java
final class SeededTerrainBlender {
    static final long TERRAIN_SALT = 6072350185815100977L;
    static final int DEFAULT_MAX_SEED_DELTA = 6;
    static final int MAX_CONFIGURABLE_SEED_DELTA = 8;
    static final int PAD_HALF_SIZE = 4;
    private static final double[] CELL = new double[]{36.0, 18.0, 9.0};
    private static final double[] WEIGHT = new double[]{0.6, 0.3, 0.1};
    private static final double GAIN = 1.7;
    private static final double PAD_RAMP = 12.0;
    private static final double LANE_FLOOR = 0.35;
    private static final double LANE_RAMP = 10.0;
    private static final int OBJECTIVE_CLEAR = 4;
    private static final double OBJECTIVE_RAMP = 10.0;
    private static final int WORLD_FLOOR_MARGIN = 4;
    private static final int WORLD_CEILING_MARGIN = 40;

    private SeededTerrainBlender() {
    }

    static List<int[]> anchors(int spawnOffset, int teamCount) {
        List<int[]> anchors = new ArrayList<>();
        anchors.add(new int[]{-spawnOffset, 0});
        anchors.add(new int[]{spawnOffset, 0});
        if (teamCount >= 3) {
            anchors.add(new int[]{0, -spawnOffset});
        }

        if (teamCount >= 4) {
            anchors.add(new int[]{0, spawnOffset});
        }

        return anchors;
    }

    static TerrainProfile blend(
        TerrainProfile scanned,
        long seed,
        int spawnOffset,
        int teamCount,
        int maxSeedDelta,
        int worldMinY,
        int worldMaxY
    ) {
        if (maxSeedDelta < 0) {
            throw new IllegalArgumentException("maxSeedDelta must be >= 0");
        }

        int radius = scanned.radius();
        int size = radius * 2 + 1;
        int[][] out = new int[size][size];
        long salted = mix(seed ^ 6072350185815100977L);
        int floor = worldMinY + 4;
        int ceiling = worldMaxY - 40;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int base = scanned.heightAt(x, z);
                int delta = 0;
                if (maxSeedDelta > 0) {
                    double mask = fairnessMask(x, z, spawnOffset, teamCount);
                    double noise = 0.0;

                    for (int octave = 0; octave < CELL.length; octave++) {
                        noise += WEIGHT[octave] * valueNoise(salted, octave, x, z, CELL[octave]);
                    }

                    delta = (int)Math.round(noise * 1.7 * maxSeedDelta * mask);
                    delta = Math.max(-maxSeedDelta, Math.min(maxSeedDelta, delta));
                }

                int y = Math.max(Math.min(floor, base), Math.min(Math.max(ceiling, base), base + delta));
                out[x + radius][z + radius] = y;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        return new TerrainProfile(radius, out, minY, maxY);
    }

    static double fairnessMask(int x, int z, int spawnOffset, int teamCount) {
        double mask = 1.0;

        for (int[] anchor : anchors(spawnOffset, teamCount)) {
            int d = Math.max(0, Math.abs(x - anchor[0]) - 4) + Math.max(0, Math.abs(z - anchor[1]) - 4);
            mask = Math.min(mask, smooth(d / 12.0));
        }

        int objective = Math.max(0, Math.max(Math.abs(x), Math.abs(z)) - 4);
        mask = Math.min(mask, smooth(objective / 10.0));
        if (Math.abs(x) <= spawnOffset) {
            mask = Math.min(mask, 0.35 + 0.65 * smooth(Math.abs(z) / 10.0));
        }

        boolean greenLane = teamCount >= 3 && z <= 0 && z >= -spawnOffset;
        boolean yellowLane = teamCount >= 4 && z >= 0 && z <= spawnOffset;
        if (greenLane || yellowLane) {
            mask = Math.min(mask, 0.35 + 0.65 * smooth(Math.abs(x) / 10.0));
        }

        return mask;
    }

    private static double valueNoise(long salted, int octave, int x, int z, double cell) {
        double fx = x / cell;
        double fz = z / cell;
        int ix = (int)Math.floor(fx);
        int iz = (int)Math.floor(fz);
        double tx = smooth(fx - ix);
        double tz = smooth(fz - iz);
        double a = lerp(lattice(salted, octave, ix, iz), lattice(salted, octave, ix + 1, iz), tx);
        double b = lerp(lattice(salted, octave, ix, iz + 1), lattice(salted, octave, ix + 1, iz + 1), tx);
        return lerp(a, b, tz);
    }

    private static double lattice(long salted, int octave, int ix, int iz) {
        long h = mix(salted ^ octave * -7046029254386353131L ^ ix * -4417276706812531889L ^ iz * 1609587929392839161L);
        return (float)(h >>> 11) * 1.110223E-16F * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long mix(long z) {
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }
}
