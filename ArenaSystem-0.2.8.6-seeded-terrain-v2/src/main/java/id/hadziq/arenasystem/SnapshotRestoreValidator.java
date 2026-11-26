package id.hadziq.arenasystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates an entire restore plan before the first block is written. Every BlockData
 * string is parsed, every relative position is bounds-checked, and every absolute
 * coordinate is checked against the world height. Nothing touches the world here, so
 * a single bad entry can never leave the world half restored.
 */
final class SnapshotRestoreValidator {
    private SnapshotRestoreValidator() { }

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

    /**
     * @param horizontalBound maximum |relative x| and |relative z|, or a negative value to skip
     *                        the horizontal check (used for in-memory match changes)
     * @throws IllegalStateException on the first invalid entry, naming it
     */
    static <T> List<Entry<T>> validate(Map<BlockPos, String> blocks, int centerX, int originY, int centerZ,
                                       int horizontalBound, int minHeight, int maxHeight,
                                       Function<String, T> parser) {
        List<Entry<T>> plan = new ArrayList<>(blocks.size());
        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            String raw = entry.getValue();
            if (pos == null || raw == null) {
                throw new IllegalStateException("Snapshot berisi posisi atau BlockData null.");
            }
            String where = pos.x + "," + pos.y + "," + pos.z;
            if (horizontalBound >= 0 && (Math.abs((long) pos.x) > horizontalBound
                    || Math.abs((long) pos.z) > horizontalBound)) {
                throw new IllegalStateException("Posisi relatif " + where + " berada di luar footprint (batas "
                        + horizontalBound + "); restore ditolak sebelum ada perubahan.");
            }
            long absoluteX = (long) centerX + pos.x;
            long absoluteY = (long) originY + pos.y;
            long absoluteZ = (long) centerZ + pos.z;
            if (absoluteX < Integer.MIN_VALUE || absoluteX > Integer.MAX_VALUE
                    || absoluteZ < Integer.MIN_VALUE || absoluteZ > Integer.MAX_VALUE
                    || absoluteY < minHeight || absoluteY >= maxHeight) {
                throw new IllegalStateException("Koordinat absolut untuk posisi " + where
                        + " berada di luar batas world (y harus " + minHeight + ".." + (maxHeight - 1)
                        + "); restore ditolak sebelum ada perubahan.");
            }
            final T parsed;
            try {
                parsed = parser.apply(raw);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("BlockData tidak valid pada posisi " + where + ": '" + raw
                        + "'; restore ditolak sebelum ada perubahan.", ex);
            }
            if (parsed == null) {
                throw new IllegalStateException("BlockData tidak valid pada posisi " + where + ": '" + raw + "'.");
            }
            plan.add(new Entry<>((int) absoluteX, (int) absoluteY, (int) absoluteZ, parsed));
        }
        return plan;
    }
}
