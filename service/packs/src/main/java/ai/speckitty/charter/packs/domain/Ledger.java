package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.List;

/**
 * The ledger of one walk (ADR-0082 decision 4): every fault the walker's phases found, in the order they report them.
 *
 * @param faults the faults, phase by phase, each phase's in check order
 */
public record Ledger(List<ClosureFault> faults) {

    /**
     * Copies the faults.
     *
     * @param faults the faults
     */
    public Ledger {
        faults = List.copyOf(faults);
    }

    /**
     * The ledger as the kernel's rationale, in the same order.
     *
     * @return the rationale
     */
    public Rationale rationale() {
        return new Rationale(faults.stream().map(ClosureFault::reason).toList());
    }

    /**
     * Whether any fault is a violation.
     *
     * @return {@code true} when the walk found a violation
     */
    public boolean violated() {
        return faults.stream().anyMatch(fault -> fault.reason().isFailing(Severity.VIOLATION));
    }
}
