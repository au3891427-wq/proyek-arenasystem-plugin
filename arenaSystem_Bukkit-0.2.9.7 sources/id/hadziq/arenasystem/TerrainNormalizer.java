package id.hadziq.arenasystem;

import java.util.List;

// $VF: Compiled from TerrainNormalizer.java
final class TerrainNormalizer {
    private TerrainNormalizer() {
    }

    static TerrainNormalizer.Result normalize(TerrainProfile source, int maxAdjacentStep) {
        return normalize(source, maxAdjacentStep, -1);
    }

    static TerrainNormalizer.Result normalize(TerrainProfile source, int maxAdjacentStep, int spawnOffset) {
        return spawnOffset >= 0 && spawnOffset <= source.radius()
            ? normalize(source, maxAdjacentStep, SeededTerrainBlender.anchors(spawnOffset, 2))
            : normalize(source, maxAdjacentStep, List.of());
    }

    static TerrainNormalizer.Result normalize(TerrainProfile source, int maxAdjacentStep, List<int[]> anchors) {
        if (maxAdjacentStep != 1) {
            throw new IllegalArgumentException("maxAdjacentStep must be exactly 1 for ArenaSystem combat terrain");
        }

        int radius = source.radius();
        int size = radius * 2 + 1;
        int[][] original = source.copyHeights();
        int[][] seedHeights = source.copyHeights();
        if (!anchors.isEmpty()) {
            long sum = 0L;

            for (int[] anchor : anchors) {
                sum += source.heightAt(anchor[0], anchor[1]);
            }

            int sharedSpawnY = (int)Math.floorDiv((long)sum, (long)anchors.size());

            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    int lowerBound = Integer.MIN_VALUE;
                    int upperBound = Integer.MAX_VALUE;

                    for (int[] anchor : anchors) {
                        int distance = Math.max(0, Math.abs(x - (radius + anchor[0])) - 4)
                            + Math.max(0, Math.abs(z - (radius + anchor[1])) - 4);
                        lowerBound = Math.max(lowerBound, sharedSpawnY - maxAdjacentStep * distance);
                        upperBound = Math.min(upperBound, sharedSpawnY + maxAdjacentStep * distance);
                    }

                    seedHeights[x][z] = Math.max(lowerBound, Math.min(upperBound, seedHeights[x][z]));
                }
            }
        }

        int[][] upper = copy(seedHeights);
        int[][] lower = copy(seedHeights);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x > 0) {
                    upper[x][z] = Math.min(upper[x][z], upper[x - 1][z] + maxAdjacentStep);
                }

                if (z > 0) {
                    upper[x][z] = Math.min(upper[x][z], upper[x][z - 1] + maxAdjacentStep);
                }
            }
        }

        for (int x = size - 1; x >= 0; x--) {
            for (int z = size - 1; z >= 0; z--) {
                if (x + 1 < size) {
                    upper[x][z] = Math.min(upper[x][z], upper[x + 1][z] + maxAdjacentStep);
                }

                if (z + 1 < size) {
                    upper[x][z] = Math.min(upper[x][z], upper[x][z + 1] + maxAdjacentStep);
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x > 0) {
                    lower[x][z] = Math.max(lower[x][z], lower[x - 1][z] - maxAdjacentStep);
                }

                if (z > 0) {
                    lower[x][z] = Math.max(lower[x][z], lower[x][z - 1] - maxAdjacentStep);
                }
            }
        }

        for (int x = size - 1; x >= 0; x--) {
            for (int z = size - 1; z >= 0; z--) {
                if (x + 1 < size) {
                    lower[x][z] = Math.max(lower[x][z], lower[x + 1][z] - maxAdjacentStep);
                }

                if (z + 1 < size) {
                    lower[x][z] = Math.max(lower[x][z], lower[x][z + 1] - maxAdjacentStep);
                }
            }
        }

        int[][] normalized = new int[size][size];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int changedColumns = 0;
        int maxAdjustment = 0;
        long totalAdjustment = 0L;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int target = Math.floorDiv(upper[x][z] + lower[x][z], 2);
                normalized[x][z] = target;
                minY = Math.min(minY, target);
                maxY = Math.max(maxY, target);
                int adjustment = Math.abs(target - original[x][z]);
                if (adjustment > 0) {
                    changedColumns++;
                }

                maxAdjustment = Math.max(maxAdjustment, adjustment);
                totalAdjustment += adjustment;
            }
        }

        int measuredMaxStep = 0;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x + 1 < size) {
                    measuredMaxStep = Math.max(measuredMaxStep, Math.abs(normalized[x][z] - normalized[x + 1][z]));
                }

                if (z + 1 < size) {
                    measuredMaxStep = Math.max(measuredMaxStep, Math.abs(normalized[x][z] - normalized[x][z + 1]));
                }
            }
        }

        if (measuredMaxStep > maxAdjacentStep) {
            throw new IllegalStateException(
                "Terrain normalization failed: adjacent step " + measuredMaxStep + " exceeds limit " + maxAdjacentStep
            );
        }

        TerrainProfile result = new TerrainProfile(radius, normalized, minY, maxY);
        double averageAdjustment = (double)totalAdjustment / (size * size);
        return new TerrainNormalizer.Result(result, changedColumns, maxAdjustment, averageAdjustment, measuredMaxStep);
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];

        for (int i = 0; i < source.length; i++) {
            result[i] = (int[])source[i].clone();
        }

        return result;
    }

    // $VF: Compiled from TerrainNormalizer.java
    static final class Result {
        private final TerrainProfile profile;
        private final int changedColumns;
        private final int maxAdjustment;
        private final double averageAdjustment;
        private final int maxAdjacentStep;

        private Result(
            TerrainProfile profile,
            int changedColumns,
            int maxAdjustment,
            double averageAdjustment,
            int maxAdjacentStep
        ) {
            this.profile = profile;
            this.changedColumns = changedColumns;
            this.maxAdjustment = maxAdjustment;
            this.averageAdjustment = averageAdjustment;
            this.maxAdjacentStep = maxAdjacentStep;
        }

        TerrainProfile profile() {
            return this.profile;
        }

        int changedColumns() {
            return this.changedColumns;
        }

        int maxAdjustment() {
            return this.maxAdjustment;
        }

        double averageAdjustment() {
            return this.averageAdjustment;
        }

        int maxAdjacentStep() {
            return this.maxAdjacentStep;
        }
    }
}
