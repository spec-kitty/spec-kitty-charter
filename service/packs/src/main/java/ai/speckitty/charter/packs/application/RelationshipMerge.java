package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
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
     * <p>Every registration door reaches the shard through here, so this is where the writable-relation rule lives:
     * an {@code impacts} edge is refused, because its signed strength is required on read and the registration wire
     * does not model it yet; persisting one would write a shard the reader refuses.
     *
     * @throws CharterRefusal {@link RefusalCode#VALIDATION_FAILED} when {@code incoming} is an {@code impacts} edge
     */
    static List<Relationship> upsert(List<Relationship> existing, Relationship incoming) {
        if (incoming.type() == RelationType.IMPACTS) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "An impacts relationship cannot be registered yet: its signed strength is not part of the"
                            + " registration body, and an impacts edge without one is invalid.");
        }
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
