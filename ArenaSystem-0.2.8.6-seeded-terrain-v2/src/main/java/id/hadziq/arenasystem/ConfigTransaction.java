package id.hadziq.arenasystem;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All-or-nothing mutation of config subtrees. The mutation is applied in memory,
 * persisted through a caller-supplied writer that may throw IOException, and rolled
 * back in memory if persisting fails. A caller that sees an exception can therefore
 * rely on both the file and the in-memory config still holding the previous state.
 */
final class ConfigTransaction {
    private ConfigTransaction() { }

    interface ConfigAccess {
        Object get(String path);
        void set(String path, Object value);
        /** Leaf values below root keyed by relative path, or null when root does not exist. */
        Map<String, Object> leafValues(String root);
    }

    @FunctionalInterface
    interface Persister {
        void persist() throws IOException;
    }

    static void commit(ConfigAccess config, Persister persister, Collection<String> roots, Runnable mutation) {
        Map<String, Map<String, Object>> before = new LinkedHashMap<>();
        for (String root : roots) {
            Map<String, Object> leaves = config.get(root) == null ? null : config.leafValues(root);
            before.put(root, leaves == null ? null : new LinkedHashMap<>(leaves));
        }
        try {
            mutation.run();
            persister.persist();
        } catch (IOException ex) {
            rollback(config, before);
            throw new IllegalStateException("Gagal menyimpan konfigurasi secara atomik: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            rollback(config, before);
            throw ex;
        }
    }

    private static void rollback(ConfigAccess config, Map<String, Map<String, Object>> before) {
        for (Map.Entry<String, Map<String, Object>> entry : before.entrySet()) {
            config.set(entry.getKey(), null);
            if (entry.getValue() == null) continue;
            for (Map.Entry<String, Object> leaf : entry.getValue().entrySet()) {
                config.set(entry.getKey() + "." + leaf.getKey(), leaf.getValue());
            }
        }
    }
}
