package id.hadziq.arenasystem;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

final class TerrainScanner {
    /**
     * How many blocks above the scanned surface to look for pre-existing "generic structure"
     * matter (houses, walls, ruins, roofs - anything man-made or otherwise not accounted for by
     * the foliage/vegetation exclusions below). Matches the scan height already used elsewhere
     * for combat-vegetation cleanup (ArenaGenerator.queueCombatVegetationCleanup,
     * ArenaSystemPlugin.clearCombatVegetationSafely), so this reuses an existing, already-tuned
     * convention instead of introducing a new one.
     */
    private static final int GENERIC_STRUCTURE_SCAN_HEIGHT = 12;

    private TerrainScanner() {
    }

    static TerrainProfile scan(World world, int centerX, int centerZ, int radius) {
        int size = radius * 2 + 1;
        int[][] heights = new int[size][size];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int surfaceY = findSurface(world, centerX + x, centerZ + z);
                if (surfaceY == Integer.MIN_VALUE) return null;
                heights[x + radius][z + radius] = surfaceY;
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
            }
        }
        return new TerrainProfile(radius, heights, minY, maxY);
    }

    static int findSurface(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        // A water/lava top can be used as a build surface; the platform replaces only its top layer.
        if (highest.isLiquid()) return highest.getY();
        for (int y = highest.getY(); y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material material = block.getType();
            if (!material.isSolid() || material.isAir() || isFoliage(material)) continue;
            if (block.getState() instanceof TileState) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * True when the column above the already-identified surface still holds solid matter that
     * is neither foliage (trees, leaves - natural, not "structure") nor combat vegetation (the
     * same exclusion ArenaGenerator already clears). This is a coarse density signal only: it
     * never classifies WHAT the structure is, and it never edits a block. Used solely so the
     * admin creating/regenerating an arena can be told the plot already has too much pre-existing
     * building/ruin material to make a proportional, readable combat arena, instead of silently
     * inheriting whatever density happens to be there, or - worse - discovering it only when
     * ArenaGenerator later refuses to cut/fill through a block entity it finds in that structure.
     */
    static boolean hasGenericStructureAbove(World world, int x, int surfaceY, int z) {
        int ceiling = Math.min(world.getMaxHeight() - 1, surfaceY + GENERIC_STRUCTURE_SCAN_HEIGHT);
        for (int y = surfaceY + 1; y <= ceiling; y++) {
            Block block = world.getBlockAt(x, y, z);
            Material material = block.getType();
            if (material.isAir() || !material.isSolid() || isFoliage(material)) continue;
            if (ArenaGenerator.isCombatVegetation(material)) continue;
            return true;
        }
        return false;
    }

    private static boolean isFoliage(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.equals("AZALEA") || name.equals("FLOWERING_AZALEA");
    }
}
