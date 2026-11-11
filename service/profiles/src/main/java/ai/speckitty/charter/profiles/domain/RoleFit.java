package ai.speckitty.charter.profiles.domain;

/**
 * How well a profile fits the role a step asks for (ADR-0069, step 1 of ADR-0032's selection order). The constants
 * are declared best first: a primary-role match outranks any alt-role match, whatever else the profiles declare, and
 * a profile that does not take the role at all is no candidate. Specialization (lineage, then specificity) only
 * orders profiles of the same fit.
 */
public enum RoleFit {
    /** The asked role is the profile's {@code role}. */
    PRIMARY(2),
    /** The asked role is one of the profile's {@code alt-roles}. */
    ALT(1),
    /** The profile does not take the asked role. */
    NONE(0);

    private final int rank;

    RoleFit(int rank) {
        this.rank = rank;
    }

    /**
     * Whether this fit makes the profile a candidate for the role.
     *
     * @return {@code true} for {@link #PRIMARY} and {@link #ALT}
     */
    public boolean fits() {
        return this != NONE;
    }

    /**
     * Whether this fit ranks above another: a strictly better role fit.
     *
     * @param other the fit compared with
     * @return {@code true} when this fit is the better one
     */
    public boolean outranks(RoleFit other) {
        return rank > other.rank;
    }
}
