package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Objects;
import java.util.Optional;

/**
 * A relationship a write asks to register, before it is bound: its endpoint kinds are not yet judged by the endpoint
 * table (ADR-0064 decision 13), so the registration can refuse it in its own order, each fault with its own code. A
 * registered source is authored in the pack, so it is bare, except the source of a {@code tension} between two
 * artifacts the pack holds neither of, which names its pack (ADR-0045 decision 1).
 *
 * @param from the source
 * @param fromPack the pack the source reference names; empty for a bare reference
 * @param to the target
 * @param toPack the pack the target reference names; empty for a bare reference
 * @param type the relation
 * @param when when the relationship applies; empty when absent
 * @param rationale why it holds; empty when absent
 */
public record RelationshipDraft(
        Urn from,
        Optional<String> fromPack,
        Urn to,
        Optional<String> toPack,
        RelationType type,
        Optional<String> when,
        Optional<String> rationale) {

    /**
     * Checks that every field is present.
     *
     * @param from the source
     * @param fromPack the source's pack
     * @param to the target
     * @param toPack the target's pack
     * @param type the relation
     * @param when the condition
     * @param rationale the reason
     */
    public RelationshipDraft {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(fromPack, "fromPack");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(toPack, "toPack");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(rationale, "rationale");
    }

    /**
     * The draft of a relationship already bound, as a test or a caller that holds one writes it.
     *
     * @param relationship the relationship
     * @return the draft
     */
    public static RelationshipDraft of(Relationship relationship) {
        return new RelationshipDraft(
                relationship.from(),
                relationship.fromPack(),
                relationship.to(),
                relationship.toPack(),
                relationship.type(),
                relationship.when(),
                relationship.rationale());
    }

    /**
     * The source as written.
     *
     * @return the reference
     */
    public PackQualifiedReference fromReference() {
        return new PackQualifiedReference(fromPack, from.toString());
    }

    /**
     * The same relationship with its two ends swapped, as a {@code tension} is stored in its stored orientation
     * (ADR-0045 decision 1).
     *
     * @return the reversed draft
     */
    public RelationshipDraft reversed() {
        return new RelationshipDraft(to, toPack, from, fromPack, type, when, rationale);
    }

    /**
     * The target as written.
     *
     * @return the reference
     */
    public PackQualifiedReference toReference() {
        return new PackQualifiedReference(toPack, to.toString());
    }

    /**
     * Binds the draft through the endpoint table.
     *
     * @return the relationship, or the table's fault
     */
    public EndpointRule.Binding bind() {
        return EndpointRule.bind(type, from, fromPack, to, toPack, when, rationale);
    }
}
