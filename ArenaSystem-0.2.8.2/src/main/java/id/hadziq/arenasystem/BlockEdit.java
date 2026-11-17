package id.hadziq.arenasystem;

import org.bukkit.Material;

final class BlockEdit {
    final BlockPos position;
    final Material material;

    BlockEdit(int x, int y, int z, Material material) {
        this.position = new BlockPos(x, y, z);
        this.material = material;
    }
}
