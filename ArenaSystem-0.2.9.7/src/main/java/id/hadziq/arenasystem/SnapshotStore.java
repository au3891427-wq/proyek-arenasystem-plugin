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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// $VF: Compiled from SnapshotStore.java
final class SnapshotStore {
    private static final int MAGIC_ARN2 = 1095913010;
    private static final int MAGIC_ARN3 = 1095913011;
    private static final int MAX_BLOCKS = 2000000;
    private final File directory;

    SnapshotStore(File pluginFolder) {
        this.directory = new File(pluginFolder, "snapshots");
        if (!this.directory.exists() && !this.directory.mkdirs()) {
            throw new IllegalStateException("Could not create snapshot directory: " + this.directory);
        }
    }

    File fileFor(String arenaName) {
        return new File(this.directory, safeKey(arenaName) + ".dat.gz");
    }

    private File backupFor(String arenaName) {
        return new File(this.directory, safeKey(arenaName) + ".bak");
    }

    private File temporaryFor(String arenaName) {
        return new File(this.directory, safeKey(arenaName) + ".tmp");
    }

    private File backupTemporaryFor(String arenaName) {
        return new File(this.directory, safeKey(arenaName) + ".bak.tmp");
    }

    boolean exists(String arenaName) {
        return this.fileFor(arenaName).isFile() || this.backupFor(arenaName).isFile();
    }

    void save(ArenaData arena, Map<BlockPos, String> blocks) throws IOException {
        if (arena == null) {
            throw new IllegalArgumentException("arena must not be null");
        }

        this.saveInternal(arena.name, blocks, arena);
    }

    void save(String arenaName, Map<BlockPos, String> blocks) throws IOException {
        this.saveInternal(arenaName, blocks, null);
    }

