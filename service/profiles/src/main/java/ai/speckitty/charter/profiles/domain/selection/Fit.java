package ai.speckitty.charter.profiles.domain.selection;

import ai.speckitty.charter.profiles.domain.RoleFit;

/** How a picked profile came to fill the role (ADR-0069 decision 4, ADR-0078). */
public enum Fit {
    /** The role is the profile's primary role. */
    PRIMARY,
    /** The role is one of the profile's alt-roles. */
    ALT,
    /** No candidate fitted; the fallback in force is answered. */
    FALLBACK;

    /**
     * The fit of a candidate that takes the role.
     *
     * @param roleFit {@link RoleFit#PRIMARY} or {@link RoleFit#ALT}
     * @return {@link #PRIMARY} or {@link #ALT}
     */
    static Fit of(RoleFit roleFit) {
        return roleFit == RoleFit.PRIMARY ? PRIMARY : ALT;
    }
}
