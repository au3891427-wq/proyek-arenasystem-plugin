package id.hadziq.arenasystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone regression checks for SnapshotStore; run without a Minecraft server. */
public final class TestSnapshotStore {
    private TestSnapshotStore() { }

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("arenasystem-snapshot-test-");
        try {
            SnapshotStore store = new SnapshotStore(temporaryDirectory.toFile());
            ArenaData arena = arena("arena1", "world", 120, 70, -45);
            Map<BlockPos, String> original = new LinkedHashMap<>();
            original.put(new BlockPos(-2, 0, 3), "minecraft:stone");
            original.put(new BlockPos(4, -1, -5), "minecraft:oak_planks[axis=y]");

            store.save(arena, original);
            SnapshotStore.LoadedSnapshot first = store.loadSnapshot(arena);
            assertEquals(original, first.blocks(), "ARN3 round-trip");
            assertEquals(70, first.centerY(), "ARN3 absolute Y origin");
            assertTrue(!first.legacy(), "ARN3 must not be marked legacy");
            assertTrue(Files.exists(store.fileFor(arena.name).toPath()), "primary snapshot exists");
            assertTrue(Files.exists(store.fileFor(arena.name).toPath().resolveSibling("arena1.bak")),
                    "verified backup exists after first save");

            // The stored Y origin is authoritative during interrupted regeneration;
            // horizontal origin and world must still match to avoid restoring elsewhere.
            ArenaData staleConfig = arena("arena1", "world", 120, 73, -45);
            assertEquals(70, store.loadSnapshot(staleConfig).centerY(), "snapshot Y survives stale config");
            expectIOException(() -> store.loadSnapshot(arena("arena1", "other-world", 120, 70, -45)),
                    "wrong world must be rejected");
            expectIOException(() -> store.loadSnapshot(arena("arena1", "world", 121, 70, -45)),
                    "wrong X origin must be rejected");

            // Corrupt primary; the byte-identical committed backup must recover it.
            Files.writeString(store.fileFor(arena.name).toPath(), "corrupt-primary");
            SnapshotStore.LoadedSnapshot recovered = store.loadSnapshot(arena);
            assertEquals(original, recovered.blocks(), "backup fallback");
            assertEquals(70, recovered.centerY(), "backup origin");

            // A successful later save updates both committed files to the same state.
            Map<BlockPos, String> updated = Map.of(new BlockPos(0, 1, 0), "minecraft:dirt");
            store.save(arena, updated);
            assertEquals(updated, store.loadSnapshot(arena).blocks(), "save after corrupt-primary recovery");
            Files.writeString(store.fileFor(arena.name).toPath(), "corrupt-again");
            assertEquals(updated, store.loadSnapshot(arena).blocks(), "refreshed backup fallback");
            Files.delete(store.fileFor(arena.name).toPath());
            assertEquals(updated, store.loadSnapshot(arena).blocks(), "missing-primary fallback");

            // ARN2 remains readable. Its missing origin is supplied by legacy ArenaData.
            store.delete(arena.name);
            store.save(arena.name, original);
            SnapshotStore.LoadedSnapshot legacy = store.loadSnapshot(arena);
            assertTrue(legacy.legacy(), "ARN2 must be marked legacy");
            assertEquals(70, legacy.centerY(), "ARN2 uses config Y origin");
            assertEquals(original, legacy.blocks(), "ARN2 compatibility");

            store.delete(arena.name);
            assertTrue(!store.exists(arena.name), "delete removes primary and backup");
            System.out.println("PASS: SnapshotStore ARN2/ARN3, origin validation, backup fallback, overwrite, and delete.");
        } finally {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); }
                            catch (IOException ex) { throw new RuntimeException(ex); }
                        });
            }
        }
    }

    private static ArenaData arena(String name, String world, int x, int y, int z) {
        return new ArenaData(name, world, 123L, x, z, y, y, y, y, y, y,
                "tdm", 27, 40);
    }

    private static void expectIOException(IoRunnable action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws Exception;
    }
}
