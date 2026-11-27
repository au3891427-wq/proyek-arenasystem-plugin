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

    static NavigationScanner scan(TerrainProfile terrain, Map<BlockPos, BlockEdit> edits) {
        int radius = terrain.radius();
        boolean[][] path = new boolean[radius * 2 + 1][radius * 2 + 1];
        for (BlockEdit edit : edits.values()) {
            int x = edit.position.x;
            int z = edit.position.z;
            if (Math.abs(x) > radius || Math.abs(z) > radius) continue;
            if (PATH_MATERIALS.contains(edit.material)) {
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

    private boolean inside(int x, int z) {
        return Math.abs(x) <= radius && Math.abs(z) <= radius;
    }
}
