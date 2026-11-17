package id.hadziq.arenasystem.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Minimal Windows-safe replacement helper.
 *
 * The client mod launches this helper during JVM shutdown. This process waits
 * for the Minecraft PID, verifies the staged file, then replaces only the
 * expected mod JAR.
 */
public final class UpdaterMain {
    private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 180_000L;
    private static final long POLL_INTERVAL_MILLIS = 500L;
    private static final String WAIT_TIMEOUT_PROPERTY = "arenasystem.updater.waitTimeoutMillis";
    private static final String EXPECTED_JAR = "arenasystem-assassin-dagger-client.jar";

    private UpdaterMain() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception exception) {
            System.err.println("[ArenaSystem Updater] Update failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: <minecraft-pid> <target-jar> <staged-jar> <expected-sha256>");
        }

        long minecraftPid = Long.parseLong(args[0]);
        if (minecraftPid <= 0) {
            throw new IllegalArgumentException("Invalid Minecraft process ID.");
        }

        Path target = Path.of(args[1]).toAbsolutePath().normalize();
        Path staged = Path.of(args[2]).toAbsolutePath().normalize();
        String expectedHash = args[3].toLowerCase(java.util.Locale.ROOT);

        if (!expectedHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 value.");
        }

        validatePaths(target, staged);
        waitForProcessExit(minecraftPid);

        if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Staged update does not exist or is not a regular file.");
        }
        if (!sha256(staged).equals(expectedHash)) {
            throw new IOException("Staged update SHA-256 mismatch.");
        }

        Path backup = target.resolveSibling(target.getFileName() + ".backup");
        Path failed = target.resolveSibling(target.getFileName() + ".failed");

        Files.deleteIfExists(failed);
        boolean originalBackedUp = false;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
            originalBackedUp = true;
        }

        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(target).equals(expectedHash)) {
                throw new IOException("Installed JAR failed post-install verification.");
            }
            System.out.println("[ArenaSystem Updater] Update installed successfully.");
        } catch (Exception installFailure) {
            rollback(target, backup, failed, originalBackedUp, installFailure);
            throw installFailure;
        }
    }

    private static void rollback(
            Path target,
            Path backup,
            Path failed,
            boolean originalBackedUp,
            Exception installFailure) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.move(target, failed, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception quarantineFailure) {
                installFailure.addSuppressed(quarantineFailure);
            }
        }

        if (originalBackedUp && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                if (!sha256(target).equals(sha256(backup))) {
                    throw new IOException("Restored backup failed SHA-256 verification.");
                }
                System.err.println("[ArenaSystem Updater] Original JAR restored from backup.");
            } catch (Exception restoreFailure) {
                installFailure.addSuppressed(restoreFailure);
                System.err.println("[ArenaSystem Updater] Rollback failed; backup retained at: " + backup);
            }
        }
    }

    private static void validatePaths(Path target, Path staged) throws IOException {
        Path targetParent = target.getParent();
        Path stagedParent = staged.getParent();

        if (targetParent == null || stagedParent == null
                || !targetParent.equals(stagedParent)) {
            throw new IllegalArgumentException("Target and staged JAR must share one directory.");
        }
        if (!EXPECTED_JAR.equalsIgnoreCase(target.getFileName().toString())) {
            throw new IllegalArgumentException("Refusing to replace an unexpected JAR filename.");
        }
        if (!staged.getFileName().toString().equalsIgnoreCase(EXPECTED_JAR + ".pending")) {
            throw new IllegalArgumentException("Unexpected staged filename.");
        }
        if (Files.isSymbolicLink(targetParent)
                || Files.isSymbolicLink(target)
                || Files.isSymbolicLink(staged)) {
            throw new IllegalArgumentException("Symbolic-link paths are not allowed.");
        }
        if (!Files.isDirectory(targetParent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Mods directory does not exist or is not a real directory.");
        }

        Path modsDirectory = targetParent.toRealPath();
        if (!modsDirectory.getFileName().toString().equalsIgnoreCase("mods")) {
            throw new IllegalArgumentException("Target must be inside a Minecraft mods directory.");
        }
    }

    private static void waitForProcessExit(long pid) throws InterruptedException {
        long timeoutMillis = Long.getLong(WAIT_TIMEOUT_PROPERTY, DEFAULT_WAIT_TIMEOUT_MILLIS);
        if (timeoutMillis <= 0 || timeoutMillis > 900_000L) {
            throw new IllegalArgumentException("Invalid updater process wait timeout.");
        }

        long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long deadline = System.nanoTime() + timeoutNanos;
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IllegalStateException("Timed out waiting for Minecraft to close.");
            }
            long sleepNanos = Math.min(
                    remainingNanos,
                    java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS));
            java.util.concurrent.TimeUnit.NANOSECONDS.sleep(sleepNanos);
        }
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
