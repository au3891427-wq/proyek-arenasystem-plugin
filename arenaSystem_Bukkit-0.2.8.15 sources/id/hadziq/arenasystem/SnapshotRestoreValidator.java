package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// $VF: Compiled from SnapshotRestoreValidator.java
final class SnapshotRestoreValidator {
    private SnapshotRestoreValidator() {
    }

    static <T> List<SnapshotRestoreValidator.Entry<T>> validate(
        Map<BlockPos, String> blocks,
        int centerX,
        int originY,
        int centerZ,
        int horizontalBound,
        int minHeight,
        int maxHeight,
        Function<String, T> parser
    ) {
        List<SnapshotRestoreValidator.Entry<T>> plan = new ArrayList<>(blocks.size());

        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            String raw = entry.getValue();
            if (pos != null && raw != null) {
                String where = pos.x + "," + pos.y + "," + pos.z;
                if (horizontalBound < 0
                    || Math.abs((long)pos.x) <= horizontalBound && Math.abs((long)pos.z) <= horizontalBound) {
                    long absoluteX = (long)centerX + pos.x;
                    long absoluteY = (long)originY + pos.y;
                    long absoluteZ = (long)centerZ + pos.z;
                    if (absoluteX >= -2147483648L
                        && absoluteX <= 2147483647L
                        && absoluteZ >= -2147483648L
                        && absoluteZ <= 2147483647L
                        && absoluteY >= minHeight
                        && absoluteY < maxHeight) {
                        T parsed;
                        try {
                            parsed = parser.apply(raw);
                        } catch (RuntimeException ex) {
                            throw new IllegalStateException(
                                "BlockData tidak valid pada posisi "
                                    + where
                                    + ": '"
                                    + raw
                                    + "'; restore ditolak sebelum ada perubahan.",
                                ex
                            );
                        }

                        if (parsed == null) {
                            throw new IllegalStateException(
                                "BlockData tidak valid pada posisi " + where + ": '" + raw + "'."
                            );
                        }

                        plan.add(
                            new SnapshotRestoreValidator.Entry<>((int)absoluteX, (int)absoluteY, (int)absoluteZ, parsed)
                        );
                        continue;
                    }

                    throw new IllegalStateException(
                        "Koordinat absolut untuk posisi "
                            + where
                            + " berada di luar batas world (y harus "
                            + minHeight
                            + ".."
                            + (maxHeight - 1)
                            + "); restore ditolak sebelum ada perubahan."
                    );
                }

                throw new IllegalStateException(
                    "Posisi relatif "
                        + where
                        + " berada di luar footprint (batas "
                        + horizontalBound
                        + "); restore ditolak sebelum ada perubahan."
                );
            }

            throw new IllegalStateException("Snapshot berisi posisi atau BlockData null.");
        }

        return plan;
    }

    // $VF: Compiled from SnapshotRestoreValidator.java
    static final class Entry<T> {
        final int worldX;
        final int worldY;
        final int worldZ;
        final T data;

        private Entry(int worldX, int worldY, int worldZ, T data) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.data = data;
        }
    }
}
