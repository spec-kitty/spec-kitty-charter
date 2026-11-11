package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackPolicy;
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

    /**
     * The longest {@code from} or {@code to} a request may write, as written, the qualifier included (#443). It is a
     * request cap only: the kernel URN types also parse pack content, whose schemas do not cap a URN.
     */
    static final int MAX_ENDPOINT_LENGTH = 255;

    private RelationshipWire() {}

    /**
     * The relationship of a relationship-route request body.
     *
     * @param wire the request body
     * @return the kernel relationship
     * @throws CharterRefusal with {@link RefusalCode#VALIDATION_FAILED} when an endpoint is longer than
     *     {@link #MAX_ENDPOINT_LENGTH} or not a URN of a known kind, or the type is unknown
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
                relationship.fromReference().toString(),
                relationship.toReference().toString(),
                relationship.type().token());
        dto.setRationale(relationship.rationale().orElse(null));
        dto.setWhen(relationship.when().orElse(null));
        return dto;
    }

    private static Relationship relationship(
            String from, String to, String type, @Nullable String when, @Nullable String rationale) {
        capped("from", from);
        capped("to", to);
        if (parsed(from).qualified()) {
            throw new IllegalArgumentException(
                    "a registered relationship is authored from an artifact of the pack, so its source is not"
                            + " pack-qualified: " + from);
        }
        Urn fromUrn =
                Urn.parse(from).orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + from));
        PackQualifiedReference toReference = parsed(to);
        Urn toUrn =
                toReference.asUrn().orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + to));
        for (Urn endpoint : List.of(fromUrn, toUrn)) {
            if (!endpoint.kind().authoredInGraphs()) {
                throw new IllegalArgumentException(PackPolicy.NOT_AUTHORED + endpoint);
            }
        }
        RelationType relationType = RelationType.fromToken(type)
                .orElseThrow(() -> new IllegalArgumentException("unknown relation type: " + type));
        if (relationType.derived()) {
            throw new IllegalArgumentException(
                    "relation type " + type + " is derived from an artifact's own field, never registered");
        }
        return new Relationship(
                fromUrn,
                Optional.empty(),
                toUrn,
                toReference.packName(),
                relationType,
                Optional.ofNullable(when),
                Optional.ofNullable(rationale));
    }

    /** An endpoint longer than the request cap is refused before it is parsed. */
    private static void capped(String member, String endpoint) {
        if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException(member + " is longer than " + MAX_ENDPOINT_LENGTH + " characters.");
        }
    }

    /** The target as written, bare or pack-qualified; a malformed qualifier is a refused registration. */
    private static PackQualifiedReference parsed(String reference) {
        try {
            return PackQualifiedReference.parse(reference);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("not a URN of a known kind: " + reference, malformed);
        }
    }

    private static CharterRefusal refused(IllegalArgumentException invalid) {
        return new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
    }
}
