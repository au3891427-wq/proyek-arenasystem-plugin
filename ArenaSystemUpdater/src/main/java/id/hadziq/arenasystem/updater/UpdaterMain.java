package id.hadziq.arenasystem.updater;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Minimal Windows-safe replacement helper.
 *
 * The client mod launches this helper during JVM shutdown. The staged filename
 * determines the destination filename:
 *
 *   <minecraft-pid> <current-mod-jar> <new-mod-jar.pending> <expected-sha256>
 *
 * If the current and destination paths are equal, the helper performs an
 * in-place replacement. Otherwise, it installs the new versioned filename and
 * removes the old JAR only after the new JAR has passed hash verification.
 */
public final class UpdaterMain {
    private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 180_000L;
    private static final long POLL_INTERVAL_MILLIS = 500L;
    private static final String WAIT_TIMEOUT_PROPERTY = "arenasystem.updater.waitTimeoutMillis";
    private static final String UPDATE_LOCK_FILE = ".arenasystem-assassin-dagger-update.lock";
    private static final String PENDING_SUFFIX = ".pending";
    private static final String JAR_SUFFIX = ".jar";

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
                    "Usage: <minecraft-pid> <current-mod-jar> <new-mod-jar.pending> <expected-sha256>");
        }

        long minecraftPid = Long.parseLong(args[0]);
        if (minecraftPid <= 0) {
            throw new IllegalArgumentException("Invalid Minecraft process ID.");
        }

        Path currentJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path staged = Path.of(args[2]).toAbsolutePath().normalize();
        String expectedHash = args[3].toLowerCase(Locale.ROOT);

        if (!expectedHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 value.");
        }

        Path destination = destinationFromStage(staged);
        validatePaths(currentJar, destination, staged);
        waitForProcessExit(minecraftPid);

        Path lockPath = currentJar.getParent().resolve(UPDATE_LOCK_FILE);
        try (FileChannel lockChannel = FileChannel.open(
                    lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = waitForUpdateLock(lockChannel)) {
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Staged update does not exist or is not a regular file.");
            }
            if (!sha256(staged).equals(expectedHash)) {
                throw new IOException("Staged update SHA-256 mismatch.");
            }

            if (currentJar.equals(destination)) {
                installInPlace(currentJar, staged, expectedHash);
            } else {
                installVersioned(currentJar, destination, staged, expectedHash);
            }
        }
    }

    private static Path destinationFromStage(Path staged) {
        String stagedName = staged.getFileName().toString();
        if (!stagedName.toLowerCase(Locale.ROOT).endsWith(PENDING_SUFFIX)) {
            throw new IllegalArgumentException("Staged JAR filename must end with " + PENDING_SUFFIX + ".");
        }

        String destinationName = stagedName.substring(0, stagedName.length() - PENDING_SUFFIX.length());
        if (destinationName.isBlank()) {
            throw new IllegalArgumentException("Staged JAR does not contain a destination filename.");
        }
        return staged.resolveSibling(destinationName).toAbsolutePath().normalize();
    }

    private static void validatePaths(Path currentJar, Path destination, Path staged) throws IOException {
        Path parent = currentJar.getParent();
        if (parent == null || destination.getParent() == null || staged.getParent() == null
                || !parent.equals(destination.getParent()) || !parent.equals(staged.getParent())) {
            throw new IllegalArgumentException("Current, destination, and staged JARs must share one directory.");
        }

        if (!endsWithIgnoreCase(currentJar.getFileName().toString(), JAR_SUFFIX)
                || !endsWithIgnoreCase(destination.getFileName().toString(), JAR_SUFFIX)) {
            throw new IllegalArgumentException("Current and destination files must use the .jar extension.");
        }

        Path lockFile = parent.resolve(UPDATE_LOCK_FILE);
        if (Files.isSymbolicLink(parent)
                || Files.isSymbolicLink(currentJar)
                || Files.isSymbolicLink(destination)
                || Files.isSymbolicLink(staged)
                || Files.isSymbolicLink(lockFile)) {
            throw new IllegalArgumentException("Symbolic-link paths are not allowed.");
        }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Mods directory does not exist or is not a real directory.");
        }

        Path modsDirectory = parent.toRealPath();
        if (!modsDirectory.getFileName().toString().equalsIgnoreCase("mods")) {
            throw new IllegalArgumentException("Target must be inside a Minecraft mods directory.");
        }
        if (!Files.isRegularFile(currentJar, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Current mod JAR must already exist as a regular file.");
        }
        if (!currentJar.equals(destination) && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Destination already exists; refusing to overwrite another file.");
        }
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.toLowerCase(Locale.ROOT).endsWith(suffix);
    }

    private static void installInPlace(Path target, Path staged, String expectedHash) throws Exception {
        Path backup = target.resolveSibling(target.getFileName() + ".backup");
        Path failed = target.resolveSibling(target.getFileName() + ".failed");

        Files.deleteIfExists(failed);
        String originalHash = sha256(target);
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        if (!sha256(backup).equals(originalHash)) {
            throw new IOException("Backup JAR failed SHA-256 verification; original JAR was not changed.");
        }

        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(target).equals(expectedHash)) {
                throw new IOException("Installed JAR failed post-install verification.");
            }
            System.out.println("[ArenaSystem Updater] Update installed successfully.");
        } catch (Exception installFailure) {
            rollback(target, backup, failed, true, installFailure);
            throw installFailure;
        }
    }

    private static void installVersioned(
            Path currentJar, Path destination, Path staged, String expectedHash) throws Exception {
        String destinationStem = destination.getFileName().toString();
        destinationStem = destinationStem.substring(0, destinationStem.length() - JAR_SUFFIX.length());
        Path backup = currentJar.resolveSibling(
                currentJar.getFileName() + ".backup-" + destinationStem);
        Path failed = destination.resolveSibling(destination.getFileName() + ".failed");

        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Version-specific backup already exists; refusing to overwrite it: "
                    + backup.getFileName());
        }

        String originalHash = sha256(currentJar);
        Files.copy(currentJar, backup);
        if (!sha256(backup).equals(originalHash)) {
            Files.deleteIfExists(backup);
            throw new IOException("Backup JAR failed SHA-256 verification; original JAR was not changed.");
        }

        try {
            // No REPLACE_EXISTING: an unexpected destination must never be overwritten.
            Files.move(staged, destination);
            if (!sha256(destination).equals(expectedHash)) {
                throw new IOException("New versioned JAR failed post-install verification.");
            }

            // Keep the old JAR until the new one is present and verified.
            Files.delete(currentJar);
            System.out.println("[ArenaSystem Updater] Versioned update installed successfully: "
                    + destination.getFileName());
        } catch (Exception installFailure) {
            // The current JAR is still present unless deletion completed. In the latter
            // case, Files.delete returned normally and no later operation can fail here.
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.move(destination, failed, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception quarantineFailure) {
                    installFailure.addSuppressed(quarantineFailure);
                    try {
                        Files.deleteIfExists(destination);
                    } catch (Exception cleanupFailure) {
                        installFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw installFailure;
        }
    }

    private static void rollback(
            Path target,
            Path backup,
            Path failed,
            boolean originalBackedUp,
            Exception installFailure) {
        if (!originalBackedUp || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            String backupHash = sha256(backup);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && sha256(target).equals(backupHash)) {
                System.err.println("[ArenaSystem Updater] Original JAR remained intact; backup verified.");
                return;
            }

            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.move(target, failed, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception quarantineFailure) {
                    installFailure.addSuppressed(quarantineFailure);
                }
            }

            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(target).equals(backupHash)) {
                throw new IOException("Restored backup failed SHA-256 verification.");
            }
            System.err.println("[ArenaSystem Updater] Original JAR restored from backup.");
        } catch (Exception restoreFailure) {
            installFailure.addSuppressed(restoreFailure);
            System.err.println("[ArenaSystem Updater] Rollback failed; backup retained at: " + backup);
        }
    }

    private static FileLock waitForUpdateLock(FileChannel channel)
            throws IOException, InterruptedException {
        long timeoutMillis = Long.getLong(WAIT_TIMEOUT_PROPERTY, DEFAULT_WAIT_TIMEOUT_MILLIS);
        if (timeoutMillis <= 0 || timeoutMillis > 900_000L) {
            throw new IllegalArgumentException("Invalid updater lock timeout.");
        }

        long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException ignored) {
                // Another updater in this JVM already owns the lock.
            }

            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IllegalStateException("Timed out waiting for another updater instance.");
            }
            long sleepNanos = Math.min(
                    remainingNanos,
                    java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS));
            java.util.concurrent.TimeUnit.NANOSECONDS.sleep(sleepNanos);
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
