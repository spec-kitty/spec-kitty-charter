package ai.speckitty.charter.packs.application.badfixture;

import java.io.IOException;
import java.net.Socket;

/** Known-bad control for {@code NetworkCodeBoundaryTest}: an application class that reaches the network itself. */
public final class ApplicationOpensSocket {

    private ApplicationOpensSocket() {}

    /**
     * Opens a socket; never called.
     *
     * @return a socket
     * @throws IOException never, the method is not called
     */
    public static Socket open() throws IOException {
        return new Socket();
    }
}
