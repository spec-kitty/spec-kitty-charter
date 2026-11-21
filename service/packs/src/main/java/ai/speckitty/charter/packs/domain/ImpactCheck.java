package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Port: the impact check of ADR-0075 decision 6 (#556), the closure difference of a pack write. With the write in place
 * it composes every closure that holds the written pack and judges each: the compose, the relation rules and the
 * closure walk ({@link ClosureWalk}); for a removal, the packs and activations that still depend on the pack come
 * first. It collects every fault into one {@link ClosureVerdict}, and answers the floor and lock changes the write
 * makes from the same compose. A pack that cannot be read is no fault: its refusal propagates. The service composes it
 * for every write; the validation edge and {@code pack-check} compose none of it.
 */
public interface ImpactCheck {

    /**
     * Judges a change to a pack, under the registry lock and the pack's own.
     *
     * @param change the change
     * @return the verdict, and the floor and lock changes when it passes
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal {@code pack_not_found} for a write to no configured
     *     pack, {@code pack_unreadable} when a pack it must read cannot be read, or the activations slice's own refusal
     *     ({@code prompt_slot_missing}) when the verdict passes but a stored activation would not compose
     */
    Impact judge(PackChange change);

    /**
     * What the check answers.
     *
     * @param verdict every fault, in the order judged; passing when the write holds
     * @param notices the floor and lock changes over the closures the write joins; none when the verdict fails
     */
    record Impact(ClosureVerdict verdict, Notices notices) {

        /**
         * Checks the fields.
         *
         * @param verdict the verdict
         * @param notices the notices
         */
        public Impact {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(notices, "notices");
        }
    }

    /** A change to a pack the check judges. */
    sealed interface PackChange permits Registration, Write, Removal {}

    /**
     * A whole-pack registration: the pack as it will be stored.
     *
     * @param replacement the pack as it will be stored
     */
    record Registration(Replacement replacement) implements PackChange {

        /**
         * Checks the field.
         *
         * @param replacement the pack as it will be stored
         */
        public Registration {
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    /**
     * A write to one stored pack, an artifact or a relationship or a removal of one: its facts once the write is stored.
     *
     * @param pack the pack written to, by its registered id
     * @param write what the pack's facts become once the write is stored
     * @param staged the pack aggregate once the write is stored, when the write has one (ADR-0068 reads it)
     */
    record Write(PackIdentifier pack, UnaryOperator<PackCompositionFacts> write, Optional<Pack> staged)
            implements PackChange {

        /**
         * Checks the fields.
         *
         * @param pack the pack written to
         * @param write what its facts become
         * @param staged the staged aggregate, if any
         */
        public Write {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(write, "write");
            Objects.requireNonNull(staged, "staged");
        }
    }

    /**
     * The removal of a pack whole (ADR-0081): its dependents first, then every closure left without it.
     *
     * @param configured how the removed pack is configured
     * @param descriptor its descriptor; empty when its own {@code pack.yaml} cannot be read (ADR-0081 D6)
     * @param held every artifact and term it holds, as URNs; an artifact of another pack that refers to one is a
     *     dependent
     */
    record Removal(ConfiguredPack configured, Optional<PackDescriptor> descriptor, Set<Urn> held)
            implements PackChange {

        /**
         * Checks the fields and copies the set.
         *
         * @param configured how it is configured
         * @param descriptor its descriptor, if it can be read
         * @param held what it holds
         */
        public Removal {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            held = Set.copyOf(held);
        }
    }

    /**
     * The floor and lock changes of a registration (FR-027, B4), over every closure the write joined: the must-have
     * items the floor gained and lost, and the artifacts that became locked and stopped being locked. A closure that
     * could not be composed before the write has no before, so everything of it counts as gained.
     *
     * @param floorAdded must-have items the floor gained, as URNs in code point order
     * @param floorRemoved must-have items the floor lost
     * @param locksAdded artifacts locked now and not before
     * @param locksRemoved artifacts locked before and not now
     * @param activationsAffected the stored activations whose in-force set the write changes, sorted
     */
    record Notices(
            List<String> floorAdded,
            List<String> floorRemoved,
            List<String> locksAdded,
            List<String> locksRemoved,
            List<String> activationsAffected) {

        /** No change. */
        public static final Notices NONE = new Notices(List.of(), List.of(), List.of(), List.of());

        /**
         * Copies the lists.
         *
         * @param floorAdded must-have items gained
         * @param floorRemoved must-have items lost
         * @param locksAdded locks gained
         * @param locksRemoved locks lost
         * @param activationsAffected the activations whose in-force set changes
         */
        public Notices {
            floorAdded = List.copyOf(floorAdded);
            floorRemoved = List.copyOf(floorRemoved);
            locksAdded = List.copyOf(locksAdded);
            locksRemoved = List.copyOf(locksRemoved);
            activationsAffected = List.copyOf(activationsAffected);
        }

        /**
         * Notices that name no activation.
         *
         * @param floorAdded must-have items gained
         * @param floorRemoved must-have items lost
         * @param locksAdded locks gained
         * @param locksRemoved locks lost
         */
        public Notices(
                List<String> floorAdded,
                List<String> floorRemoved,
                List<String> locksAdded,
                List<String> locksRemoved) {
            this(floorAdded, floorRemoved, locksAdded, locksRemoved, List.of());
        }
    }

    /**
     * The state a pack will have once the write is stored.
     *
     * @param configured how the pack is configured: the name it is stored under and its level
     * @param descriptor its descriptor
     * @param facts what the compose reads of it
     * @param pack the staged aggregate, when the write sends a whole pack (ADR-0068 reads the fallback profile here)
     */
    record Replacement(
            ConfiguredPack configured, PackDescriptor descriptor, PackCompositionFacts facts, Optional<Pack> pack) {

        /**
         * Checks the fields.
         *
         * @param configured how the pack is configured
         * @param descriptor its descriptor
         * @param facts what the compose reads of it
         * @param pack the staged aggregate, when the write sends a whole pack; a rule that reads an entity of the
         *     written pack (ADR-0068) reads it here
         */
        public Replacement {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(pack, "pack");
        }

        /**
         * A replacement whose entities are read from the store.
         *
         * @param configured how the pack is configured
         * @param descriptor its descriptor
         * @param facts what the compose reads of it
         */
        public Replacement(ConfiguredPack configured, PackDescriptor descriptor, PackCompositionFacts facts) {
            this(configured, descriptor, facts, Optional.empty());
        }
    }
}
