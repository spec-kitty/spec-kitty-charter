package ai.speckitty.charter.profiles.domain.selection;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.Objects;

/**
 * An in-force, non-enhancer agent profile that selection may answer (ADR-0069 decision 4, ADR-0078).
 *
 * @param profile the profile, as authored: selection reads its declared fields only, never inherited ones
 * @param pack the pack that holds it
 * @param closurePosition its pack's position in the activation's closure; orders a tie
 * @param depth the length of its longest in-force {@code specializes_from} chain; 0 for a root
 */
public record SelectionCandidate(AgentProfile profile, String pack, int closurePosition, int depth) {

    /**
     * Checks the parts.
     *
     * @param profile the profile
     * @param pack the pack that holds it
     * @param closurePosition its pack's closure position, 0 or above
     * @param depth its longest in-force chain, 0 or above
     * @throws IllegalArgumentException when the position or the depth is negative
     */
    public SelectionCandidate {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(pack, "pack");
        if (closurePosition < 0 || depth < 0) {
            throw new IllegalArgumentException("A candidate's closure position and depth must not be negative.");
        }
    }

    /**
     * The profile's id.
     *
     * @return the profile id
     */
    public String profileId() {
        return profile.id().value();
    }
}
