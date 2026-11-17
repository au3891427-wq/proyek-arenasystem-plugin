package id.hadziq.arenasystem;

public final class TestTerrainNormalizer {
    public static void main(String[] args) {
        for (int spawnOffset : new int[]{27, 32}) {
            testAbruptSpawnPlateau(12, spawnOffset);
            testAbruptSpawnPlateau(20, spawnOffset);
            testSinglePeak(20, spawnOffset);
            testAlreadyFlat(spawnOffset);
        }
    }
    private static void testAbruptSpawnPlateau(int jump, int spawnOffset) {
        int radius = 40;
        int[][] heights = new int[81][81];
        for (int x = 0; x < 81; x++) for (int z = 0; z < 81; z++) heights[x][z] = x < 40 ? 64 : 64 + jump;
        TerrainProfile raw = new TerrainProfile(radius, heights, 64, 64 + jump);
        TerrainNormalizer.Result result = TerrainNormalizer.normalize(raw, 2, spawnOffset);
        int red = result.profile().heightAt(-spawnOffset, 0), blue = result.profile().heightAt(spawnOffset, 0);
        if (result.maxAdjacentStep() > 2 || red != blue) throw new AssertionError("spawn balance / step failed");
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            if (result.profile().heightAt(-spawnOffset + dx, dz) != red
                    || result.profile().heightAt(spawnOffset + dx, dz) != blue) {
                throw new AssertionError("team pad must be flat");
            }
        }
        System.out.printf("abrupt spawn cliff=%d offset=%d -> maxStep=%d spawn=%d/%d maxAdjustment=%d changed=%d range=%d%n",
                jump, spawnOffset, result.maxAdjacentStep(), red, blue, result.maxAdjustment(), result.changedColumns(),
                result.profile().maxY - result.profile().minY);
    }
    private static void testSinglePeak(int peak, int spawnOffset) {
        int[][] heights = new int[81][81];
        for (int x = 0; x < 81; x++) for (int z = 0; z < 81; z++) heights[x][z] = 64;
        heights[40][40] = 64 + peak;
        TerrainNormalizer.Result result = TerrainNormalizer.normalize(new TerrainProfile(40, heights, 64, 64 + peak), 2, spawnOffset);
        if (result.maxAdjacentStep() > 2) throw new AssertionError("peak smoothing failed");
        System.out.printf("single peak=%d offset=%d -> maxStep=%d maxAdjustment=%d changed=%d range=%d%n",
                peak, spawnOffset, result.maxAdjacentStep(), result.maxAdjustment(), result.changedColumns(),
                result.profile().maxY - result.profile().minY);
    }
    private static void testAlreadyFlat(int spawnOffset) {
        int[][] heights = new int[81][81];
        for (int x = 0; x < 81; x++) for (int z = 0; z < 81; z++) heights[x][z] = 70;
        TerrainNormalizer.Result result = TerrainNormalizer.normalize(new TerrainProfile(40, heights, 70, 70), 2, spawnOffset);
        if (result.changedColumns() != 0 || result.maxAdjustment() != 0) throw new AssertionError("flat terrain modified");
        System.out.println("flat terrain preserved at spawn offset " + spawnOffset);
    }
}
