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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Windows-safe installer for versioned ArenaSystem client mod JARs. */
public final class UpdaterMain {
    private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 180_000L;
    private static final long POLL_INTERVAL_MILLIS = 500L;
    private static final String WAIT_TIMEOUT_PROPERTY = "arenasystem.updater.waitTimeoutMillis";
    private static final String UPDATE_LOCK_FILE = ".arenasystem-assassin-dagger-update.lock";
    private static final String LEGACY_JAR = "arenasystem-assassin-dagger-client.jar";
    private static final Pattern CLIENT_JAR = Pattern.compile("arenaSystem_client-\\d+\\.\\d+\\.\\d+\\.jar");

    private UpdaterMain() { }

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
                    "Usage: <minecraft-pid> <current-jar> <staged-new-jar> <expected-sha256>");
        }

        long minecraftPid = Long.parseLong(args[0]);
        if (minecraftPid <= 0) throw new IllegalArgumentException("Invalid Minecraft process ID.");

        Path currentJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path stagedJar = Path.of(args[2]).toAbsolutePath().normalize();
        String expectedHash = args[3].toLowerCase(Locale.ROOT);
        if (!expectedHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 value.");
        }

        Path newJar = deriveNewJarPath(currentJar, stagedJar);
        validatePaths(currentJar, stagedJar, newJar);
        waitForProcessExit(minecraftPid);

        Path lockPath = currentJar.getParent().resolve(UPDATE_LOCK_FILE);
        if (Files.isSymbolicLink(lockPath)) {
            throw new IOException("Updater lock path must not be a symbolic link.");
        }

        try (FileChannel lockChannel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = waitForUpdateLock(lockChannel)) {
            if (!Files.isRegularFile(stagedJar, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Staged update is missing or is not a regular file.");
            }
            if (!sha256(stagedJar).equals(expectedHash)) {
                throw new IOException("Staged update SHA-256 mismatch.");
            }
            if (!newJar.equals(currentJar) && Files.exists(newJar, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The new versioned JAR already exists: " + newJar.getFileName());
            }

            Path backup = currentJar.resolveSibling(currentJar.getFileName() + ".backup");
            String originalHash = sha256(currentJar);
            Files.copy(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(backup).equals(originalHash)) {
                throw new IOException("Backup verification failed; the installed mod was not changed.");
            }

            try {
                if (newJar.equals(currentJar)) {
                    Files.move(stagedJar, newJar, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    moveWithoutReplacing(stagedJar, newJar);
                }
                if (!sha256(newJar).equals(expectedHash)) {
                    throw new IOException("Installed JAR failed post-install SHA-256 verification.");
                }
                if (!newJar.equals(currentJar)) Files.delete(currentJar);
                System.out.println("[ArenaSystem Updater] Installed " + newJar.getFileName()
                        + "; previous JAR backup: " + backup.getFileName());
            } catch (Exception installFailure) {
                rollback(currentJar, newJar, backup, originalHash, installFailure);
                throw installFailure;
            }
        }
    }

    private static Path deriveNewJarPath(Path currentJar, Path stagedJar) {
        String stagedName = stagedJar.getFileName().toString();
        String suffix = ".pending";
        if (!stagedName.endsWith(suffix)) {
            throw new IllegalArgumentException("Staged JAR must end with .pending.");
        }
        if (LEGACY_JAR.equalsIgnoreCase(currentJar.getFileName().toString())
                && stagedName.equalsIgnoreCase(currentJar.getFileName() + suffix)) {
            return currentJar;
        }
        return stagedJar.resolveSibling(stagedName.substring(0, stagedName.length() - suffix.length()));
    }

    private static void validatePaths(Path currentJar, Path stagedJar, Path newJar) throws IOException {
        Path parent = currentJar.getParent();
        if (parent == null || stagedJar.getParent() == null || newJar.getParent() == null
                || !parent.equals(stagedJar.getParent()) || !parent.equals(newJar.getParent())) {
            throw new IllegalArgumentException("Current, staged, and new JARs must share one directory.");
        }
        boolean legacyInPlaceUpdate = LEGACY_JAR.equalsIgnoreCase(currentJar.getFileName().toString())
                && currentJar.equals(newJar);
        if ((!legacyInPlaceUpdate && !CLIENT_JAR.matcher(currentJar.getFileName().toString()).matches())
                || (!legacyInPlaceUpdate && !CLIENT_JAR.matcher(newJar.getFileName().toString()).matches())) {
            throw new IllegalArgumentException("Refusing an unexpected client JAR filename.");
        }
        if (!stagedJar.getFileName().toString().equals(newJar.getFileName() + ".pending")) {
            throw new IllegalArgumentException("Staged filename does not match the new versioned JAR.");
        }
        Path lockFile = parent.resolve(UPDATE_LOCK_FILE);
        if (Files.isSymbolicLink(parent) || Files.isSymbolicLink(currentJar)
                || Files.isSymbolicLink(stagedJar) || Files.isSymbolicLink(newJar)
                || Files.isSymbolicLink(lockFile)) {
            throw new IllegalArgumentException("Symbolic-link paths are not allowed.");
        }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || !parent.toRealPath().getFileName().toString().equalsIgnoreCase("mods")) {
            throw new IllegalArgumentException("Target must be inside a real Minecraft mods directory.");
        }
        if (!Files.isRegularFile(currentJar, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Current client JAR must exist as a regular file.");
        }
    }

    private static void moveWithoutReplacing(Path source, Path destination) throws IOException {
        Files.move(source, destination);
    }

    private static void rollback(Path currentJar, Path newJar, Path backup,
                                 String originalHash, Exception installFailure) {
        try {
            if (Files.exists(newJar, LinkOption.NOFOLLOW_LINKS)) Files.delete(newJar);
            if (Files.isRegularFile(currentJar, LinkOption.NOFOLLOW_LINKS)
                    && sha256(currentJar).equals(originalHash)) {
                System.err.println("[ArenaSystem Updater] Previous JAR remained intact.");
                return;
            }
            if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                    || !sha256(backup).equals(originalHash)) {
                throw new IOException("Verified backup is unavailable; retained at " + backup);
            }
            Files.copy(backup, currentJar, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(currentJar).equals(originalHash)) {
                throw new IOException("Restored backup failed SHA-256 verification.");
            }
            System.err.println("[ArenaSystem Updater] Previous JAR restored from backup.");
        } catch (Exception rollbackFailure) {
            installFailure.addSuppressed(rollbackFailure);
            System.err.println("[ArenaSystem Updater] Rollback failed; backup retained at " + backup);
        }
    }

    private static FileLock waitForUpdateLock(FileChannel channel)
            throws IOException, InterruptedException {
        long timeoutMillis = readWaitTimeout();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException ignored) {
                // Another updater process owns the lock.
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Timed out waiting for another updater instance.");
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining,
                    TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS)));
        }
    }

    private static void waitForProcessExit(long pid) throws InterruptedException {
        long timeoutMillis = readWaitTimeout();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IllegalStateException("Timed out waiting for Minecraft to close.");
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining,
                    TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS)));
        }
    }

    private static long readWaitTimeout() {
        long timeout = Long.getLong(WAIT_TIMEOUT_PROPERTY, DEFAULT_WAIT_TIMEOUT_MILLIS);
        if (timeout <= 0 || timeout > 900_000L) {
            throw new IllegalArgumentException("Invalid updater process wait timeout.");
        }
        return timeout;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
    }
}
