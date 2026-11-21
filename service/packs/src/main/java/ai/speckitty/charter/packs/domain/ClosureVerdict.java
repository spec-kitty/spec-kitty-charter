package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The verdict of the closure checks of a write (#556): every fault they found, in the order they judged them. A verdict
 * with no fault passes. It holds no refusal mapping: the closure edge's mapper ({@code PackRefusals.of}) turns a failing
 * verdict into the write's one refusal, and {@link #rationale()} is the same verdict in the kernel's terms, which the
 * ports' rule views return.
 *
 * @param faults the faults, in the order they were found
 */
public record ClosureVerdict(List<ClosureFault> faults) {

    /** The verdict of a write that breaks no closure check. */
    public static final ClosureVerdict PASSING = new ClosureVerdict(List.of());

    /**
     * Copies the list.
     *
     * @param faults the faults, in order
     */
    public ClosureVerdict {
        faults = List.copyOf(Objects.requireNonNull(faults, "faults"));
    }

    /**
     * Whether the write breaks no closure check.
     *
     * @return true when there is no fault
     */
    public boolean isPassing() {
        return faults.isEmpty();
    }

    /**
     * This verdict, then another's faults.
     *
     * @param next the verdict judged after this one
     * @return the faults of both, this verdict's first
     */
    public ClosureVerdict and(ClosureVerdict next) {
        Objects.requireNonNull(next, "next");
        if (next.isPassing()) {
            return this;
        }
        if (isPassing()) {
            return next;
        }
        List<ClosureFault> both = new ArrayList<>(faults);
        both.addAll(next.faults());
        return new ClosureVerdict(both);
    }

    /**
     * The verdict as the kernel states it: one failing reason per fault, in order.
     *
     * @return the rationale
     */
    public Rationale rationale() {
        return new Rationale(faults.stream().map(ClosureFault::reason).toList());
    }
}
