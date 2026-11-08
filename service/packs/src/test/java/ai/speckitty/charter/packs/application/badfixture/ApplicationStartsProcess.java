package ai.speckitty.charter.packs.application.badfixture;

import java.io.IOException;

/** Known-bad control for {@code NetworkCodeBoundaryTest}: a class outside the git adapter that starts a process. */
public final class ApplicationStartsProcess {

    private ApplicationStartsProcess() {}

    /**
     * Starts a process; never called.
     *
     * @return the process
     * @throws IOException never, the method is not called
     */
    public static Process start() throws IOException {
        return new ProcessBuilder("true").start();
    }
}
