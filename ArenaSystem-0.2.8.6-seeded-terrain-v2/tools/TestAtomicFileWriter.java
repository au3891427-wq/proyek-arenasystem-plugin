package id.hadziq.arenasystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone checks for AtomicFileWriter (no Minecraft server needed). */
public final class TestAtomicFileWriter {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("arenasystem-atomic-");
        try {
            File target = new File(dir.toFile(), "config.yml");

            // Config write succeeds: content is exactly the new content and no temp file remains.
            AtomicFileWriter.write(target, "v1".getBytes(StandardCharsets.UTF_8));
            check(Files.readString(target.toPath()).equals("v1"), "first write");
            AtomicFileWriter.write(target, "v2-longer-content".getBytes(StandardCharsets.UTF_8));
            check(Files.readString(target.toPath()).equals("v2-longer-content"), "replace write");
            check(!new File(dir.toFile(), "config.yml.tmp").exists(), "no leftover temp after success");

            // Config write fails (temp path blocked by a directory): IOException, target untouched.
            File blocker = new File(dir.toFile(), "config.yml.tmp");
            check(blocker.mkdir(), "create blocker directory");
            boolean threw = false;
            try {
                AtomicFileWriter.write(target, "SHOULD-NOT-APPEAR".getBytes(StandardCharsets.UTF_8));
            } catch (IOException expected) {
                threw = true;
            }
            check(threw, "failed write must surface IOException");
            check(Files.readString(target.toPath()).equals("v2-longer-content"), "target unchanged after failed write");
            check(!new File(dir.toFile(), "config.yml.tmp.x").exists(), "no stray files created");

            // Failure when the parent directory does not exist.
            threw = false;
            try {
                AtomicFileWriter.write(new File(dir.toFile(), "missing/sub/config.yml"), new byte[]{1});
            } catch (IOException expected) {
                threw = true;
            }
            check(threw, "missing parent must surface IOException");
            System.out.println("PASS: AtomicFileWriter success, replace, failure-preserves-target, error propagation.");
        } finally {
            deleteTree(dir);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ex) { throw new RuntimeException(ex); }
            });
        }
    }
}
