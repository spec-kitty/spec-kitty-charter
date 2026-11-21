package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Rule;
import java.util.List;
import java.util.Objects;

/**
 * Port: the mutability check of ADR-0012 (#556, ADR-0075 slice 7, D5), the seam every pack write consults before it
 * mutates a pack and before the impact check judges the write. It judges all the writes of one request at once, over
 * one read of the closure, and names every write the levels along the closure refuse, each one
 * {@code governance_policy_violation} fault, in the order given. Its refusal ({@code 409}) ends the write.
 */
@FunctionalInterface
public interface MutabilityCheck {

    /** The write a pack write performs, which the levels may allow or refuse per target. */
    enum Operation {
        /** Register a net-new artifact in the pack. */
        REGISTER,
        /** Replace an existing artifact of the pack. */
        REPLACE,
        /** Register a relationship between two artifacts. */
        RELATIONSHIP,
        /** Remove an existing artifact of the pack (ADR-0065 D5, ADR-0012 decision 1, ADR-0030 decision 3). */
        REMOVE
    }

    /**
     * One write: the operation and the artifact (or the endpoint of the edge) it concerns.
     *
     * @param operation the write
     * @param target the artifact the write concerns
     */
    record Write(Operation operation, Urn target) {

        /**
         * Checks the parts.
         *
         * @param operation the write
         * @param target the artifact the write concerns
         */
        public Write {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(target, "target");
        }
    }

    /** The writes of one request into one pack, judged together. */
    sealed interface Writes permits Live, Staged {

        /**
         * The pack written.
         *
         * @return the pack
         */
        Pack pack();

        /**
         * The writes, in the order they are judged and named.
         *
         * @return the writes
         */
        List<Write> writes();
    }

    /**
     * Writes into a stored pack, judged over its live closure.
     *
     * @param pack the pack being mutated, already loaded
     * @param writes the writes, in order
     */
    record Live(Pack pack, List<Write> writes) implements Writes {

        /**
         * Checks the parts and copies the writes.
         *
         * @param pack the pack
         * @param writes the writes
         */
        public Live {
            Objects.requireNonNull(pack, "pack");
            writes = List.copyOf(Objects.requireNonNull(writes, "writes"));
        }

        /**
         * One write into a stored pack.
         *
         * @param pack the pack being mutated
         * @param operation the write
         * @param target the artifact the write concerns
         * @return the writes
         */
        public static Live of(Pack pack, Operation operation, Urn target) {
            return new Live(pack, List.of(new Write(operation, target)));
        }
    }

    /**
     * The writes of a whole-pack registration, judged over the closure the staged pack will have (ADR-0065 D5): the
     * staged pack is not (yet) in the live closure, and a replace may change its {@code depends_on}.
     *
     * @param pack the staged pack, as it will be stored
     * @param writes the diff of the registration: artifacts added, changed and removed, and edges added and removed
     */
    record Staged(Pack pack, List<Write> writes) implements Writes {

        /**
         * Checks the parts and copies the writes.
         *
         * @param pack the staged pack
         * @param writes the writes
         */
        public Staged {
            Objects.requireNonNull(pack, "pack");
            writes = List.copyOf(Objects.requireNonNull(writes, "writes"));
        }
    }

    /**
     * Judges the writes.
     *
     * @param writes the writes of one request
     * @return one {@code governance_policy_violation} fault per refused write, in the order given; passing when every
     *     write is permitted
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the closure cannot be read to judge them
     */
    ClosureVerdict judge(Writes writes);

    /**
     * The check as a kernel rule: one failing reason per refused write.
     *
     * @return the rule
     */
    default Rule<Writes> rule() {
        return writes -> judge(writes).rationale().details();
    }
}
