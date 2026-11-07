package ai.speckitty.charter.kernel.transport;

/**
 * The service cannot take this request now and the client should send it again shortly: {@code 503 Service Unavailable}
 * with {@code Retry-After}, a plain {@code Problem} with no code (ADR-0065 D12, NFR-009). The pack registry lock that
 * was not free in time and a full registration queue are both this, so the inbound edge answers either in one place and
 * a registration job that meets it ends {@code refused} with the same answer.
 */
public class RetryLaterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int retryAfterSeconds;

    /**
     * A refusal to take the request now.
     *
     * @param message the detail sentence for the {@code Problem} body
     * @param retryAfterSeconds the seconds a client waits before it sends the request again; at least 1
     */
    public RetryLaterException(String message, int retryAfterSeconds) {
        super(message);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("Retry-After is at least one second");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * The seconds a client waits before it sends the request again.
     *
     * @return the {@code Retry-After} value, at least 1
     */
    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
