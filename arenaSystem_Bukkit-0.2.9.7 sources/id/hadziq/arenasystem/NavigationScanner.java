package id.hadziq.arenasystem;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

// $VF: Compiled from NavigationScanner.java
final class NavigationScanner {
    private static final Set<Material> PATH_MATERIALS = Set.of(
        Material.GRAVEL,
        Material.COBBLESTONE,
        Material.COARSE_DIRT,
        Material.MOSSY_COBBLESTONE,
        Material.CUT_SANDSTONE,
        Material.SMOOTH_SANDSTONE,
        Material.SANDSTONE
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
            if (Math.abs(x) <= radius
                && Math.abs(z) <= radius
                && edit.position.y == terrain.heightAt(x, z) - arena.centerY
                && PATH_MATERIALS.contains(edit.material)) {
                path[x + radius][z + radius] = true;
            }
        }

        return new NavigationScanner(radius, path);
    }

    boolean isMainPath(int x, int z) {
        return this.inside(x, z) && this.mainPath[x + this.radius][z + this.radius];
    }

    boolean isNearMainPath(int x, int z, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
                if (this.isMainPath(x + dx, z + dz)) {
                    return true;
                }
            }
        }

        return false;
    }

    static boolean hasRelicRoutes(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int spawnOffset
    ) {
        return connected(arena, terrain, edits, -spawnOffset + 6, 0, 7, 0)
            && connected(arena, terrain, edits, spawnOffset - 6, 0, 7, 0);
    }

    private static boolean connected(
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int goalX,
        int goalZ
    ) {
        int radius = terrain.radius();
        boolean[][] visited = new boolean[radius * 2 + 1][radius * 2 + 1];
        int capacity = (radius * 2 + 1) * (radius * 2 + 1);
        int[] queueX = new int[capacity];
        int[] queueZ = new int[capacity];
        int head = 0;
        int tail = 0;
        if (walkable(arena, terrain, edits, startX, startZ) && walkable(arena, terrain, edits, goalX, goalZ)) {
            queueX[tail] = startX;
            queueZ[tail++] = startZ;
            visited[startX + radius][startZ + radius] = true;
            int[] dx = new int[]{1, -1, 0, 0};
            int[] dz = new int[]{0, 0, 1, -1};

            while (head < tail) {
                int x = queueX[head];
                int z = queueZ[head++];
                if (x == goalX && z == goalZ) {
                    return true;
                }

                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int nz = z + dz[i];
                    if (Math.abs(nx) <= radius
                        && Math.abs(nz) <= radius
                        && !visited[nx + radius][nz + radius]
                        && Math.abs(terrain.heightAt(nx, nz) - terrain.heightAt(x, z)) <= 1
                        && walkable(arena, terrain, edits, nx, nz)) {
                        visited[nx + radius][nz + radius] = true;
                        queueX[tail] = nx;
                        queueZ[tail++] = nz;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static boolean walkable(
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int x, int z
    ) {
        int surfaceY = terrain.heightAt(x, z);
        BlockEdit surface = edits.get(new BlockPos(x, surfaceY - arena.centerY, z));
        if (surface != null && !surface.material.isSolid()) {
            return false;
        }

        for (int dy = 1; dy <= 2; dy++) {
            BlockEdit above = edits.get(new BlockPos(x, surfaceY + dy - arena.centerY, z));
            if (above != null
                && !above.material.isAir()
                && above.material.isSolid()
                && !above.material.name().endsWith("_DOOR")) {
                return false;
            }
        }

        return true;
    }

    private boolean inside(int x, int z) {
        return Math.abs(x) <= this.radius && Math.abs(z) <= this.radius;
    }
}
