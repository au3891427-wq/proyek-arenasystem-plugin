package id.hadziq.arenasystem;

/**
 * Creates a slope-limited copy of the scanned surface map. The source profile is never
 * modified. The result is later materialized by ArenaGenerator and included in the
 * normal arena snapshot, so restore/delete can put the original terrain back.
 */
final class TerrainNormalizer {
    private TerrainNormalizer() {
    }

    static Result normalize(TerrainProfile source, int maxAdjacentStep) {
        return normalize(source, maxAdjacentStep, -1);
    }

    /**
     * Normalize the complete map and, when spawnOffset is in range, constrain both
     * team spawn columns to one shared elevation. The neighboring terrain is blended
     * into those anchors using the same slope limit, producing ramps instead of pads
     * perched on cliffs.
     */
    static Result normalize(TerrainProfile source, int maxAdjacentStep, int spawnOffset) {
        if (maxAdjacentStep < 1) {
            throw new IllegalArgumentException("maxAdjacentStep must be at least 1");
        }

        int radius = source.radius();
        int size = radius * 2 + 1;
        int[][] original = source.copyHeights();
        int[][] seedHeights = source.copyHeights();

        if (spawnOffset >= 0 && spawnOffset <= radius) {
            int sharedSpawnY = Math.floorDiv(source.heightAt(-spawnOffset, 0)
                    + source.heightAt(spawnOffset, 0), 2);
            int redX = radius - spawnOffset;
            int blueX = radius + spawnOffset;
            int centerZ = radius;

            // Restrict the source map to a slope-compatible corridor around both
            // equal-height spawn anchors. This makes the two anchors survive the
            // smoothing envelopes exactly while gradually blending nearby terrain.
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    // Keep each 9x9 team pad level, then slope the surrounding
                    // terrain away from the pad boundary. Distance is measured to
                    // the rectangle rather than only to its center block.
                    int redDistance = Math.max(0, Math.abs(x - redX) - 4)
                            + Math.max(0, Math.abs(z - centerZ) - 4);
                    int blueDistance = Math.max(0, Math.abs(x - blueX) - 4)
                            + Math.max(0, Math.abs(z - centerZ) - 4);
                    int lowerBound = Math.max(sharedSpawnY - maxAdjacentStep * redDistance,
                            sharedSpawnY - maxAdjacentStep * blueDistance);
                    int upperBound = Math.min(sharedSpawnY + maxAdjacentStep * redDistance,
                            sharedSpawnY + maxAdjacentStep * blueDistance);
                    seedHeights[x][z] = Math.max(lowerBound, Math.min(upperBound, seedHeights[x][z]));
                }
            }
        }

        int[][] upper = copy(seedHeights);
        int[][] lower = copy(seedHeights);

        // Upper Lipschitz envelope: the closest surface that never rises faster
        // than maxAdjacentStep per horizontal block from any scanned column.
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x > 0) upper[x][z] = Math.min(upper[x][z], upper[x - 1][z] + maxAdjacentStep);
                if (z > 0) upper[x][z] = Math.min(upper[x][z], upper[x][z - 1] + maxAdjacentStep);
            }
        }
        for (int x = size - 1; x >= 0; x--) {
            for (int z = size - 1; z >= 0; z--) {
                if (x + 1 < size) upper[x][z] = Math.min(upper[x][z], upper[x + 1][z] + maxAdjacentStep);
                if (z + 1 < size) upper[x][z] = Math.min(upper[x][z], upper[x][z + 1] + maxAdjacentStep);
            }
        }

        // Lower Lipschitz envelope. Averaging the upper and lower envelopes gives
        // a balanced cut/fill solution instead of only cutting peaks or filling pits.
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x > 0) lower[x][z] = Math.max(lower[x][z], lower[x - 1][z] - maxAdjacentStep);
                if (z > 0) lower[x][z] = Math.max(lower[x][z], lower[x][z - 1] - maxAdjacentStep);
            }
        }
        for (int x = size - 1; x >= 0; x--) {
            for (int z = size - 1; z >= 0; z--) {
                if (x + 1 < size) lower[x][z] = Math.max(lower[x][z], lower[x + 1][z] - maxAdjacentStep);
                if (z + 1 < size) lower[x][z] = Math.max(lower[x][z], lower[x][z + 1] - maxAdjacentStep);
            }
        }

        int[][] normalized = new int[size][size];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int changedColumns = 0;
        int maxAdjustment = 0;
        long totalAdjustment = 0;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // floorDiv keeps the integer rounding consistent across the map.
                int target = Math.floorDiv(upper[x][z] + lower[x][z], 2);
                normalized[x][z] = target;
                minY = Math.min(minY, target);
                maxY = Math.max(maxY, target);
                int adjustment = Math.abs(target - original[x][z]);
                if (adjustment > 0) changedColumns++;
                maxAdjustment = Math.max(maxAdjustment, adjustment);
                totalAdjustment += adjustment;
            }
        }

        // Defensive post-condition: a bug in the envelope pass must never silently
        // create another cliff.
        int measuredMaxStep = 0;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x + 1 < size) measuredMaxStep = Math.max(measuredMaxStep,
                        Math.abs(normalized[x][z] - normalized[x + 1][z]));
                if (z + 1 < size) measuredMaxStep = Math.max(measuredMaxStep,
                        Math.abs(normalized[x][z] - normalized[x][z + 1]));
            }
        }
        if (measuredMaxStep > maxAdjacentStep) {
            throw new IllegalStateException("Terrain normalization failed: adjacent step "
                    + measuredMaxStep + " exceeds limit " + maxAdjacentStep);
        }

        TerrainProfile result = new TerrainProfile(radius, normalized, minY, maxY);
        double averageAdjustment = totalAdjustment / (double) (size * size);
        return new Result(result, changedColumns, maxAdjustment, averageAdjustment, measuredMaxStep);
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    static final class Result {
        private final TerrainProfile profile;
        private final int changedColumns;
        private final int maxAdjustment;
        private final double averageAdjustment;
        private final int maxAdjacentStep;

        private Result(TerrainProfile profile, int changedColumns, int maxAdjustment,
                       double averageAdjustment, int maxAdjacentStep) {
            this.profile = profile;
            this.changedColumns = changedColumns;
            this.maxAdjustment = maxAdjustment;
            this.averageAdjustment = averageAdjustment;
            this.maxAdjacentStep = maxAdjacentStep;
        }

        TerrainProfile profile() { return profile; }
        int changedColumns() { return changedColumns; }
        int maxAdjustment() { return maxAdjustment; }
        double averageAdjustment() { return averageAdjustment; }
        int maxAdjacentStep() { return maxAdjacentStep; }
    }
}
