package id.hadziq.arenasystem;

// $VF: Compiled from TerrainProfile.java
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
        return this.radius;
    }

    int heightAt(int x, int z) {
        if (Math.abs(x) <= this.radius && Math.abs(z) <= this.radius) {
            return this.heights[x + this.radius][z + this.radius];
        } else {
            throw new IllegalArgumentException("Terrain coordinate is outside the scanned area.");
        }
    }

    int[][] copyHeights() {
        return copy(this.heights);
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];

        for (int i = 0; i < source.length; i++) {
            result[i] = (int[])source[i].clone();
        }

        return result;
    }
}
