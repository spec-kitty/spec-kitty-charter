package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * The verdict of the closure checks of a write (#556): every fault they found, in the order they judged them. A verdict
 * with no fault passes. It holds no refusal mapping: the closure edge's mapper ({@code PackRefusals.of}) turns a failing
 * verdict into the write's one refusal.
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
}
