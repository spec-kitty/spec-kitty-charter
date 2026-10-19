package ai.speckitty.charter.contracts.closure;

import java.util.List;

/**
 * What the checker found in one bundle.
 *
 * @param positions  the number of response schemas judged
 * @param violations every violation, in document order
 */
public record ClosureReport(int positions, List<Violation> violations) {

    /** Copies the violations, so a report never changes. */
    public ClosureReport {
        violations = List.copyOf(violations);
    }

    /**
     * Whether the bundle follows the convention.
     *
     * @return {@code true} when there are no violations
     */
    public boolean passes() {
        return violations.isEmpty();
    }
}
