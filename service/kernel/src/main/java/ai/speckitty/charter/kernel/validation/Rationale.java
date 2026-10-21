package ai.speckitty.charter.kernel.validation;

import java.util.List;

/**
 * The ordered {@link Reason}s of one evaluation. Immutable: the list is copied on construction.
 *
 * @param details every reason, in constraint order
 */
public record Rationale(List<Reason> details) {

    /** Copies the list so the rationale cannot be changed after the fact. */
    public Rationale {
        details = List.copyOf(details);
    }

    /**
     * Whether every constraint held; an empty rationale passes.
     *
     * @return true when no reason failed
     */
    public boolean isPassing() {
        return details.stream().allMatch(Reason::isPassing);
    }

    /**
     * Whether at least one constraint was breached.
     *
     * @return true when some reason failed
     */
    public boolean isFailing() {
        return !isPassing();
    }

    /**
     * The breaches only.
     *
     * @return the failing reasons, in constraint order
     */
    public List<Reason> failures() {
        return details.stream().filter(Reason::isFailing).toList();
    }
}
