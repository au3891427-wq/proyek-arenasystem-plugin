package id.hadziq.arenasystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

// $VF: Compiled from PendingPlayerSnapshotStore.java
final class PendingPlayerSnapshotStore {
    private final File directory;

    PendingPlayerSnapshotStore(File pluginFolder) {
        this.directory = new File(pluginFolder, "player-snapshots");
        if (!this.directory.exists() && !this.directory.mkdirs()) {
            throw new IllegalStateException("Tidak dapat membuat direktori snapshot pemain: " + this.directory);
        }
    }

    void save(UUID playerId, PlayerSnapshot snapshot) throws IOException {
        if (!this.directory.isDirectory() && !this.directory.mkdirs()) {
            throw new IOException("Direktori snapshot pemain tidak tersedia: " + this.directory);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.writeTo(yaml.createSection("snapshot"));
        File destination = this.fileFor(playerId);
        File temporary = new File(this.directory, playerId + ".tmp");
        yaml.save(temporary);

        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    Map<UUID, PlayerSnapshot> loadAll(Logger logger) {
        Map<UUID, PlayerSnapshot> result = new LinkedHashMap<>();
        if (!this.directory.isDirectory()) {
            return result;
        }

        File[] files = this.directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }

        for (File file : files) {
            try {
                String name = file.getName();
                UUID playerId = UUID.fromString(name.substring(0, name.length() - 4));
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                PlayerSnapshot snapshot = PlayerSnapshot.readFrom(yaml.getConfigurationSection("snapshot"));
                result.put(playerId, snapshot);
            } catch (Exception ex) {
                logger.severe(
                    "Snapshot pemain gagal dimuat ("
                        + file.getName()
                        + "): "
                        + ex.getMessage()
                        + ". File dipertahankan di disk."
                );
            }
        }

        return result;
    }

    void delete(UUID playerId) throws IOException {
        Files.deleteIfExists(this.fileFor(playerId).toPath());
    }

    private File fileFor(UUID playerId) {
        return new File(this.directory, playerId + ".yml");
    }
}
