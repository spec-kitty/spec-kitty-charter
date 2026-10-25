package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Directionality;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the {@link Relationship}s an artefact is the effective origin of, from its references. The relations are the
 * interim mapping of research R2, pending confirmation against core: typed references {@link RelationType#SUGGESTS},
 * bare references {@link RelationType#REQUIRES}. No relationship carries an {@code impacts} strength (that lives on the
 * storage DSO only; ADR-0025 Decision 5).
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
     * Builds the relationships of one artefact: every one authored from the owner. Each is kept only when the owner is
     * its {@linkplain #effectiveOrigin(Urn, List) effective origin}, which an authored-from-owner relationship always
     * is; the filter is the single guard that a derivation can never leak a relationship the owner would climb back up.
     *
     * @param owner the artefact's node
     * @param typed its typed references
     * @param bareTargetKind the target kind of its bare references: {@code PROCEDURE} for a directive's
     *     procedures, {@code DIRECTIVE} for a paradigm's directive refs
     * @param bare its bare reference ids; callers guarantee they are non-blank
     * @return typed relationships first, then bare relationships, each in the given order
     * @throws java.util.NoSuchElementException when a typed reference names an unknown kind token
     */
    static List<Relationship> build(Urn owner, List<TypedRef> typed, NodeKind bareTargetKind, List<String> bare) {
        List<Relationship> relationships = new ArrayList<>(typed.size() + bare.size());
        for (TypedRef ref : typed) {
            NodeKind kind = NodeKind.of(ArtifactKind.fromToken(ref.type()).orElseThrow());
            relationships.add(
                    new Relationship(owner, new Urn(kind, ref.id()), RelationType.SUGGESTS, ref.when(), ref.reason()));
        }
        for (String id : bare) {
            relationships.add(Relationship.of(owner, new Urn(bareTargetKind, id), RelationType.REQUIRES));
        }
        return effectiveOrigin(owner, relationships);
    }

    /**
     * Selects the relationships a component is the effective origin of (ADR-0025 Decision 3, FR-003): every
     * relationship whose {@code from} is the component, plus every {@link Directionality#BIDIRECTIONAL} relationship
     * whose {@code to} is the component. A purely {@link Directionality#DIRECTED} relationship with the component as
     * {@code to} is excluded, so a walk never climbs back up a {@code requires}.
     *
     * @param self the component whose effective-origin relationships are wanted
     * @param pool the relationships the component participates in, in authored order
     * @return the effective-origin subset, preserving the pool's order
     */
    static List<Relationship> effectiveOrigin(Urn self, List<Relationship> pool) {
        return pool.stream()
                .filter(relationship -> isEffectiveOrigin(self, relationship))
                .toList();
    }

    private static boolean isEffectiveOrigin(Urn self, Relationship relationship) {
        if (relationship.from().equals(self)) {
            return true;
        }
        return relationship.type().directionality() == Directionality.BIDIRECTIONAL
                && relationship.to().equals(self);
    }
}
