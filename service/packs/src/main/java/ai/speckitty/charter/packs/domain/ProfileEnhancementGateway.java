package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The profile enhancement merge (rulings D5 and D7, #380) and the inheritance along {@code specializes_from} (ruling
 * D6) as the packs slice reaches them: the profiles slice's {@code ProfileEnhancementMerge} and
 * {@code ProfileInheritance} own the rules, and the storage ring, which binds the agent-profile kind, implements this
 * port over them, so the application ring judges an enhancement, and composes the effective profile, over kernel types
 * alone.
 */
public interface ProfileEnhancementGateway {

    /**
     * One enhancer of a profile.
     *
     * @param profile the enhancing agent profile
     * @param pack the registered id of the pack that holds it
     */
    record Enhancing(CharterArtifact profile, PackIdentifier pack) {

        /**
         * Checks the fields.
         *
         * @param profile the enhancing agent profile
         * @param pack the holding pack
         */
        public Enhancing {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(pack, "pack");
        }
    }

    /**
     * An enhancement that would lose content of the profile it enhances.
     *
     * @param pack the registered id of the enhancing pack
     * @param field the top-level field, as the schema names it
     * @param detail the sentence naming the pack, the profiles and the field
     */
    record Dropped(PackIdentifier pack, String field, String detail) implements Composed {

        /**
         * Checks the fields.
         *
         * @param pack the enhancing pack
         * @param field the field
         * @param detail the sentence
         */
        public Dropped {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * Merges a profile with its enhancers and answers the first drop.
     *
     * @param base the enhanced agent profile
     * @param inClosureOrder its enhancers, in closure order, the nearest pack last
     * @return the first enhancer, in that order, whose merge loses content; empty when none does
     */
    Optional<Dropped> firstDrop(CharterArtifact base, List<Enhancing> inClosureOrder);

    /**
     * One profile of a {@code specializes_from} chain with its own in-force enhancers.
     *
     * @param profile the agent profile, as the pack that holds the version in force stores it
     * @param enhancers its in-force enhancers, in closure order, the nearest pack last
     */
    record Layer(CharterArtifact profile, List<Enhancing> enhancers) {

        /**
         * Checks and copies the fields.
         *
         * @param profile the agent profile
         * @param enhancers its enhancers
         */
        public Layer {
            Objects.requireNonNull(profile, "profile");
            enhancers = List.copyOf(Objects.requireNonNull(enhancers, "enhancers"));
        }
    }

    /** What {@link #compose} answers: the effective profile, or the first enhancement that would drop a field. */
    sealed interface Composed permits Effective, Dropped {}

    /**
     * The effective profile.
     *
     * @param profile the leaf merged with its enhancers, with the fields it leaves absent inherited
     */
    record Effective(CharterArtifact profile) implements Composed {

        /**
         * Checks the field.
         *
         * @param profile the effective profile
         */
        public Effective {
            Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * The effective profile of a chain (rulings D5 and D6): each profile is merged with its own enhancers first, then the
     * chain is folded root to leaf by per-field replacement, identity fields never inherited.
     *
     * @param rootToLeaf the chain, the root first and the profile read last; at least the profile itself
     * @return the effective profile, or the first drop, in chain order and then in each profile's enhancer order
     */
    Composed compose(List<Layer> rootToLeaf);
}
