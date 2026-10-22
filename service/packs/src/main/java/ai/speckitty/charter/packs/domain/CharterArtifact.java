package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;

/**
 * The thin common spine of every doctrine artefact a pack carries: only what all kinds genuinely share. It lives in
 * {@code packs.domain} rather than the kernel because it returns a {@link PackIdentifier}.
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
     * The schema version the artefact was authored against.
     *
     * @return the {@code schema_version}
     */
    String schemaVersion();

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
     * The DRG edges this artefact contributes, derived per the edge contract (see {@link ArtifactEdges}).
     *
     * @return the edges; empty when the kind mints none, as for a styleguide
     */
    List<DrgEdge> edges();

    /**
     * The artefact's DRG node identity.
     *
     * @return {@code <kind>:<localId>}
     */
    default Urn urn() {
        return new Urn(NodeKind.of(kind()), localId());
    }
}
