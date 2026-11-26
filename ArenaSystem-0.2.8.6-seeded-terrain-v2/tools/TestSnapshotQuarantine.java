package id.hadziq.arenasystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone checks for the administrative-recovery snapshot handling: missing/corrupt snapshots, evidence preserved. */
public final class TestSnapshotQuarantine {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("arenasystem-quarantine-");
        try {
            SnapshotStore store = new SnapshotStore(dir.toFile());
            ArenaData arena = new ArenaData("arena1", "world", 1L, 10, 20, 64, 64, 64, 68, 60, 70, "tdm", 27, 40);
            Map<BlockPos, String> blocks = new LinkedHashMap<>();
            blocks.put(new BlockPos(1, 0, 1), "minecraft:stone");
            store.save(arena, blocks);

            // Both primary and backup corrupted: normal recovery cannot read the snapshot.
            File primary = store.fileFor("arena1");
            File backup = new File(primary.getParentFile(), "arena1.bak");
            Files.writeString(primary.toPath(), "corrupt-primary");
            Files.writeString(backup.toPath(), "corrupt-backup");
            boolean unreadable = false;
            try { store.loadSnapshot(arena); } catch (java.io.IOException expected) { unreadable = true; }
            check(unreadable, "corrupt primary+backup must be unreadable");

            // Administrative path: files are renamed, never deleted, and are no longer live snapshots.
            List<String> preserved = store.quarantine("arena1", 12345L);
            check(preserved.size() == 2, "primary and backup preserved: " + preserved);
            check(!store.exists("arena1"), "quarantined snapshot is no longer a live snapshot");
            check(Files.readString(new File(dir.toFile(), "snapshots/arena1.discarded-12345.dat.gz").toPath()).equals("corrupt-primary"),
                    "primary content preserved byte for byte");
            check(Files.readString(new File(dir.toFile(), "snapshots/arena1.discarded-12345.bak").toPath()).equals("corrupt-backup"),
                    "backup content preserved byte for byte");

            // Missing snapshot: quarantine is a no-op that reports nothing preserved and does not throw.
            check(store.quarantine("arena1", 99L).isEmpty(), "nothing to preserve when snapshot is missing");

            // Name can be reused after an explicit release without clobbering the preserved evidence.
            store.save(arena, blocks);
            check(store.loadSnapshot(arena).blocks().equals(blocks), "fresh snapshot after quarantine");
            check(new File(dir.toFile(), "snapshots/arena1.discarded-12345.dat.gz").isFile(), "evidence survives a new save");

            boolean rejected = false;
            try { store.quarantine("../evil", 1L); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid arena key rejected");
            System.out.println("PASS: snapshot quarantine preserves corrupt/missing-snapshot evidence and frees the live name.");
        } finally {
            TestAtomicFileWriter.deleteTree(dir);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
