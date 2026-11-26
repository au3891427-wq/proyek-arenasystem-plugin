package id.hadziq.arenasystem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Durable snapshots for arena generation and recovery.
 *
 * ARN2 snapshots remain readable for backward compatibility. New snapshots use
 * ARN3, which also records the world and absolute arena origin so a snapshot cannot
 * accidentally be applied at a different location.
 */
final class SnapshotStore {
    private static final int MAGIC_ARN2 = 0x41524E32;
    private static final int MAGIC_ARN3 = 0x41524E33;
    private static final int MAX_BLOCKS = 2_000_000;

    private final File directory;

    SnapshotStore(File pluginFolder) {
        this.directory = new File(pluginFolder, "snapshots");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create snapshot directory: " + directory);
        }
    }

    File fileFor(String arenaName) {
        return new File(directory, safeKey(arenaName) + ".dat.gz");
    }

    private File backupFor(String arenaName) {
        return new File(directory, safeKey(arenaName) + ".bak");
    }

    private File temporaryFor(String arenaName) {
        return new File(directory, safeKey(arenaName) + ".tmp");
    }

    private File backupTemporaryFor(String arenaName) {
        return new File(directory, safeKey(arenaName) + ".bak.tmp");
    }

    boolean exists(String arenaName) {
        return fileFor(arenaName).isFile() || backupFor(arenaName).isFile();
    }

    /** Save a new ARN3 snapshot with its expected world origin. */
    void save(ArenaData arena, Map<BlockPos, String> blocks) throws IOException {
        if (arena == null) throw new IllegalArgumentException("arena must not be null");
        saveInternal(arena.name, blocks, arena);
    }

    /**
     * Compatibility overload for internal callers that have not yet been migrated.
     * New code should use save(ArenaData, Map) so the origin is persisted.
     */
    void save(String arenaName, Map<BlockPos, String> blocks) throws IOException {
        saveInternal(arenaName, blocks, null);
    }

    private void saveInternal(String arenaName, Map<BlockPos, String> blocks, ArenaData origin) throws IOException {
        if (blocks == null) throw new IllegalArgumentException("blocks must not be null");
        if (blocks.size() > MAX_BLOCKS) throw new IOException("Jumlah blok snapshot melebihi batas aman.");

        File destination = fileFor(arenaName);
        File backup = backupFor(arenaName);
        File temporary = temporaryFor(arenaName);
        File backupTemporary = backupTemporaryFor(arenaName);

        deleteIfExists(temporary.toPath());
        deleteIfExists(backupTemporary.toPath());

        writeSnapshot(temporary, blocks, origin);
        // Verify the complete gzip stream (including its trailer) before either
        // committed snapshot path is replaced.
        LoadedSnapshot verified = readSnapshot(temporary, origin);
        if (origin != null && verified.centerY() != origin.centerY) {
            throw new IOException("Origin Y snapshot hasil penulisan tidak cocok.");
        }

        // The backup must represent the SAME pre-generation world state as the
        // primary. Keeping an older generation's snapshot as fallback could omit
        // blocks touched only by the newer plan.
        Files.copy(temporary.toPath(), backupTemporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
        readSnapshot(backupTemporary, origin);
        moveReplacing(backupTemporary.toPath(), backup.toPath());
        try {
            moveReplacing(temporary.toPath(), destination.toPath());
        } finally {
            deleteIfExists(temporary.toPath());
            deleteIfExists(backupTemporary.toPath());
        }

    }

    Map<BlockPos, String> load(String arenaName) throws IOException {
        return loadInternal(arenaName, null).blocks();
    }

    Map<BlockPos, String> load(ArenaData expectedArena) throws IOException {
        LoadedSnapshot snapshot = loadSnapshot(expectedArena);
        if (snapshot.centerY() != expectedArena.centerY) {
            throw new IOException("Origin Y snapshot tidak cocok dengan arena " + expectedArena.name + ".");
        }
        return snapshot.blocks();
    }

    LoadedSnapshot loadSnapshot(ArenaData expectedArena) throws IOException {
        if (expectedArena == null) throw new IllegalArgumentException("expectedArena must not be null");
        return loadInternal(expectedArena.name, expectedArena);
    }

    private LoadedSnapshot loadInternal(String arenaName, ArenaData expectedArena) throws IOException {
        File primary = fileFor(arenaName);
        File backup = backupFor(arenaName);
        IOException primaryFailure;

        if (primary.isFile()) {
            try {
                return readSnapshot(primary, expectedArena);
            } catch (IOException ex) {
                primaryFailure = ex;
            }
        } else {
            primaryFailure = new IOException("Snapshot utama tidak ditemukan: " + primary.getName());
        }

        if (backup.isFile()) {
            try {
                return readSnapshot(backup, expectedArena);
            } catch (IOException backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
            }
        }
        throw primaryFailure;
    }

    private void writeSnapshot(File destination, Map<BlockPos, String> blocks, ArenaData origin)
            throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(destination))))) {
            if (origin == null) {
                // Retained only for compatibility callers. All arena generation paths
                // now use the origin-aware ARN3 overload.
                output.writeInt(MAGIC_ARN2);
            } else {
                output.writeInt(MAGIC_ARN3);
                output.writeUTF(origin.worldName);
                output.writeInt(origin.centerX);
                output.writeInt(origin.centerY);
                output.writeInt(origin.centerZ);
            }
            output.writeInt(blocks.size());
            for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                String blockData = entry.getValue();
                if (pos == null || blockData == null) {
                    throw new IOException("Snapshot berisi posisi atau BlockData null.");
                }
                output.writeInt(pos.x);
                output.writeInt(pos.y);
                output.writeInt(pos.z);
                output.writeUTF(blockData);
            }
        }
    }

    private LoadedSnapshot readSnapshot(File source, ArenaData expectedArena) throws IOException {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        int originY = expectedArena == null ? 0 : expectedArena.centerY;
        boolean legacy = false;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(source))))) {
            int magic = input.readInt();
            if (magic == MAGIC_ARN3) {
                String worldName = input.readUTF();
                int centerX = input.readInt();
                originY = input.readInt();
                int centerZ = input.readInt();
                if (expectedArena != null && (!expectedArena.worldName.equals(worldName)
                        || expectedArena.centerX != centerX
                        || expectedArena.centerZ != centerZ)) {
                    throw new IOException("Origin snapshot tidak cocok dengan arena " + expectedArena.name
                            + " (world/koordinat horizontal berbeda).");
                }
            } else if (magic == MAGIC_ARN2) {
                legacy = true;
            } else {
                throw new IOException("Format snapshot tidak dikenal pada " + source.getName() + ".");
            }

            int count = input.readInt();
            if (count < 0 || count > MAX_BLOCKS) {
                throw new IOException("Jumlah blok snapshot tidak valid: " + count);
            }
            for (int i = 0; i < count; i++) {
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                String blockData = input.readUTF();
                BlockPos position = new BlockPos(x, y, z);
                if (result.put(position, blockData) != null) {
                    throw new IOException("Snapshot memiliki posisi blok duplikat: " + x + "," + y + "," + z);
                }
            }
            if (input.read() != -1) {
                throw new IOException("Snapshot memiliki data tambahan yang tidak dikenal.");
            }
        } catch (IOException ex) {
            throw new IOException("Gagal memvalidasi snapshot " + source.getName() + ": " + ex.getMessage(), ex);
        }
        return new LoadedSnapshot(result, originY, legacy);
    }

    record LoadedSnapshot(Map<BlockPos, String> blocks, int centerY, boolean legacy) { }

    void delete(String arenaName) throws IOException {
        IOException failure = null;
        for (File file : new File[]{fileFor(arenaName), backupFor(arenaName),
                temporaryFor(arenaName), backupTemporaryFor(arenaName)}) {
            try {
                deleteIfExists(file.toPath());
            } catch (IOException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw new IOException("Gagal menghapus seluruh berkas snapshot "
                + safeKey(arenaName) + ".", failure);
    }

    /**
     * Renames (never deletes) every snapshot file of an arena to a ".discarded-<tag>" name so the
     * evidence survives an administrative discard and cannot be picked up as a live snapshot.
     * Returns the names of the files that were preserved.
     */
    java.util.List<String> quarantine(String arenaName, long tag) throws IOException {
        java.util.List<String> preserved = new java.util.ArrayList<>();
        String key = safeKey(arenaName);
        String[][] renames = {
                {key + ".dat.gz", key + ".discarded-" + tag + ".dat.gz"},
                {key + ".bak", key + ".discarded-" + tag + ".bak"},
                {key + ".tmp", key + ".discarded-" + tag + ".tmp"},
                {key + ".bak.tmp", key + ".discarded-" + tag + ".bak.tmp"}};
        IOException failure = null;
        for (String[] rename : renames) {
            Path source = new File(directory, rename[0]).toPath();
            Path target = new File(directory, rename[1]).toPath();
            if (!Files.exists(source)) continue;
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(source, target);
                }
                preserved.add(rename[1]);
            } catch (IOException ex) {
                if (failure == null) failure = ex; else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
        return preserved;
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static String safeKey(String arenaName) {
        if (arenaName == null || !arenaName.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Invalid arena name for snapshot: " + arenaName);
        }
        return arenaName.toLowerCase(Locale.ROOT);
    }
}
