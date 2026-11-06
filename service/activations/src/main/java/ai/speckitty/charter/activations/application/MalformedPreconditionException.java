package ai.speckitty.charter.activations.application;

/**
 * A write whose preconditions cannot be evaluated (J10): {@code If-Match} and {@code If-None-Match} on one request, or
 * an {@code If-None-Match} other than {@code *} on a write. It is the request's fault and no charter refusal: the
 * inbound edge answers it with a plain {@code 400 Problem}, without a code.
 */
public final class MalformedPreconditionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A malformed precondition.
     *
     * @param message the detail sentence for the {@code Problem} body
     */
    public MalformedPreconditionException(String message) {
        super(message);
    }
}
