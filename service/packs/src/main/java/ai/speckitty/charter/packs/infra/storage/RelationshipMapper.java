package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.Optional;

/**
 * Converts the kernel {@link Relationship} to the storage {@link DrgEdge} DSO on the write side of the YAML
 * boundary (ADR-0025 Decision 5); the domain never sees {@code DrgEdge} and the DSO never carries domain behaviour.
 * The read side binds a shard edge through {@code PackPolicy}, the one authority for a relationship's invariants
 * (a URN per endpoint, a known relation, no self-loop), so a written shard is read back through the same rules as
 * any other (FR-005, NFR-003: the relation and a present-vs-absent {@code when}/{@code rationale} survive).
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
}
