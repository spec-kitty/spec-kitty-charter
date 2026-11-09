package ai.speckitty.charter.packs.infra.fetch.git.goodfixture;

import java.io.IOException;

/** Known-good control for {@code NetworkCodeBoundaryTest}: inside the git adapter's package a process may be started. */
public final class GitAdapterStartsProcess {

    private GitAdapterStartsProcess() {}

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
