package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;

/**
 * Port: the single seam a registration service consults before it mutates a pack. It is the one place FR-011's
 * mutability gate (WP06, ADR-0012) lands, so the real policy can be supplied without editing any registration service
 * or the commit store. This WP ships only the port and a permissive default; it models no mutability levels.
 *
 * <p>A gate authorizes or refuses: an authorized call returns, a refused one throws a
 * {@link ai.speckitty.charter.kernel.domain.CharterRefusal} (the charter fails loud, never an empty success).
 */
public interface WritePolicyGate {

    /** The write a registration service is about to perform, which the policy may allow or refuse per target. */
    enum Operation {
        /** Register a net-new artifact in the pack. */
        REGISTER,
        /** Replace an existing artifact of the pack. */
        REPLACE,
        /** Register a relationship between two artifacts. */
        RELATIONSHIP
    }

    /**
     * Authorizes a write, or refuses it.
     *
     * @param pack the pack being mutated, already loaded
     * @param operation the write about to be performed
     * @param target the artifact or relationship endpoint the write concerns
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the policy forbids the write
     */
    void authorize(Pack pack, Operation operation, Urn target);
}
