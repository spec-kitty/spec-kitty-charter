package ai.speckitty.charter.profiles.domain.selection;

import java.util.Objects;

/**
 * A profile selection answers.
 *
 * @param profileId the profile's id
 * @param pack the pack that holds it
 * @param fit how it fills the role
 */
public record Pick(String profileId, String pack, Fit fit) {

    /**
     * Checks that every part is present.
     *
     * @param profileId the profile's id
     * @param pack the pack that holds it
     * @param fit how it fills the role
     */
    public Pick {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(fit, "fit");
    }
}
