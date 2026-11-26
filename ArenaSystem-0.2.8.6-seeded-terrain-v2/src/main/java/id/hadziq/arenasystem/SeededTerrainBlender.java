package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed-dominant bounded blend: blendedY = scannedY + clamp(seedDelta, -maxSeedDelta, +maxSeedDelta).
 * The seed delta comes from deterministic multi-octave 2D value noise in arena-relative
 * coordinates, damped by a fairness mask around every active spawn pad, the main lanes and
 * the objective. The result is a plain TerrainProfile that still goes through TerrainNormalizer
 * and TerrainDetector before any block is touched. Pure and Bukkit-free.
 */
final class SeededTerrainBlender {
    static final long TERRAIN_SALT = 0x5445525241494E31L; // "TERRAIN1": independent of the layout salts
    /** Measured on flat terrain (60 seeds): 6 gives relief 6-12 blocks and a cut/fill of exactly 6, far inside the 12-block bound. */
    static final int DEFAULT_MAX_SEED_DELTA = 6;
    static final int MAX_CONFIGURABLE_SEED_DELTA = 8;
    static final int PAD_HALF_SIZE = 4;                    // 9x9 spawn pads (matches TerrainNormalizer)
    private static final double[] CELL = {36.0, 18.0, 9.0};
    private static final double[] WEIGHT = {0.60, 0.30, 0.10};
    private static final double GAIN = 1.7;                // value noise rarely reaches +-1
    private static final double PAD_RAMP = 12.0;
    private static final double LANE_FLOOR = 0.35;
    private static final double LANE_RAMP = 10.0;
    private static final int OBJECTIVE_CLEAR = 4;
    private static final double OBJECTIVE_RAMP = 10.0;
    private static final int WORLD_FLOOR_MARGIN = 4;
    private static final int WORLD_CEILING_MARGIN = 40;    // headroom for structures above the surface

    private SeededTerrainBlender() { }

    /**
     * Relative spawn anchors of the ACTIVE teams only, matching ArenaGenerator/buildAdditionalTeamSpawnPads:
     * RED (-o,0), BLUE (o,0), then GREEN (0,-o) for 3+ teams and YELLOW (0,o) for 4 teams.
     */
    static List<int[]> anchors(int spawnOffset, int teamCount) {
        List<int[]> anchors = new ArrayList<>();
        anchors.add(new int[]{-spawnOffset, 0});
        anchors.add(new int[]{spawnOffset, 0});
        if (teamCount >= 3) anchors.add(new int[]{0, -spawnOffset});
        if (teamCount >= 4) anchors.add(new int[]{0, spawnOffset});
        return anchors;
    }

    static TerrainProfile blend(TerrainProfile scanned, long seed, int spawnOffset, int teamCount,
                                int maxSeedDelta, int worldMinY, int worldMaxY) {
        if (maxSeedDelta < 0) throw new IllegalArgumentException("maxSeedDelta must be >= 0");
        int radius = scanned.radius();
        int size = radius * 2 + 1;
        int[][] out = new int[size][size];
        long salted = mix(seed ^ TERRAIN_SALT);
        int floor = worldMinY + WORLD_FLOOR_MARGIN;
        int ceiling = worldMaxY - WORLD_CEILING_MARGIN;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int base = scanned.heightAt(x, z);
                int delta = 0;
                if (maxSeedDelta > 0) {
                    double mask = fairnessMask(x, z, spawnOffset, teamCount);
                    double noise = 0;
                    for (int octave = 0; octave < CELL.length; octave++) {
                        noise += WEIGHT[octave] * valueNoise(salted, octave, x, z, CELL[octave]);
                    }
                    delta = (int) Math.round(noise * GAIN * maxSeedDelta * mask);
                    delta = Math.max(-maxSeedDelta, Math.min(maxSeedDelta, delta));
                }
                // Safe world band, but never push a column that is already outside it further out.
                int y = Math.max(Math.min(floor, base), Math.min(Math.max(ceiling, base), base + delta));
                out[x + radius][z + radius] = y;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return new TerrainProfile(radius, out, minY, maxY);
    }

    /** 0 on spawn pads and the objective core, ~LANE_FLOOR on the main lanes, 1 in the open terrain. */
    static double fairnessMask(int x, int z, int spawnOffset, int teamCount) {
        double mask = 1.0;
        for (int[] anchor : anchors(spawnOffset, teamCount)) {
            int d = Math.max(0, Math.abs(x - anchor[0]) - PAD_HALF_SIZE) + Math.max(0, Math.abs(z - anchor[1]) - PAD_HALF_SIZE);
            mask = Math.min(mask, smooth(d / PAD_RAMP));
        }
        // Objective at the arena center.
        int objective = Math.max(0, Math.max(Math.abs(x), Math.abs(z)) - OBJECTIVE_CLEAR);
        mask = Math.min(mask, smooth(objective / OBJECTIVE_RAMP));
        // Lanes between spawns: damped, not removed, so the terrain keeps its seed character.
        if (Math.abs(x) <= spawnOffset) {
            mask = Math.min(mask, LANE_FLOOR + (1.0 - LANE_FLOOR) * smooth(Math.abs(z) / LANE_RAMP));
        }
        // Z-axis lane only toward ACTIVE spawns: GREEN lane is z<=0, YELLOW lane z>=0.
        boolean greenLane = teamCount >= 3 && z <= 0 && z >= -spawnOffset;
        boolean yellowLane = teamCount >= 4 && z >= 0 && z <= spawnOffset;
        if (greenLane || yellowLane) {
            mask = Math.min(mask, LANE_FLOOR + (1.0 - LANE_FLOOR) * smooth(Math.abs(x) / LANE_RAMP));
        }
        return mask;
    }

    private static double valueNoise(long salted, int octave, int x, int z, double cell) {
        double fx = x / cell;
        double fz = z / cell;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix);
        double tz = smooth(fz - iz);
        double a = lerp(lattice(salted, octave, ix, iz), lattice(salted, octave, ix + 1, iz), tx);
        double b = lerp(lattice(salted, octave, ix, iz + 1), lattice(salted, octave, ix + 1, iz + 1), tx);
        return lerp(a, b, tz);
    }

    private static double lattice(long salted, int octave, int ix, int iz) {
        long h = mix(salted ^ (octave * 0x9E3779B97F4A7C15L) ^ (ix * 0xC2B2AE3D27D4EB4FL) ^ (iz * 0x165667B19E3779F9L));
        return ((h >>> 11) * (1.0 / (1L << 53))) * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
