package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;

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
        RELATIONSHIP,
        /** Remove an existing artifact of the pack (ADR-0065 D5, ADR-0012 decision 1, ADR-0030 decision 3). */
        REMOVE
    }

    /**
     * One write of a whole-pack registration's diff: the operation and the artifact (or the source of the edge) it
     * concerns.
     *
     * @param operation the write
     * @param target the artifact the write concerns
     */
    record Write(Operation operation, Urn target) {

        /**
         * Checks the parts.
         *
         * @param operation the write
         * @param target the artifact the write concerns
         */
        public Write {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(target, "target");
        }
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

    /**
     * Authorizes the writes of a whole-pack registration over the closure the staged pack will have (ADR-0065 D5): the
     * staged pack is not (yet) in the live closure, and a replace may change its {@code depends_on}, so the live-closure
     * {@link #authorize} would judge the wrong packs and answer {@code pack_not_found} for a pack that does not exist yet.
     * The writes are judged together, over one read of the closure.
     *
     * @param staged the staged pack, as it will be stored
     * @param writes the diff of the registration: artifacts added, changed and removed, and edges added and removed
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the policy forbids one of the writes
     */
    void authorizeStaged(Pack staged, List<Write> writes);
}
