package ai.speckitty.charter.packs.application;

/**
 * An optimistic-concurrency precondition failure of a registration write (ADR-0026 Decision 5, FR-012). It is
 * deliberately <strong>not</strong> a {@link ai.speckitty.charter.kernel.domain.CharterRefusal}: the {@code 412} and
 * {@code 428} preconditions are HTTP preconditions carried as a plain {@code Problem} body (ADR-0020, research D4),
 * never a {@code RefusalCode}. The inbound edge maps {@link Kind#STALE} to {@code 412 Precondition Failed} and
 * {@link Kind#MISSING} to {@code 428 Precondition Required}.
 */
public final class PreconditionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Which precondition was not met. */
    public enum Kind {
        /** An {@code If-Match} was supplied but does not name the current revision: {@code 412 Precondition Failed}. */
        STALE,
        /** A write that requires a precondition supplied none: {@code 428 Precondition Required}. */
        MISSING
    }

    /** Which precondition was not met. */
    private final Kind kind;

    /**
     * A precondition failure.
     *
     * @param kind which precondition was not met
     * @param message the detail sentence for the {@code Problem} body
     */
    public PreconditionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Which precondition was not met.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }
}
