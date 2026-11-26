package id.hadziq.arenasystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone checks for ConfigTransaction using a Map-backed fake config. The lifecycle
 * "pattern" tests mirror the order used by ArenaSystemPlugin (persist state first; only
 * then touch the world / delete the snapshot). They test the pattern with fakes, not the
 * plugin class itself, which needs the Paper API.
 */
public final class TestConfigTransaction {
    /** Flat dotted-key fake of a YAML config. */
    static final class FakeConfig implements ConfigTransaction.ConfigAccess {
        final Map<String, Object> values = new LinkedHashMap<>();
        public Object get(String path) {
            if (values.containsKey(path)) return values.get(path);
            for (String key : values.keySet()) if (key.startsWith(path + ".")) return "<section>";
            return null;
        }
        public void set(String path, Object value) {
            values.keySet().removeIf(k -> k.equals(path) || k.startsWith(path + "."));
            if (value != null) values.put(path, value);
        }
        public Map<String, Object> leafValues(String root) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getKey().startsWith(root + ".")) out.put(e.getKey().substring(root.length() + 1), e.getValue());
            }
            return out.isEmpty() ? null : out;
        }
    }

    public static void main(String[] args) throws Exception {
        testSuccessPersistsState();
        testFailureRollsBackExistingArena();
        testFailureRollsBackNewArena();
        testMutationExceptionRollsBack();
        testFailedStateBlocksWorldMutationAndSnapshotDelete();
        System.out.println("PASS: ConfigTransaction commit, rollback (existing/new root), and persist-before-mutate ordering.");
    }

    static void testSuccessPersistsState() {
        FakeConfig cfg = new FakeConfig();
        int[] writes = {0};
        ConfigTransaction.commit(cfg, () -> writes[0]++, List.of("arenas.a1"), () -> {
            cfg.set("arenas.a1.complete", false);
            cfg.set("arenas.a1.generation-state", "BUILDING");
        });
        check(writes[0] == 1, "persist called exactly once");
        check("BUILDING".equals(cfg.values.get("arenas.a1.generation-state")), "state kept after success");
    }

    static void testFailureRollsBackExistingArena() {
        FakeConfig cfg = new FakeConfig();
        cfg.set("arenas.a1.complete", true);
        cfg.set("arenas.a1.generation-state", "COMPLETE");
        cfg.set("arenas.a1.team-count", 3);
        expectIllegalState(() -> ConfigTransaction.commit(cfg, () -> { throw new IOException("disk full"); },
                List.of("arenas.a1"), () -> {
                    cfg.set("arenas.a1.complete", false);
                    cfg.set("arenas.a1.generation-state", "RESTORING");
                    cfg.set("arenas.a1.extra", 1);
                }), "failed persist must throw");
        check(Boolean.TRUE.equals(cfg.values.get("arenas.a1.complete")), "complete flag rolled back");
        check("COMPLETE".equals(cfg.values.get("arenas.a1.generation-state")), "state rolled back");
        check(Integer.valueOf(3).equals(cfg.values.get("arenas.a1.team-count")), "unrelated leaf preserved");
        check(!cfg.values.containsKey("arenas.a1.extra"), "newly added leaf removed");
    }

    static void testFailureRollsBackNewArena() {
        FakeConfig cfg = new FakeConfig();
        expectIllegalState(() -> ConfigTransaction.commit(cfg, () -> { throw new IOException("read-only"); },
                List.of("arenas.fresh"), () -> cfg.set("arenas.fresh.generation-state", "BUILDING")),
                "failed persist must throw");
        check(cfg.get("arenas.fresh") == null, "new arena root must not exist after rollback");
    }

    static void testMutationExceptionRollsBack() {
        FakeConfig cfg = new FakeConfig();
        cfg.set("arenas.a1.mode", "tdm");
        boolean threw = false;
        try {
            ConfigTransaction.commit(cfg, () -> { }, List.of("arenas.a1"), () -> {
                cfg.set("arenas.a1.mode", "relic_rush");
                throw new IllegalArgumentException("boom");
            });
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "mutation exception propagates");
        check("tdm".equals(cfg.values.get("arenas.a1.mode")), "mutation rolled back");
    }

    /** Mirrors regenerate/delete/recover: world and snapshot may only change after state is durable. */
    static void testFailedStateBlocksWorldMutationAndSnapshotDelete() throws Exception {
        Path dir = Files.createTempDirectory("arenasystem-lifecycle-");
        try {
            File snapshot = new File(dir.toFile(), "a1.dat.gz");
            Files.writeString(snapshot.toPath(), "baseline");
            int[] worldBlocksChanged = {0};

            // Persist fails -> world untouched, snapshot kept, memory state unchanged.
            FakeConfig failing = new FakeConfig();
            failing.set("arenas.a1.generation-state", "COMPLETE");
            boolean started = lifecycle(failing, () -> { throw new IOException("disk full"); },
                    snapshot, worldBlocksChanged);
            check(!started, "lifecycle must report not started");
            check(worldBlocksChanged[0] == 0, "world must not change when state cannot be saved");
            check(snapshot.exists(), "snapshot must be kept when state cannot be saved");
            check("COMPLETE".equals(failing.values.get("arenas.a1.generation-state")), "memory state not left half-changed");

            // Persist succeeds -> world changes, then snapshot is removed last.
            FakeConfig working = new FakeConfig();
            working.set("arenas.a1.generation-state", "COMPLETE");
            started = lifecycle(working, () -> { }, snapshot, worldBlocksChanged);
            check(started, "lifecycle starts when state saves");
            check(worldBlocksChanged[0] == 1, "world changed after durable state");
            check(!snapshot.exists(), "snapshot removed only after durable final state");
        } finally {
            TestAtomicFileWriter.deleteTree(dir);
        }
    }

    private static boolean lifecycle(FakeConfig cfg, ConfigTransaction.Persister persister,
                                     File snapshot, int[] worldBlocksChanged) throws IOException {
        try {
            ConfigTransaction.commit(cfg, persister, List.of("arenas.a1"),
                    () -> cfg.set("arenas.a1.generation-state", "RESTORING"));
        } catch (IllegalStateException ex) {
            return false;
        }
        worldBlocksChanged[0]++;                                   // restore blocks
        ConfigTransaction.commit(cfg, persister, List.of("arenas.a1"), () -> cfg.set("arenas.a1", null)); // final state
        Files.deleteIfExists(snapshot.toPath());                   // snapshot goes last
        return true;
    }

    static void expectIllegalState(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
