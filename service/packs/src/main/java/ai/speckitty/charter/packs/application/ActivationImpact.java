package ai.speckitty.charter.packs.application;

import java.util.List;
import java.util.Optional;

/**
 * Port: what the registration impact check asks of the activations slice (IC-15, FR-025, FR-027). {@code packs} declares
 * it and {@code activations.infra} implements it, so the dependency keeps its direction (C-002).
 */
@FunctionalInterface
public interface ActivationImpact {

    /**
     * The root set of every stored activation: its {@code packs} list as the configured identifiers it names, in the
     * order written, or an empty optional for an activation that lists no packs, which roots at every configured pack
     * (FR-008). An activation whose document cannot be read has no root set to give and is left out.
     *
     * @return one entry per stored activation
     */
    List<Optional<List<String>>> activationRoots();

    /**
     * Tells the activations slice that a registration was stored and what it did to the floor and the locks, so that it
     * can name the activations whose in-force set changed (FR-027, B4). Called after the commit, under the locks. The
     * default does nothing: the affected-activations notice is built in PR B.
     *
     * @param packId the name the pack is stored under
     * @param notices the floor and lock changes of the registration
     */
    default void registered(String packId, RegistrationImpact.Notices notices) {}

    /** The port of a deployment with no activations slice: no activation is stored. */
    ActivationImpact NONE = List::of;
}
