package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.ArrayList;
import java.util.List;

/**
 * The single copy of the pure relationship rules of the one edge write (ADR-0064): the writable-relation rule and the
 * unordered-pair upsert. Package-private on purpose: it is an application-internal
 * helper, not a port.
 */
final class RelationshipMerge {

    private RelationshipMerge() {}

    /**
     * Upserts {@code incoming} into {@code existing} on the unordered {@code {from, to}} pair, last-write-wins. It is
     * an explicit put, not a {@code Set.add}: {@link Relationship#equals(Object)} is pair-identity, so any
     * relationship already on the pair (in either orientation) is dropped and {@code incoming} appended. It is the only
     * upsert of registered relationships; the storage shard only reads and serialises the result.
     *
     */
    static List<Relationship> upsert(List<Relationship> existing, Relationship incoming) {
        List<Relationship> merged = new ArrayList<>(existing.size() + 1);
        for (Relationship relationship : existing) {
            if (!relationship.equals(incoming)) {
                merged.add(relationship);
            }
        }
        merged.add(incoming);
        return List.copyOf(merged);
    }
}
