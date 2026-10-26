package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one mapping between a relationship's wire shape ({@code RelationshipRegistration}) and the kernel
 * {@link Relationship}.
 * Every controller that reads or answers relationships goes through it, so an unknown URN kind or relation type is
 * refused the same way everywhere: {@link RefusalCode#VALIDATION_FAILED}.
 */
final class RelationshipWire {

    private RelationshipWire() {}

    /**
     * The relationships of a registration body.
     *
     * @param wire the body's relationships
     * @return the kernel relationships, in body order
     * @throws CharterRefusal with {@link RefusalCode#VALIDATION_FAILED} when any endpoint is not a URN of a known kind
     *     or the type is unknown
     */
    static List<Relationship> fromWire(List<RelationshipRegistration> wire) {
        try {
            return wire.stream()
                    .map(r -> relationship(r.getFrom(), r.getTo(), r.getType(), r.getWhen(), r.getRationale()))
                    .toList();
        } catch (IllegalArgumentException invalid) {
            throw refused(invalid);
        }
    }

    /**
     * The relationship of a relationship-route request body.
     *
     * @param wire the request body
     * @return the kernel relationship
     * @throws CharterRefusal with {@link RefusalCode#VALIDATION_FAILED} when an endpoint is not a URN of a known kind
     *     or the type is unknown
     */
    static Relationship fromWire(RelationshipRegistration wire) {
        try {
            return relationship(wire.getFrom(), wire.getTo(), wire.getType(), wire.getWhen(), wire.getRationale());
        } catch (IllegalArgumentException invalid) {
            throw refused(invalid);
        }
    }

    /**
     * The wire shape of a relationship.
     *
     * @param relationship the kernel relationship
     * @return its response DTO
     */
    static RelationshipRegistration toWire(Relationship relationship) {
        RelationshipRegistration dto = new RelationshipRegistration(
                relationship.from().toString(),
                relationship.to().toString(),
                relationship.type().token());
        dto.setRationale(relationship.rationale().orElse(null));
        dto.setWhen(relationship.when().orElse(null));
        return dto;
    }

    private static Relationship relationship(
            String from, String to, String type, @Nullable String when, @Nullable String rationale) {
        Urn fromUrn =
                Urn.parse(from).orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + from));
        Urn toUrn = Urn.parse(to).orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + to));
        RelationType relationType = RelationType.fromToken(type)
                .orElseThrow(() -> new IllegalArgumentException("unknown relation type: " + type));
        return new Relationship(
                fromUrn, toUrn, relationType, Optional.ofNullable(when), Optional.ofNullable(rationale));
    }

    private static CharterRefusal refused(IllegalArgumentException invalid) {
        return new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
    }
}
