package id.hadziq.arenasystem;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

final class TerrainScanner {
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

    private static boolean isFoliage(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.equals("AZALEA") || name.equals("FLOWERING_AZALEA");
    }
}
