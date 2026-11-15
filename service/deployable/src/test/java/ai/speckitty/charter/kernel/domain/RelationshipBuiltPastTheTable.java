package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * The known-bad control of RELATIONSHIP_IS_BOUND: it sits in the kernel's own package, the only place the
 * package-private constructor can be called from, and builds an edge the endpoint table refuses without
 * {@link EndpointRule}. Test-only, so the production import never sees it.
 */
final class RelationshipBuiltPastTheTable {

    private RelationshipBuiltPastTheTable() {}

    /** A procedure that requires a step: the edge of #350, built past the table. */
    static Relationship procedureRequiresAStep() {
        return new Relationship(
                new Urn(NodeKind.PROCEDURE, "p"),
                new Urn(NodeKind.ACTION, "software-dev/implement"),
                RelationType.REQUIRES,
                Optional.empty(),
                Optional.empty());
    }
}
