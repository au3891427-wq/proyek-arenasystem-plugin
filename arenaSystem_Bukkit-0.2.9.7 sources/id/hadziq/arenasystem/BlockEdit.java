package id.hadziq.arenasystem;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

// $VF: Compiled from BlockEdit.java
final class BlockEdit {
    final BlockPos position;
    final Material material;
    final BlockData blockData;

    BlockEdit(int x, int y, int z, Material material) {
        this.position = new BlockPos(x, y, z);
        this.material = material;
        this.blockData = null;
    }

    BlockEdit(int x, int y, int z, BlockData blockData) {
        if (blockData == null) {
            throw new IllegalArgumentException("blockData must not be null");
        }

        this.position = new BlockPos(x, y, z);
        this.blockData = blockData.clone();
        this.material = blockData.getMaterial();
    }
}
