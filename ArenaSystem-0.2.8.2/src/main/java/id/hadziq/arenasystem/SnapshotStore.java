package id.hadziq.arenasystem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class SnapshotStore {
    private final File directory;

    SnapshotStore(File pluginFolder) {
        this.directory = new File(pluginFolder, "snapshots");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create snapshot directory: " + directory);
        }
    }

    File fileFor(String arenaName) {
        return new File(directory, arenaName.toLowerCase() + ".dat.gz");
    }

    boolean exists(String arenaName) {
        return fileFor(arenaName).isFile();
    }

    void save(String arenaName, Map<BlockPos, String> blocks) throws IOException {
        File destination = fileFor(arenaName);
        File temporary = new File(directory, arenaName.toLowerCase() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(temporary))))) {
            output.writeInt(0x41524E32); // ARN2
            output.writeInt(blocks.size());
            for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                output.writeInt(pos.x);
                output.writeInt(pos.y);
                output.writeInt(pos.z);
                output.writeUTF(entry.getValue());
            }
        }
        File backup = new File(directory, arenaName.toLowerCase() + ".bak");
        if (backup.exists() && !backup.delete()) {
            throw new IOException("Could not remove stale snapshot backup: " + backup);
        }
        boolean hadDestination = destination.exists();
        if (hadDestination && !destination.renameTo(backup)) {
            throw new IOException("Could not protect previous snapshot: " + destination);
        }
        if (!temporary.renameTo(destination)) {
            if (hadDestination) backup.renameTo(destination);
            throw new IOException("Could not finalize snapshot: " + destination);
        }
        if (backup.exists() && !backup.delete()) {
            // The new snapshot is already valid; a stale backup is harmless and can be removed later.
        }
    }

    Map<BlockPos, String> load(String arenaName) throws IOException {
        File source = fileFor(arenaName);
        if (!source.isFile()) throw new IOException("Snapshot tidak ditemukan: " + source.getName());
        Map<BlockPos, String> result = new LinkedHashMap<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(source))))) {
            int magic = input.readInt();
            if (magic != 0x41524E32) throw new IOException("Format snapshot tidak dikenal.");
            int count = input.readInt();
            if (count < 0 || count > 2_000_000) throw new IOException("Jumlah blok snapshot tidak valid.");
            for (int i = 0; i < count; i++) {
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                String blockData = input.readUTF();
                result.put(new BlockPos(x, y, z), blockData);
            }
        }
        return result;
    }

    void delete(String arenaName) throws IOException {
        File file = fileFor(arenaName);
        if (file.exists() && !file.delete()) throw new IOException("Gagal menghapus snapshot " + file.getName());
    }
}
