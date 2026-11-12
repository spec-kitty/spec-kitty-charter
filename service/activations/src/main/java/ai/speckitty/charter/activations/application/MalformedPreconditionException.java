package ai.speckitty.charter.activations.application;

import org.jspecify.annotations.Nullable;

/**
 * A write whose preconditions cannot be evaluated (J10): {@code If-Match} and {@code If-None-Match} on one request, or
 * an {@code If-None-Match} other than {@code *} on a write. It is the request's fault and no charter refusal: the
 * inbound edge answers it with a {@code 400 Problem} without a code, worded as every parameter 400 is (#411).
 */
public final class MalformedPreconditionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The {@code If-None-Match} that is not {@code *}; null when both validators are sent. */
    private final @Nullable String ifNoneMatch;

    private MalformedPreconditionException(String message, @Nullable String ifNoneMatch) {
        super(message);
        this.ifNoneMatch = ifNoneMatch;
    }

    /**
     * A write that sends {@code If-Match} and {@code If-None-Match}.
     *
     * @return the failure
     */
    public static MalformedPreconditionException bothSent() {
        return new MalformedPreconditionException("A write sends both If-Match and If-None-Match.", null);
    }

    /**
     * A write whose {@code If-None-Match} is not {@code *}.
     *
     * @param ifNoneMatch the header's value
     * @return the failure
     */
    public static MalformedPreconditionException notAWildcard(String ifNoneMatch) {
        return new MalformedPreconditionException("A write's If-None-Match is not *.", ifNoneMatch);
    }

    /**
     * The {@code If-None-Match} that is not {@code *}.
     *
     * @return the value; null when the failure is that both validators are sent
     */
    public @Nullable String ifNoneMatch() {
        return ifNoneMatch;
    }
}
