package id.hadziq.arenasystem;

final class TerrainProfile {
    private final int radius;
    private final int[][] heights;
    final int minY;
    final int maxY;

    TerrainProfile(int radius, int[][] heights, int minY, int maxY) {
        this.radius = radius;
        this.heights = copy(heights);
        this.minY = minY;
        this.maxY = maxY;
    }

    int radius() {
        return radius;
    }

    int heightAt(int x, int z) {
        if (Math.abs(x) > radius || Math.abs(z) > radius) {
            throw new IllegalArgumentException("Terrain coordinate is outside the scanned area.");
        }
        return heights[x + radius][z + radius];
    }

    int[][] copyHeights() {
        return copy(heights);
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }
}
