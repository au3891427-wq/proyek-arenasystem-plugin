package id.hadziq.arenasystem;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

// $VF: Compiled from ConfigTransaction.java
final class ConfigTransaction {
    private ConfigTransaction() {
    }

    static void commit(
        ConfigTransaction.ConfigAccess config,
        ConfigTransaction.Persister persister,
        Collection<String> roots,
        Runnable mutation
    ) {
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

    private static void rollback(ConfigTransaction.ConfigAccess config, Map<String, Map<String, Object>> before) {
        for (Entry<String, Map<String, Object>> entry : before.entrySet()) {
            config.set(entry.getKey(), null);
            if (entry.getValue() != null) {
                for (Entry<String, Object> leaf : entry.getValue().entrySet()) {
                    config.set(entry.getKey() + "." + leaf.getKey(), leaf.getValue());
                }
            }
        }
    }

    // $VF: Compiled from ConfigTransaction.java
    interface ConfigAccess {
        Object get(String var1);

        void set(String var1, Object var2);

        Map<String, Object> leafValues(String var1);
    }

    // $VF: Compiled from ConfigTransaction.java
    @FunctionalInterface
    interface Persister {
        void persist() throws IOException;
    }
}
