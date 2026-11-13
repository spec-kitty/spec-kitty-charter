package ai.speckitty.charter.kernel.validation;

import java.util.List;
import java.util.stream.Stream;

/**
 * The ordered {@link Reason}s of one check. Immutable: the list is copied on construction. Only a
 * {@link Severity#VIOLATION} fails it; warnings and notices are findings it reports and lets pass.
 *
 * @param details every reason, held or breached, in rule order
 */
public record Rationale(List<Reason> details) {

    /** Copies the list so the rationale cannot be changed after the fact. */
    public Rationale {
        details = List.copyOf(details);
    }

    /**
     * Whether no rule found a violation; an empty rationale passes.
     *
     * @return true when no reason is a breached violation
     */
    public boolean isPassing() {
        return details.stream().noneMatch(reason -> reason.isFailing(Severity.VIOLATION));
    }

    /**
     * Whether some rule found a violation.
     *
     * @return true when some reason is a breached violation
     */
    public boolean isFailing() {
        return !isPassing();
    }

    /**
     * Every finding: the breaches, at any severity.
     *
     * @return the breached reasons, in rule order
     */
    public List<Reason> failures() {
        return details.stream().filter(Reason::isFailing).toList();
    }

    /**
     * The breached violations only.
     *
     * @return them, in rule order
     */
    public List<Reason> violations() {
        return failing(Severity.VIOLATION);
    }

    /**
     * The breached warnings only.
     *
     * @return them, in rule order
     */
    public List<Reason> warnings() {
        return failing(Severity.WARNING);
    }

    /**
     * The breached notices only.
     *
     * @return them, in rule order
     */
    public List<Reason> notices() {
        return failing(Severity.NOTICE);
    }

    /**
     * This rationale followed by another; neither is changed.
     *
     * @param next the reasons that follow
     * @return the joined rationale
     */
    public Rationale and(Rationale next) {
        return new Rationale(
                Stream.concat(details.stream(), next.details.stream()).toList());
    }

    private List<Reason> failing(Severity weight) {
        return details.stream().filter(reason -> reason.isFailing(weight)).toList();
    }
}
