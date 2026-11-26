package id.hadziq.arenasystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// $VF: Compiled from AtomicFileWriter.java
final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    static void write(File target, byte[] content) throws IOException {
        if (target != null && content != null) {
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent == null) {
                throw new IOException("Target has no parent directory: " + target);
            }

            File temporary = new File(parent, target.getName() + ".tmp");

            try {
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(content);
                    output.flush();
                    output.getFD().sync();
                }

                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException ex) {
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
        } else {
            throw new IllegalArgumentException("target and content are required");
        }
    }
}
