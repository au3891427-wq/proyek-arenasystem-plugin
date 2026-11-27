package id.hadziq.arenasystem;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

// $VF: Compiled from TerrainScanner.java
final class TerrainScanner {
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
                if (surfaceY == Integer.MIN_VALUE) {
                    return null;
                }

                heights[x + radius][z + radius] = surfaceY;
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
            }
        }

        return new TerrainProfile(radius, heights, minY, maxY);
    }

    static int findSurface(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        if (highest.isLiquid()) {
            return highest.getY();
        }

        for (int y = highest.getY(); y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material material = block.getType();
            if (material.isSolid()
                && !material.isAir()
                && !isFoliage(material)
                && !(block.getState() instanceof TileState)) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    static boolean hasGenericStructureAbove(World world, int x, int surfaceY, int z) {
        int ceiling = Math.min(world.getMaxHeight() - 1, surfaceY + 12);

        for (int y = surfaceY + 1; y <= ceiling; y++) {
            Block block = world.getBlockAt(x, y, z);
            Material material = block.getType();
            if (!material.isAir()
                && material.isSolid()
                && !isFoliage(material)
                && !ArenaGenerator.isCombatVegetation(material)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isFoliage(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES")
            || name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.equals("AZALEA")
            || name.equals("FLOWERING_AZALEA");
    }
}