    private void saveInternal(String arenaName, Map<BlockPos, String> blocks, ArenaData origin) throws IOException {
        if (blocks == null) {
            throw new IllegalArgumentException("blocks must not be null");
        }

        if (blocks.size() > 2000000) {
            throw new IOException("Jumlah blok snapshot melebihi batas aman.");
        }

        File destination = this.fileFor(arenaName);
        File backup = this.backupFor(arenaName);
        File temporary = this.temporaryFor(arenaName);
        File backupTemporary = this.backupTemporaryFor(arenaName);
        deleteIfExists(temporary.toPath());
        deleteIfExists(backupTemporary.toPath());
        this.writeSnapshot(temporary, blocks, origin);
        SnapshotStore.LoadedSnapshot verified = this.readSnapshot(temporary, origin);
        if (origin != null && verified.centerY() != origin.centerY) {
            throw new IOException("Origin Y snapshot hasil penulisan tidak cocok.");
        }

        Files.copy(temporary.toPath(), backupTemporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
        this.readSnapshot(backupTemporary, origin);
        moveReplacing(backupTemporary.toPath(), backup.toPath());

        try {
            moveReplacing(temporary.toPath(), destination.toPath());
        } finally {
            deleteIfExists(temporary.toPath());
            deleteIfExists(backupTemporary.toPath());
        }
    }

    Map<BlockPos, String> load(String arenaName) throws IOException {
        return this.loadInternal(arenaName, null).blocks();
    }

    Map<BlockPos, String> load(ArenaData expectedArena) throws IOException {
        SnapshotStore.LoadedSnapshot snapshot = this.loadSnapshot(expectedArena);
        if (snapshot.centerY() != expectedArena.centerY) {
            throw new IOException("Origin Y snapshot tidak cocok dengan arena " + expectedArena.name + ".");
        } else {
            return snapshot.blocks();
        }
    }

    SnapshotStore.LoadedSnapshot loadSnapshot(ArenaData expectedArena) throws IOException {
        if (expectedArena == null) {
            throw new IllegalArgumentException("expectedArena must not be null");
        } else {
            return this.loadInternal(expectedArena.name, expectedArena);
        }
    }

    private SnapshotStore.LoadedSnapshot loadInternal(String arenaName, ArenaData expectedArena) throws IOException {
        File primary = this.fileFor(arenaName);
        File backup = this.backupFor(arenaName);
        IOException primaryFailure;
        if (primary.isFile()) {
            try {
                return this.readSnapshot(primary, expectedArena);
            } catch (IOException ex) {
                primaryFailure = ex;
            }
        } else {
            primaryFailure = new IOException("Snapshot utama tidak ditemukan: " + primary.getName());
        }

        if (backup.isFile()) {
            try {
                return this.readSnapshot(backup, expectedArena);
            } catch (IOException backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
            }
        }

        throw primaryFailure;
    }

    private void writeSnapshot(File destination, Map<BlockPos, String> blocks, ArenaData origin) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(destination)))
            )) {
            if (origin == null) {
                output.writeInt(1095913010);
            } else {
                output.writeInt(1095913011);
                output.writeUTF(origin.worldName);
                output.writeInt(origin.centerX);
                output.writeInt(origin.centerY);
                output.writeInt(origin.centerZ);
            }

            output.writeInt(blocks.size());

            for (Entry<BlockPos, String> entry : blocks.entrySet()) {
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

    private SnapshotStore.LoadedSnapshot readSnapshot(File source, ArenaData expectedArena) throws IOException {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        int originY = expectedArena == null ? 0 : expectedArena.centerY;
        boolean legacy = false;

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(source)))
            )) {
            int magic = input.readInt();
            if (magic == 1095913011) {
                String worldName = input.readUTF();
                int centerX = input.readInt();
                originY = input.readInt();
                int centerZ = input.readInt();
                if (expectedArena != null
                    && (
                        !expectedArena.worldName.equals(worldName)
                            || expectedArena.centerX != centerX
                            || expectedArena.centerZ != centerZ
                    )) {
                    throw new IOException(
                        "Origin snapshot tidak cocok dengan arena "
                            + expectedArena.name
                            + " (world/koordinat horizontal berbeda)."
                    );
                }
            } else {
                if (magic != 1095913010) {
                    throw new IOException("Format snapshot tidak dikenal pada " + source.getName() + ".");
                }

                legacy = true;
            }

            int count = input.readInt();
            if (count >= 0 && count <= 2000000) {
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

                if (input.read() == -1) {
                    return new SnapshotStore.LoadedSnapshot(result, originY, legacy);
                } else {
                    throw new IOException("Snapshot memiliki data tambahan yang tidak dikenal.");
                }
            } else {
                throw new IOException("Jumlah blok snapshot tidak valid: " + count);
            }
        } catch (IOException ex) {
            throw new IOException("Gagal memvalidasi snapshot " + source.getName() + ": " + ex.getMessage(), ex);
        }
    }

    void delete(String arenaName) throws IOException {
        IOException failure = null;

        for (File file : new File[]{
            this.fileFor(arenaName),
            this.backupFor(arenaName),
            this.temporaryFor(arenaName),
            this.backupTemporaryFor(arenaName)
        }) {
            try {
                deleteIfExists(file.toPath());
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }

        if (failure != null) {
            throw new IOException("Gagal menghapus seluruh berkas snapshot " + safeKey(arenaName) + ".", failure);
        }
    }

    List<String> quarantine(String arenaName, long tag) throws IOException {
        List<String> preserved = new ArrayList<>();
        String key = safeKey(arenaName);
        String[][] renames = new String[][]{
            {key + ".dat.gz", key + ".discarded-" + tag + ".dat.gz"},
            {key + ".bak", key + ".discarded-" + tag + ".bak"},
            {key + ".tmp", key + ".discarded-" + tag + ".tmp"},
            {key + ".bak.tmp", key + ".discarded-" + tag + ".bak.tmp"}
        };
        IOException failure = null;

        for (String[] rename : renames) {
            Path source = new File(this.directory, rename[0]).toPath();
            Path target = new File(this.directory, rename[1]).toPath();
            if (Files.exists(source)) {
                try {
                    try {
                        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(source, target);
                    }

                    preserved.add(rename[1]);
                } catch (IOException ex) {
                    if (failure == null) {
                        failure = ex;
                    } else {
                        failure.addSuppressed(ex);
                    }
                }
            }
        }

        if (failure != null) {
            throw failure;
        } else {
            return preserved;
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static String safeKey(String arenaName) {
        if (arenaName != null && arenaName.matches("[A-Za-z0-9_-]{1,20}")) {
            return arenaName.toLowerCase(Locale.ROOT);
        } else {
            throw new IllegalArgumentException("Invalid arena name for snapshot: " + arenaName);
        }
    }

    // $VF: Compiled from SnapshotStore.java
    record LoadedSnapshot(Map<BlockPos, String> blocks, int centerY, boolean legacy) {
    }
}
