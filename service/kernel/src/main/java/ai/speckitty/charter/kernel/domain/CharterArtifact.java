package ai.speckitty.charter.kernel.domain;

import java.util.List;
import java.util.Optional;

/**
 * The thin common spine of every artefact a pack carries: only what all kinds genuinely share. It lives in the kernel
 * so that every slice can implement it (ADR-0035).
 */
public interface CharterArtifact {

    /**
     * The kind of this artefact.
     *
     * @return the kernel kind constant
     */
    ArtifactKind kind();

    /**
     * The pack that owns this artefact.
     *
     * @return the owning pack's identity
     */
    PackIdentifier packId();

    /**
     * The artefact's id within its kind.
     *
     * @return the schema {@code id}
     */
    String localId();

    /**
     * The schema version the artefact was authored against, when its kind has one.
     *
     * @return the {@code schema_version}, or empty for a kind without one
     */
    Optional<String> schemaVersion();

    /**
     * The human-readable name, normalized across kinds.
     *
     * @return the {@code title} of a directive or styleguide, or the {@code name} of a paradigm or tactic
     */
    String label();

    /**
     * The short statement of what the artefact is for, normalized across kinds.
     *
     * @return the {@code intent} of a directive, the {@code summary} of a paradigm, the {@code purpose} of a
     *     tactic; empty for a styleguide
     */
    Optional<String> description();

    /**
     * The relationships this artefact derives from its own runtime data: never authored, refused on write, and never
     * compared by the round trip. No artifact field authors a relationship: a pack's relationships are authored only
     * in its graph documents (ADR-0063). Today only a guard derives one, {@code implemented_by} its in-pack asset; every
     * returned relation is {@linkplain RelationType#derived() derived}.
     *
     * @return the derived relationships; empty for every kind but a guard
     */
    default List<Relationship> derivedRelationships() {
        return List.of();
    }

    /**
     * Every reference this artefact writes outside its pack's graph documents: the target of each
     * {@linkplain #derivedRelationships() derived relationship}, so the compose's reference check covers it as it covers
     * every graph-document edge.
     *
     * @return the references, in the artefact's order; empty when it writes none
     */
    default List<PackQualifiedReference> writtenReferences() {
        return derivedRelationships().stream()
                .map(Relationship::toReference)
                .distinct()
                .toList();
    }

    /**
     * The artefact's DRG node identity.
     *
     * @return {@code <kind>:<localId>}
     */
    default Urn urn() {
        return new Urn(NodeKind.of(kind()), localId());
    }
}
