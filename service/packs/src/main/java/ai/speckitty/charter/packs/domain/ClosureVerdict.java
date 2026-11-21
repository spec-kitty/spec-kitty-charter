package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import java.util.List;
import java.util.Objects;

/**
 * The verdict of the closure checks of a write (#556), as a view over their {@link Rationale} (#614, ADR-0082 decision
 * 4): every fault they found, in the order they judged them. The ledger is {@link #rationale()}, each fault's reason in
 * order; the faults hold only what a reason has no slot for, the packs, violations and requiring packs a refusal names.
 * Whether it passes is the rationale's answer. It holds no refusal mapping: the closure edge's mapper
 * ({@code PackRefusals.of}) turns a failing verdict into the write's one refusal.
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
     * The ledger of the closure checks: each fault's reason, in the order they were found.
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
