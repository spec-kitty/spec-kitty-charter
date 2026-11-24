package ai.speckitty.charter.testfixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * Named pipes for the special-file tests (operator ruling R-ad): the JDK has no call that makes one, so the system's
 * {@code mkfifo} makes it, at test time, since git cannot carry one in a fixture. Where the operating system cannot (no
 * {@code mkfifo}, a file system without FIFOs, Windows), {@link #make} answers false and the test skips itself.
 */
public final class Fifos {

    private static final long SECONDS = 30;

    private Fifos() {}

    /**
     * Makes a named pipe at {@code fifo}, its parent directories first.
     *
     * @param fifo where the pipe stands; nothing may stand there yet
     * @return true when a FIFO now stands there; false when the operating system cannot make one
     * @throws IOException when the parent directories cannot be made
     */
    public static boolean make(Path fifo) throws IOException {
        Path parent = fifo.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Process process = new ProcessBuilder("mkfifo", fifo.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                return false;
            }
        } catch (IOException noMkfifo) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        return Files.readAttributes(fifo, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                .isOther();
    }
}
