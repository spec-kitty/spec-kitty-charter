package ai.speckitty.charter.profiles.domain.selection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What selection chooses from, all in force under one activation (ADR-0069 decision 4, ADR-0078).
 *
 * @param candidates the non-enhancer profiles in force, in any order
 * @param pinnedProfileId the profile the role is pinned to, or empty
 * @param pinnedSpecializationIds the ids of the pinned profile's in-force specializations; empty without a pin
 * @param fallback the fallback profile in force, or empty
 */
public record Candidates(
        List<SelectionCandidate> candidates,
        Optional<String> pinnedProfileId,
        Set<String> pinnedSpecializationIds,
        Optional<SelectionCandidate> fallback) {

    /**
     * Copies the collections.
     *
     * @param candidates the profiles in force
     * @param pinnedProfileId the pinned profile, or empty
     * @param pinnedSpecializationIds the pinned profile's in-force specializations
     * @param fallback the fallback, or empty
     * @throws IllegalArgumentException when specializations are given without a pin
     */
    public Candidates {
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(pinnedProfileId, "pinnedProfileId");
        pinnedSpecializationIds = Set.copyOf(pinnedSpecializationIds);
        Objects.requireNonNull(fallback, "fallback");
        if (pinnedProfileId.isEmpty() && !pinnedSpecializationIds.isEmpty()) {
            throw new IllegalArgumentException("Pinned specializations need a pin.");
        }
    }

    /**
     * Candidates with no pin.
     *
     * @param candidates the profiles in force
     * @param fallback the fallback, or empty
     * @return the candidates
     */
    public static Candidates unpinned(List<SelectionCandidate> candidates, Optional<SelectionCandidate> fallback) {
        return new Candidates(candidates, Optional.empty(), Set.of(), fallback);
    }

    /**
     * Whether a candidate is the pinned profile.
     *
     * @param candidate the candidate
     * @return {@code true} when the role is pinned to it
     */
    boolean isPinned(SelectionCandidate candidate) {
        return pinnedProfileId.filter(candidate.profileId()::equals).isPresent();
    }
}
