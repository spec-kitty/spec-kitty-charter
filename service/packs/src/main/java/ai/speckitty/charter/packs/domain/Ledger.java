package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ledger of one walk (ADR-0082 decision 4): every fault the walker's phases found, in the order they report them,
 * and the phase whose violation stopped the walk, so a caller that places a phase's faults apart (the impact check's
 * own-closure breaches, ADR-0082 slice 4) reads where they came from and never guesses it from their codes.
 *
 * @param faults the faults, phase by phase, each phase's in check order
 * @param gate the name of the phase whose violation stopped the walk; empty when every phase ran
 */
public record Ledger(List<ClosureFault> faults, Optional<String> gate) {

    /**
     * Copies the faults.
     *
     * @param faults the faults
     * @param gate the phase that stopped the walk
     */
    public Ledger {
        faults = List.copyOf(faults);
        Objects.requireNonNull(gate, "gate");
    }

    /**
     * The ledger of a walk that no phase stopped.
     *
     * @param faults the faults
     */
    public Ledger(List<ClosureFault> faults) {
        this(faults, Optional.empty());
    }

    /**
     * The ledger as the kernel's rationale, in the same order. No production caller reads it yet: slice 7 of epic #636
     * moves the edge's refusal mapping onto the rationale, and this is the projection it reads (ADR-0082 decision 4).
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
