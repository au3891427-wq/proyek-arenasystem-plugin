package id.hadziq.arenasystem;

import java.util.Objects;

final class BlockPos {
    final int x;
    final int y;
    final int z;

    BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockPos)) return false;
        BlockPos that = (BlockPos) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
