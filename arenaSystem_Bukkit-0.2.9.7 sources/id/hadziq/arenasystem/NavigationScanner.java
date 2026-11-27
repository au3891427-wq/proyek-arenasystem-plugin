package id.hadziq.arenasystem;

import java.util.Arrays;
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
        ArenaData arena, TerrainProfile terrain, Map<BlockPos, BlockEdit> edits, int spawnOffset, int teamCount
    ) {
        for (String team : RelicTeamLayout.teamNames(teamCount)) {
            int[] spawn = RelicTeamLayout.offset(team, teamCount, spawnOffset);
            int startX = spawn[0] - Integer.signum(spawn[0]) * 6;
            int startZ = spawn[1] - Integer.signum(spawn[1]) * 6;
            if (!hasTwoPaths(arena, terrain, edits, startX, startZ, 0, 0)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasTwoPaths(
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int goalX,
        int goalZ
    ) {
        boolean[][] firstPath = findPath(arena, terrain, edits, startX, startZ, goalX, goalZ, null);
        if (firstPath == null) {
            return false;
        }

        int radius = terrain.radius();
        boolean[][] clearance = new boolean[radius * 2 + 1][radius * 2 + 1];

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (firstPath[x + radius][z + radius]) {
                    int fromStart = Math.abs(x - startX) + Math.abs(z - startZ);
                    int fromGoal = Math.abs(x - goalX) + Math.abs(z - goalZ);
                    if (fromStart > 4 && fromGoal > 4) {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                int nx = x + dx;
                                int nz = z + dz;
                                if (Math.abs(nx) <= radius && Math.abs(nz) <= radius) {
                                    clearance[nx + radius][nz + radius] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        clearance[startX + radius][startZ + radius] = false;
        clearance[goalX + radius][goalZ + radius] = false;
        return findPath(arena, terrain, edits, startX, startZ, goalX, goalZ, clearance) != null;
    }

    private static boolean[][] findPath(
        ArenaData arena,
        TerrainProfile terrain,
        Map<BlockPos, BlockEdit> edits,
        int startX,
        int startZ,
        int goalX,
        int goalZ,
        boolean[][] blocked
    ) {
        int radius = terrain.radius();
        int size = radius * 2 + 1;
        boolean[][] visited = new boolean[size][size];
        int[][] parentX = new int[size][size];
        int[][] parentZ = new int[size][size];

        for (int x = 0; x < size; x++) {
            Arrays.fill(parentX[x], Integer.MIN_VALUE);
            Arrays.fill(parentZ[x], Integer.MIN_VALUE);
        }

        if (walkable(arena, terrain, edits, startX, startZ) && walkable(arena, terrain, edits, goalX, goalZ)) {
            int capacity = size * size;
            int[] queueX = new int[capacity];
            int[] queueZ = new int[capacity];
            int head = 0;
            int tail = 0;
            queueX[tail] = startX;
            queueZ[tail++] = startZ;
            visited[startX + radius][startZ + radius] = true;
            int[] dx = new int[]{1, -1, 0, 0};
            int[] dz = new int[]{0, 0, 1, -1};

            while (head < tail) {
                int x = queueX[head];
                int z = queueZ[head++];
                if (x == goalX && z == goalZ) {
                    boolean[][] path = new boolean[size][size];
                    int px = goalX;
                    int pz = goalZ;

                    for (path[px + radius][pz + radius] = true;
                        px != startX || pz != startZ;
                        path[px + radius][pz + radius] = true
                    ) {
                        int nextX = parentX[px + radius][pz + radius];
                        int nextZ = parentZ[px + radius][pz + radius];
                        if (nextX == Integer.MIN_VALUE || nextZ == Integer.MIN_VALUE) {
                            return null;
                        }

                        px = nextX;
                        pz = nextZ;
                    }

                    return path;
                }

                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int nz = z + dz[i];
                    if (Math.abs(nx) <= radius && Math.abs(nz) <= radius) {
                        int ix = nx + radius;
                        int iz = nz + radius;
                        if (!visited[ix][iz]
                            && (
                                blocked == null
                                    || !blocked[ix][iz]
                                    || nx == startX && nz == startZ
                                    || nx == goalX && nz == goalZ
                            )
                            && Math.abs(terrain.heightAt(nx, nz) - terrain.heightAt(x, z)) <= 1
                            && walkable(arena, terrain, edits, nx, nz)) {
                            visited[ix][iz] = true;
                            parentX[ix][iz] = x;
                            parentZ[ix][iz] = z;
                            queueX[tail] = nx;
                            queueZ[tail++] = nz;
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
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
