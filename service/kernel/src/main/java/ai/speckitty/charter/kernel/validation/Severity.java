package ai.speckitty.charter.kernel.validation;

/**
 * How much a breached rule weighs. Only a {@link #VIOLATION} fails a {@link Rationale} or a {@code guard}; a warning
 * and a notice are findings the caller reports and lets pass (ADR-0075 decisions 1 and 5).
 */
public enum Severity {
    /** The subject is invalid. */
    VIOLATION,
    /** The subject is valid, but something in it deserves attention, such as a file nothing models. */
    WARNING,
    /** Information only. */
    NOTICE
}
