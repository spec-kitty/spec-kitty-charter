package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;

/**
 * The single copy of the pure relationship rules every registration service shares: the dual-write ownership check,
 * the writable-relation rule and the unordered-pair upsert. Package-private on purpose: it is an application-internal
 * helper, not a port.
 */
final class RelationshipMerge {

    private RelationshipMerge() {}

    /**
     * Enforces the dual-write ownership rule: an artifact body may assert only relationships authored from itself.
     *
     * @param kind the artifact kind's name, for the refusal message (for example {@code "directive"})
     * @param owner the URN of the artifact being registered
     * @param bodyRelationships the relationships carried in the body
     * @throws CharterRefusal {@link RefusalCode#VALIDATION_FAILED} when a relationship is authored from another URN
     */
    static void ownBodyRelationships(String kind, Urn owner, List<Relationship> bodyRelationships) {
        for (Relationship relationship : bodyRelationships) {
            if (!relationship.from().equals(owner)) {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "A " + kind + " body may assert only relationships authored from "
                                + owner + ", but one is authored from " + relationship.from()
                                + "; register a cross-authored edge through PUT /relationships.");
            }
        }
    }

    /** Upserts every {@code incoming} relationship into {@code existing}, in order, via {@link #upsert}. */
    static List<Relationship> mergeAll(List<Relationship> existing, List<Relationship> incoming) {
        List<Relationship> merged = new ArrayList<>(existing);
        for (Relationship relationship : incoming) {
            merged = new ArrayList<>(upsert(merged, relationship));
        }
        return List.copyOf(merged);
    }

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
