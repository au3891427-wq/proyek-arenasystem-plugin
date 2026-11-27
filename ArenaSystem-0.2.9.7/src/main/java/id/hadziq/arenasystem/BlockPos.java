package id.hadziq.arenasystem;

import java.util.Objects;

// $VF: Compiled from BlockPos.java
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
        return this == other
            || other instanceof BlockPos that && this.x == that.x && this.y == that.y && this.z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.x, this.y, this.z);
    }
}
