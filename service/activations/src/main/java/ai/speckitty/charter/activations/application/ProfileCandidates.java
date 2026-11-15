package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a profile selection chooses from under one activation, read from one {@link PacksSession} (ADR-0069 decision 4,
 * rulings D2 and D3 of slice G): the agent profiles the activation puts in force, enhancers left out, each with the
 * pack that holds it, its pack's position in the closure and its longest in-force {@code specializes_from} chain; the
 * pin in force for the asked role; and the fallback in force. The selection itself is the profiles slice's; this names
 * no profile type, so the entities are kernel artifacts the profiles edge narrows.
 *
 * @param candidates the non-enhancer agent profiles in force, in id order
 * @param pin the pin in force for the asked role
 * @param fallback the fallback in force
 */
public record ProfileCandidates(List<Candidate> candidates, Pin pin, Fallback fallback) {

    /** No candidate, no pin and no fallback: what a session that holds no closure answers. */
    public static final ProfileCandidates NONE = new ProfileCandidates(List.of(), new Pin.None(), new Fallback.None());

    /**
     * Checks and copies the fields.
     *
     * @param candidates the profiles in force
     * @param pin the pin in force
     * @param fallback the fallback in force
     */
    public ProfileCandidates {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * One agent profile selection may answer.
     *
     * @param profile the entity the winning pack stores
     * @param pack the configured id of the pack that holds the version in force
     * @param closurePosition the position of that pack in the closure, dependencies first
     * @param depth the length of its longest in-force {@code specializes_from} chain; 0 for a root
     * @param ancestors the ids of its in-force {@code specializes_from} ancestors
     */
    public record Candidate(
            CharterArtifact profile, String pack, int closurePosition, int depth, Set<String> ancestors) {

        /**
         * Checks and copies the fields.
         *
         * @param profile the entity
         * @param pack the holding pack
         * @param closurePosition the closure position, 0 or above
         * @param depth the longest chain, 0 or above
         * @param ancestors the ancestors' ids
         * @throws IllegalArgumentException when the position or the depth is negative
         */
        public Candidate {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(pack, "pack");
            ancestors = Set.copyOf(Objects.requireNonNull(ancestors, "ancestors"));
            if (closurePosition < 0 || depth < 0) {
                throw new IllegalArgumentException("A candidate's closure position and depth must not be negative.");
            }
        }

        /**
         * The profile's id.
         *
         * @return the declared profile-id
         */
        public String id() {
            return profile.localId();
        }
    }

    /** The pin in force for the asked role (ruling D3): none, one, or a conflict. */
    public sealed interface Pin {

        /** No pack of the closure pins the role. */
        record None() implements Pin {}

        /**
         * The role is pinned.
         *
         * @param profileId the pinned profile
         * @param pack the configured id of the most derived pack that pins it
         */
        record Pinned(String profileId, String pack) implements Pin {

            /**
             * Checks the fields.
             *
             * @param profileId the pinned profile
             * @param pack the pinning pack
             */
            public Pinned {
                Objects.requireNonNull(profileId, "profileId");
                Objects.requireNonNull(pack, "pack");
            }
        }

        /**
         * Packs of the closure pin the role to different profiles ({@code role_pin_conflict}).
         *
         * @param packs the configured ids of every pinning pack, in closure order
         */
        record Conflict(List<String> packs) implements Pin {

            /**
             * Copies the packs.
             *
             * @param packs the packs in conflict
             */
            public Conflict {
                packs = List.copyOf(packs);
            }
        }
    }

    /** The fallback in force (ADR-0068 decision 5): none, the held profile, or a conflict. */
    public sealed interface Fallback {

        /** No pack of the closure declares a fallback the closure holds. */
        record None() implements Fallback {}

        /**
         * The fallback in force.
         *
         * @param profile the fallback profile, as the closure holds it
         * @param declaringPack the configured id of the most derived pack that declares it
         */
        record Held(Candidate profile, String declaringPack) implements Fallback {

            /**
             * Checks the fields.
             *
             * @param profile the fallback profile
             * @param declaringPack the declaring pack
             */
            public Held {
                Objects.requireNonNull(profile, "profile");
                Objects.requireNonNull(declaringPack, "declaringPack");
            }
        }

        /**
         * Declaring packs, neither derived from the other, name different fallbacks ({@code fallback_profile_conflict}).
         *
         * @param packs the configured ids of the packs in conflict, in closure order
         */
        record Conflict(List<String> packs) implements Fallback {

            /**
             * Copies the packs.
             *
             * @param packs the packs in conflict
             */
            public Conflict {
                packs = List.copyOf(packs);
            }
        }
    }

    /**
     * The in-force specializations of the pinned profile: every candidate whose in-force lineage holds it.
     *
     * @return their ids, empty without a pin
     */
    public Set<String> pinnedSpecializations() {
        if (!(pin instanceof Pin.Pinned pinned)) {
            return Set.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate.ancestors().contains(pinned.profileId()))
                .map(Candidate::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}
