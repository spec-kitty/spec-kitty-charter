package ai.speckitty.charter.kernel.config;

import java.io.Serial;

/**
 * The service refuses to start on its configuration: a setting is missing, malformed or not allowed where it is set. The
 * message is one line that names the setting and the fix. The deployable's failure analyzer reports the refusal with
 * that message and without a stack trace (#454); a refusal raised before Spring starts is printed as that one line. A
 * failure of the build or of the service itself is no refusal and keeps its stack trace.
 */
public final class StartupRefusal extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * A refusal.
     *
     * @param message one line that names the setting and how to fix it
     */
    public StartupRefusal(String message) {
        super(message);
    }
}
