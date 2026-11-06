package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * One artifact, by kind and declared id (the contract's {@code ArtifactReference}). Ordered by kind token, then id,
 * both by code point, so every report the cascade makes is sorted the same way on every run.
 *
 * @param kind the artifact's kind
 * @param id the artifact's declared id
 */
public record ArtifactRef(ArtifactKind kind, String id) implements Comparable<ArtifactRef> {

    private static final Comparator<ArtifactRef> ORDER = Comparator.comparing(
                    (ArtifactRef ref) -> ref.kind().token(), ActivationDocument.CODE_POINT_ORDER)
            .thenComparing(ArtifactRef::id, ActivationDocument.CODE_POINT_ORDER);

    /**
     * Checks the fields.
     *
     * @param kind the kind
     * @param id the id
     */
    public ArtifactRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    /**
     * The artifact a URN names, when it names an artifact of an {@link ArtifactKind}.
     *
     * @param urn the URN
     * @return the artifact; empty for a node kind that is no artifact kind
     */
    public static Optional<ArtifactRef> of(Urn urn) {
        return urn.kind().artifactKind().map(kind -> new ArtifactRef(kind, urn.id()));
    }

    /**
     * The URN text, {@code <kind>:<id>}.
     *
     * @return the URN text
     */
    public String urn() {
        return new Urn(NodeKind.of(kind), id).toString();
    }

    @Override
    public int compareTo(ArtifactRef other) {
        return ORDER.compare(this, other);
    }
}
