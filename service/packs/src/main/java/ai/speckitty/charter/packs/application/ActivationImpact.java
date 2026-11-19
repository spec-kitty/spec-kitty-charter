package ai.speckitty.charter.packs.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Port: what the registration impact check asks of the activations slice (IC-15, FR-025, FR-027). {@code packs} declares
 * it and {@code activations.infra} implements it, so the dependency keeps its direction (C-002).
 */
@FunctionalInterface
public interface ActivationImpact {

    /**
     * A stored activation's root set (ADR-0081 P1): its id, and its {@code packs} list as the configured identifiers it
     * names, in the order written, or an empty optional for an activation that lists no packs, which roots at every
     * configured pack (FR-008).
     *
     * @param id the activation's id
     * @param packs the packs it names, or empty when it names none
     */
    record ActivationRoots(String id, Optional<List<String>> packs) {

        /**
         * Checks the fields and copies the list.
         *
         * @param id the activation's id
         * @param packs the packs it names
         */
        public ActivationRoots {
            Objects.requireNonNull(id, "id");
            packs = Objects.requireNonNull(packs, "packs").map(List::copyOf);
        }

        /**
         * Whether the activation names a pack in its {@code packs} list (ADR-0081 D6): an activation that lists no packs
         * names none.
         *
         * @param configuredName the pack's configured name
         * @return whether it names it
         */
        public boolean names(String configuredName) {
            return packs.filter(listed -> listed.contains(configuredName)).isPresent();
        }
    }

    /**
     * The root set of every stored activation, with its id. An activation whose document cannot be read has no root set
     * to give and is left out.
     *
     * @return one entry per stored activation
     */
    List<ActivationRoots> activationRoots();

    /**
     * The ids of the stored activations whose document cannot be read, which {@link #activationRoots()} leaves out. A
     * check that must fail closed asks for them (ADR-0081 D7, review R3-1): an activation it cannot read might name the
     * pack it judges.
     *
     * @return the ids, sorted; the default names none, as a deployment with no activations slice stores no activation
     */
    default List<String> unreadableActivations() {
        return List.of();
    }

    /**
     * Tells the activations slice that a registration was stored and what it did to the floor and the locks. Called after
     * the commit, under the locks. The default does nothing, and the activations slice has nothing to add: the
     * activations whose in-force set changed were named before the commit, by {@link #activationsAffected}.
     *
     * @param packId the name the pack is stored under
     * @param notices the floor and lock changes of the registration
     */
    default void registered(String packId, RegistrationImpact.Notices notices) {}

    /**
     * The closure of one root set, before and after the registration being judged, as the impact check composed it.
     *
     * @param before the closure before the write; empty when it did not exist or cannot be composed
     * @param after the closure with the write in place
     */
    record ClosureChange(Optional<ClosureView> before, ClosureView after) {}

    /** The closures a registration joins, by the root set of an activation. */
    @FunctionalInterface
    interface ClosureChanges {

        /**
         * The closure an activation's root set composes, before and after.
         *
         * @param roots the activation's {@code packs} as configured identifiers in the order written, or empty for every
         *     configured pack
         * @return the closure; empty when it does not hold the written pack (the activation is none of the registration's
         *     concern) or when its roots cannot be composed
         */
        Optional<ClosureChange> of(Optional<List<String>> roots);
    }

    /**
     * The ids of the stored activations whose in-force set the registration changes (FR-027, B4): those whose closure
     * holds the written pack and whose set of artifacts in force differs between the two closures. Called by the impact
     * check of a whole-pack registration, before the commit and under the locks, with the closures it composed anyway.
     *
     * @param closures the closures the registration joins
     * @return the activation ids, sorted and without a duplicate; the default names none
     */
    default List<String> activationsAffected(ClosureChanges closures) {
        return List.of();
    }

    /**
     * The registration gate of C38 (FR-001, OD-4 (i), squad A2): refuses the write when a stored activation whose
     * closure it joins would, once it is stored, put a step enhancement in force whose slot the prompt in force does not
     * declare. It is separate from the notices: the notices never refuse, and this never names an affected activation.
     * The impact check calls it on every path (a whole-pack registration, a per-artifact registration, a relationship
     * write and both {@code DELETE}s), before the commit and under the locks, with the closures it composed anyway; the
     * refusal it raises reaches the caller as it is.
     *
     * @param closures the closures the write joins, by the root set of an activation
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PROMPT_SLOT_MISSING}, the activation named
     *     in the detail; the default judges nothing, as a deployment with no activations slice stores no activation
     */
    default void requireComposable(ClosureChanges closures) {}

    /** The port of a deployment with no activations slice: no activation is stored. */
    ActivationImpact NONE = List::of;
}
