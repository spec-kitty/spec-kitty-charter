package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The profile enhancement merge (rulings D5 and D7, #380) as the packs slice reaches it: the profiles slice's
 * {@code ProfileEnhancementMerge} owns the rule, and the storage ring, which binds the agent-profile kind, implements
 * this port over it, so the application ring judges an enhancement over kernel types alone.
 */
public interface ProfileEnhancementGateway {

    /**
     * One enhancer of a profile.
     *
     * @param profile the enhancing agent profile
     * @param pack the pack that holds it
     */
    record Enhancing(CharterArtifact profile, String pack) {

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
     * @param pack the enhancing pack
     * @param field the top-level field, as the schema names it
     * @param detail the sentence naming the pack, the profiles and the field
     */
    record Dropped(String pack, String field, String detail) {

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
}
