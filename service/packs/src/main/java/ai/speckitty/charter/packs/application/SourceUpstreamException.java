package ai.speckitty.charter.packs.application;

import java.io.Serial;

/**
 * The source could not be read for a reason that is not the request's: the upstream failed ({@code 502}) or did not
 * answer in time ({@code 504}). Both end a registration {@code refused} as a plain problem with no code. The message is
 * fixed and carries nothing of the upstream.
 */
public final class SourceUpstreamException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean timedOut;

    private SourceUpstreamException(String message, boolean timedOut) {
        super(message);
        this.timedOut = timedOut;
    }

    /**
     * The upstream failed: it could not be reached, or answered with a status that is not a success.
     *
     * @return the exception
     */
    public static SourceUpstreamException failed() {
        return new SourceUpstreamException("The source could not be read: the upstream failed.", false);
    }

    /**
     * The upstream did not answer within the deadline.
     *
     * @return the exception
     */
    public static SourceUpstreamException deadline() {
        return new SourceUpstreamException("The source could not be read: the upstream did not answer in time.", true);
    }

    /**
     * Whether the upstream timed out rather than failed.
     *
     * @return true for a timeout, which is a {@code 504}
     */
    public boolean timedOut() {
        return timedOut;
    }
}
