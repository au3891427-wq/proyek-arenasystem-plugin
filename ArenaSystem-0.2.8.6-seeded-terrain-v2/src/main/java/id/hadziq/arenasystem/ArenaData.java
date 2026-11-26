package id.hadziq.arenasystem;

final class ArenaData {
    final String name;
    final String worldName;
    final long seed;
    final int centerX;
    final int centerZ;
    final int centerY;
    final int redY;
    final int blueY;
    final int objectiveY;
    final int minSurfaceY;
    final int maxSurfaceY;
    final String mode;
    final int spawnOffset;
    final int radius;

    ArenaData(String name, String worldName, long seed, int centerX, int centerZ,
              int centerY, int redY, int blueY, int objectiveY,
              int minSurfaceY, int maxSurfaceY, String mode, int spawnOffset, int radius) {
        this.name = name;
        this.worldName = worldName;
        this.seed = seed;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.centerY = centerY;
        this.redY = redY;
        this.blueY = blueY;
        this.objectiveY = objectiveY;
        this.minSurfaceY = minSurfaceY;
        this.maxSurfaceY = maxSurfaceY;
        this.mode = mode == null ? "tdm" : mode;
        this.radius = Math.max(20, radius);
        this.spawnOffset = Math.max(1, Math.min(this.radius, spawnOffset));
    }
}
