package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import java.util.List;
import java.util.Objects;

/**
 * The verdict of the closure checks of a write (#556): the faults they found, in the order they judged them, with their
 * {@link Rationale} derived (#614, ADR-0082 decision 4). It is a fault list, not a view over a rationale: it owns the
 * faults, because a reason has no slot for the packs, violations and requiring packs a refusal names, and
 * {@link #rationale()} projects each fault's reason, in order. Whether it passes is that projection's answer. Slice 7 of
 * #636 removes this type once the ledger carries what the faults hold. It holds no refusal mapping: the closure edge's
 * mapper ({@code PackRefusals.of}) turns a failing verdict into the write's one refusal.
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
     * The rationale projected from the faults: each fault's reason, in the order they were found.
     *
     * @return the rationale
     */
    public Rationale rationale() {
        return new Rationale(faults.stream().map(ClosureFault::reason).toList());
    }

    /**
     * Whether the write breaks no closure check: the rationale holds no violation.
     *
     * @return true when the rationale passes
     */
    public boolean isPassing() {
        return rationale().isPassing();
    }
}
