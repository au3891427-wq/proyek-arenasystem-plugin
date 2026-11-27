package id.hadziq.arenasystem;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

/**
 * Scans the generated surface edits into a mode-aware navigation map.
 * Coordinates are arena-relative. The scan runs after mode layout has been
 * queued and before generic structures are planned.
 */
final class NavigationScanner {
    private static final Set<Material> PATH_MATERIALS = Set.of(
            Material.GRAVEL, Material.COBBLESTONE, Material.COARSE_DIRT,
            Material.MOSSY_COBBLESTONE, Material.CUT_SANDSTONE,
            Material.SMOOTH_SANDSTONE, Material.SANDSTONE
    );

    private final int radius;
    private final boolean[][] mainPath;

    private NavigationScanner(int radius, boolean[][] mainPath) {
        this.radius = radius;
        this.mainPath = mainPath;
    }

    static NavigationScanner scan(ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits) {
        int radius = terrain.radius();
        boolean[][] path = new boolean[radius * 2 + 1][radius * 2 + 1];
        for (BlockEdit edit : edits.values()) {
            int x = edit.position.x;
            int z = edit.position.z;
            if (Math.abs(x) > radius || Math.abs(z) > radius) continue;
            if (edit.position.y == terrain.heightAt(x, z) - arena.centerY
                    && PATH_MATERIALS.contains(edit.material)) {
                path[x + radius][z + radius] = true;
            }
        }
        return new NavigationScanner(radius, path);
    }

    boolean isMainPath(int x, int z) {
        return inside(x, z) && mainPath[x + radius][z + radius];
    }

    boolean isNearMainPath(int x, int z, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
                if (isMainPath(x + dx, z + dz)) return true;
            }
        }
        return false;
    }

    /**
     * Ensures both Relic Rush teams retain a walkable route between their own
     * sanctum and the central altar after a candidate structure pair is queued.
     * Doors are treated as traversable; ordinary solid blocks are not.
     */
    static boolean hasRelicRoutes(ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits,
                                  int spawnOffset) {
        return connected(arena, terrain, edits, -spawnOffset, 0, 7, 0)
                && connected(arena, terrain, edits, spawnOffset, 0, 7, 0);
    }

    private static boolean connected(ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits,
                                     int startX, int startZ, int goalX, int goalZ) {
        int radius = terrain.radius();
        boolean[][] visited = new boolean[radius * 2 + 1][radius * 2 + 1];
        int capacity = (radius * 2 + 1) * (radius * 2 + 1);
        int[] queueX = new int[capacity];
        int[] queueZ = new int[capacity];
        int head = 0;
        int tail = 0;
        if (!walkable(arena, terrain, edits, startX, startZ)
                || !walkable(arena, terrain, edits, goalX, goalZ)) return false;
        queueX[tail] = startX;
        queueZ[tail++] = startZ;
        visited[startX + radius][startZ + radius] = true;
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (head < tail) {
            int x = queueX[head];
            int z = queueZ[head++];
            if (x == goalX && z == goalZ) return true;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int nz = z + dz[i];
                if (Math.abs(nx) > radius || Math.abs(nz) > radius
                        || visited[nx + radius][nz + radius]) continue;
                if (Math.abs(terrain.heightAt(nx, nz) - terrain.heightAt(x, z)) > 1
                        || !walkable(arena, terrain, edits, nx, nz)) continue;
                visited[nx + radius][nz + radius] = true;
                queueX[tail] = nx;
                queueZ[tail++] = nz;
            }
        }
        return false;
    }

    private static boolean walkable(ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits,
                                    int x, int z) {
        int surfaceY = terrain.heightAt(x, z);
        BlockEdit surface = edits.get(new BlockPos(x, surfaceY - arena.centerY, z));
        if (surface != null && !surface.material.isSolid()) return false;
        for (int dy = 1; dy <= 2; dy++) {
            BlockEdit above = edits.get(new BlockPos(x, surfaceY + dy - arena.centerY, z));
            if (above == null || above.material.isAir() || !above.material.isSolid()
                    || above.material.name().endsWith("_DOOR")) continue;
            return false;
        }
        return true;
    }

    private boolean inside(int x, int z) {
        return Math.abs(x) <= radius && Math.abs(z) <= radius;
    }
}
