package id.hadziq.arenasystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe whole-file replacement. The content is written to a sibling temporary
 * file, forced to disk, and then moved over the target. The target therefore holds
 * either its previous complete content or the new complete content, never a prefix.
 * Every failure is reported to the caller as an IOException.
 */
final class AtomicFileWriter {
    private AtomicFileWriter() { }

    static void write(File target, byte[] content) throws IOException {
        if (target == null || content == null) throw new IllegalArgumentException("target and content are required");
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Target has no parent directory: " + target);
        File temporary = new File(parent, target.getName() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(content);
                output.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                // Last resort for file systems without atomic rename. The temp file is
                // already complete and synced, so a failure here still leaves it intact.
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            throw ex;
        }
    }
}
