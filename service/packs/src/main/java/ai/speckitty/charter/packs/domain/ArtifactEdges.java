package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the DRG edges of an artefact from its references. The relations are the interim mapping of research R2,
 * pending confirmation against core: typed references {@code SUGGESTS}, bare references {@code REQUIRES}. No edge
 * carries an {@code impacts} strength.
 */
final class ArtifactEdges {

    private ArtifactEdges() {}

    /**
     * A typed reference as authored.
     *
     * @param type the artifact-kind token of the target
     * @param id the target's id
     * @param when the condition, if authored
     * @param reason the reason, if authored
     */
    record TypedRef(String type, String id, Optional<String> when, Optional<String> reason) {
        TypedRef {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Builds the edges of one artefact.
     *
     * @param owner the artefact's node
     * @param typed its typed references
     * @param bareTargetKind the target kind of its bare references: {@code PROCEDURE} for a directive's
     *     procedures, {@code DIRECTIVE} for a paradigm's directive refs
     * @param bare its bare reference ids; callers guarantee they are non-blank
     * @return typed edges first, then bare edges, each in the given order
     * @throws java.util.NoSuchElementException when a typed reference names an unknown kind token
     */
    static List<DrgEdge> build(Urn owner, List<TypedRef> typed, NodeKind bareTargetKind, List<String> bare) {
        List<DrgEdge> edges = new ArrayList<>(typed.size() + bare.size());
        for (TypedRef ref : typed) {
            NodeKind kind = NodeKind.of(ArtifactKind.fromToken(ref.type()).orElseThrow());
            edges.add(new DrgEdge(
                    owner, new Urn(kind, ref.id()), Relation.SUGGESTS, Optional.empty(), ref.when(), ref.reason()));
        }
        for (String id : bare) {
            edges.add(DrgEdge.of(owner, new Urn(bareTargetKind, id), Relation.REQUIRES));
        }
        return List.copyOf(edges);
    }
}
