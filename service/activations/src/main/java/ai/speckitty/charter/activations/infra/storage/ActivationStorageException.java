package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationId;

/**
 * A fault of the activation store itself: a stored document that cannot be read back, or a write the platform
 * refuses. It is the service's fault, never the client's, so it is not a refusal of the client's request: the inbound
 * adapter answers it as it answers any unexpected exception, with {@code internal_error} (only the inbound adapter
 * names that code, as {@code HexagonalArchitectureTest} pins).
 */
public final class ActivationStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A fault with no underlying exception.
     *
     * @param id the activation the fault concerns
     * @param detail what went wrong, for the log
     */
    ActivationStorageException(ActivationId id, String detail) {
        super(message(id, detail));
    }

    /**
     * A fault caused by another exception.
     *
     * @param id the activation the fault concerns
     * @param detail what went wrong, for the log
     * @param cause the underlying exception
     */
    ActivationStorageException(ActivationId id, String detail, Throwable cause) {
        super(message(id, detail), cause);
    }

    private ActivationStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The fault of a store whose directory the platform refuses to list. It concerns no one activation, and it is the
     * same kind of fault as a document that cannot be read back: the service's, answered {@code internal_error}.
     *
     * @param cause the platform's failure
     * @return the fault
     */
    static ActivationStorageException listing(Throwable cause) {
        return new ActivationStorageException("The activation store cannot be listed.", cause);
    }

    private static String message(ActivationId id, String detail) {
        return "The stored activation " + id.value() + " " + detail + ".";
    }
}
