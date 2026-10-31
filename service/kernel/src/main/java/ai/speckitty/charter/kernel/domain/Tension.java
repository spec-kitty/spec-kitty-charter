package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Two rules pull against each other: the {@code tension} relationship of ADR-0028 (Decision 2). It is authored, never
 * derived from value impacts, and carries no strength (Decision 1).
 *
 * <p>A tension is not a {@link Relationship}. Between two artifacts there is at most one structural relationship and,
 * beside it, a tension (ADR-0028 Decision 3), so a tension is held beside the relationships of a
 * {@link DoctrineGraph} and is outside their unordered-pair identity (ADR-0027). It is compared as written, orientation
 * included (ADR-0036 Decision 4: the reader never turns an edge around). Today's pack files write it
 * {@code in_tension_with}; that spelling is storage's, not the kernel's (ADR-0044).
 *
 * @param from the rule the tension is authored from
 * @param to the rule the tension is authored to
 * @param when the authored condition; empty when it has none
 * @param rationale the authored reason; empty when it has none
 */
public record Tension(Urn from, Urn to, Optional<String> when, Optional<String> rationale) {
    /**
     * Checks that every field is present and that the tension joins two different rules.
     *
     * @param from the rule the tension is authored from
     * @param to the rule the tension is authored to
     * @param when the condition, possibly empty
     * @param rationale the reason, possibly empty
     * @throws IllegalArgumentException when {@code from} equals {@code to}
     */
    public Tension {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(rationale, "rationale");
        if (from.equals(to)) {
            throw new IllegalArgumentException("A tension cannot join a rule to itself: " + from);
        }
    }
}
