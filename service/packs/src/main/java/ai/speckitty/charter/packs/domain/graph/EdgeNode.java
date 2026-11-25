package ai.speckitty.charter.packs.domain.graph;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import java.util.Objects;

/**
 * An edge a pack writes, kept as the pack declares it and never resolved to the pack that holds its target: which pack
 * a reference means depends on the closure, and one graph serves many closures.
 *
 * @param kind the edge's kind
 * @param pack the writing pack, by configured identifier
 * @param written the edge as the pack writes it
 */
public record EdgeNode(EdgeKind kind, PackIdentifier pack, Written written) implements PackGraphNode {

    /** An edge as a pack writes it. */
    public sealed interface Written permits DependsOn, ParentPack, Related, Referenced, Delegated {}

    /**
     * A {@code depends_on} entry.
     *
     * @param dependency the entry
     */
    public record DependsOn(PackDependency dependency) implements Written {}

    /**
     * A {@code parent_pack}.
     *
     * @param packId the parent's descriptor {@code pack_id}
     */
    public record ParentPack(String packId) implements Written {}

    /**
     * A typed edge a pack's graph documents, artifacts or shard write.
     *
     * @param relationship the edge
     */
    public record Related(Relationship relationship) implements Written {}

    /**
     * A reference to an artifact, as written.
     *
     * @param reference the reference
     */
    public record Referenced(String reference) implements Written {}

    /**
     * A substep delegation written with another pack's name.
     *
     * @param candidate the delegation
     */
    public record Delegated(SubstepDelegationRule.QualifiedCandidate candidate) implements Written {}

    /**
     * Checks the fields.
     *
     * @param kind the kind
     * @param pack the writing pack
     * @param written the edge
     */
    public EdgeNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(written, "written");
    }

    @Override
    public Origin origin() {
        return PackOrigins.of(pack);
    }
}
