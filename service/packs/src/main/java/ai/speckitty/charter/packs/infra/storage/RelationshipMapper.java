package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Optional;

/**
 * Converts between the kernel {@link Relationship} and the storage {@link DrgEdge} DSO at the YAML boundary
 * (ADR-0025 Decision 5). This is the one place the two shapes meet; the domain never sees {@code DrgEdge} and
 * the DSO never carries domain behaviour.
 *
 * <p>The round-trip is lossless for a relationship's identity and its authored {@code when}/{@code rationale}
 * (FR-005, NFR-003): {@code toDomain(toDso(r)).equals(r)} for every {@code r}, and a present {@code when} or
 * {@code rationale} survives distinct from an absent one.
 *
 * <p>{@code impacts} is a DSO-only concern (ADR-0025: the signed strength lives on the DSO, and a
 * {@code Relationship} carries no strength today), so {@link #toDso(Relationship)} emits an empty {@code impacts}.
 * The read side's {@code PackPolicy} requires a strength on an {@code impacts} edge, which is why the registration
 * write path refuses an {@code impacts} relationship before it reaches this mapper ({@code RelationshipMerge}).
 */
final class RelationshipMapper {

    private RelationshipMapper() {}

    /**
     * The DSO of a relationship: the endpoints and relation as token strings, the authored texts carried
     * through, and an empty {@code impacts} (no strength is minted on a registered relationship).
     *
     * @param relationship the relationship
     * @return the DSO mirror of {@code relationship}
     */
    static DrgEdge toDso(Relationship relationship) {
        return new DrgEdge(
                relationship.from().toString(),
                relationship.to().toString(),
                relationship.type().token(),
                Optional.empty(),
                relationship.when(),
                relationship.rationale());
    }

    /**
     * The relationship of a DSO: the endpoints parsed as URNs, the relation resolved from its token, and the
     * authored {@code when}/{@code reason} carried to {@code when}/{@code rationale}. A non-URN endpoint, an
     * unknown relation token or a self-loop is a mapper fault surfaced to the caller as an
     * {@link IllegalArgumentException} — never a silent drop (ADR-0027: the charter does not drop an edge on
     * read).
     *
     * @param edge the DSO
     * @return the relationship
     * @throws IllegalArgumentException when an endpoint is not a URN, the relation token is unknown, or the
     *     endpoints are equal (a self-loop)
     */
    static Relationship toDomain(DrgEdge edge) {
        Urn from = Urn.parse(edge.source())
                .orElseThrow(() -> new IllegalArgumentException("edge endpoint '" + edge.source() + "' is not a URN"));
        Urn to = Urn.parse(edge.target())
                .orElseThrow(() -> new IllegalArgumentException("edge endpoint '" + edge.target() + "' is not a URN"));
        RelationType type = RelationType.fromToken(edge.relation())
                .orElseThrow(() -> new IllegalArgumentException("unknown relation '" + edge.relation() + "'"));
        return new Relationship(from, to, type, edge.when(), edge.reason());
    }
}
