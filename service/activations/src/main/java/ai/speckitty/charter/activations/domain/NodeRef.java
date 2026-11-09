package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * One node of the graph, by kind and id (the contract's {@code NodeReference}): the reference of a node that may not be an
 * activatable artifact, such as a step of a mission type ({@code action:<missionType>/<step>}, kind
 * {@link NodeKind#ACTION}). The reuse rule is the contract's: {@link ArtifactRef} for an artifact an activation can select
 * or deny, {@code NodeRef} for a node that may not be activatable, and {@link Urn} for a graph identity. The node kinds are
 * the authored ones: a guard and a lifecycle cannot be named. Ordered by kind token, then id, both by code point, so every
 * list of them is sorted the same way on every run.
 *
 * <p>The activations slice's reads (the effective graph and the action context) use this value and define no second
 * copy of it. The {@code packs} slice may not import it (the ArchUnit gate), so it names a node by {@link Urn}.
 *
 * @param kind the node's kind
 * @param id the node's id, the tail of its URN
 */
public record NodeRef(NodeKind kind, String id) implements Comparable<NodeRef> {

    private static final Comparator<NodeRef> ORDER = Comparator.comparing(
                    (NodeRef ref) -> ref.kind().token(), ActivationDocument.CODE_POINT_ORDER)
            .thenComparing(NodeRef::id, ActivationDocument.CODE_POINT_ORDER);

    /**
     * Checks the fields.
     *
     * @param kind the kind
     * @param id the id
     * @throws IllegalArgumentException when the kind is not one graphs author (a guard or a lifecycle)
     */
    public NodeRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (!kind.authoredInGraphs()) {
            throw new IllegalArgumentException("A node reference names an authored kind, not " + kind.token() + ".");
        }
    }

    /**
     * The node a URN names.
     *
     * @param urn the URN
     * @return the node; empty for a kind no graph authors (a guard or a lifecycle)
     */
    public static Optional<NodeRef> of(Urn urn) {
        return urn.kind().authoredInGraphs() ? Optional.of(new NodeRef(urn.kind(), urn.id())) : Optional.empty();
    }

    /**
     * The node an artifact is.
     *
     * @param ref the artifact
     * @return the node of the artifact's kind
     */
    public static NodeRef of(ArtifactRef ref) {
        return new NodeRef(NodeKind.of(ref.kind()), ref.id());
    }

    /**
     * The URN text, {@code <kind>:<id>}.
     *
     * @return the URN text
     */
    public String urn() {
        return new Urn(kind, id).toString();
    }

    @Override
    public int compareTo(NodeRef other) {
        return ORDER.compare(this, other);
    }
}
