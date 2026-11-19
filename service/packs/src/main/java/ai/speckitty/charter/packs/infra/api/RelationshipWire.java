package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.RelationshipDraft;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one mapping between a relationship's wire shape ({@code RelationshipRegistration}) and the kernel
 * {@link RelationshipDraft} the registration binds into a {@link Relationship}.
 * Every controller that reads or answers relationships goes through it, so an unknown URN kind or relation type is
 * refused the same way everywhere: {@link RefusalCode#VALIDATION_FAILED}.
 */
final class RelationshipWire {

    /**
     * The longest {@code from} or {@code to} a request may write, as written, the qualifier included (#443). It is a
     * request cap only: the kernel URN types also parse pack content, whose schemas do not cap a URN.
     */
    static final int MAX_ENDPOINT_LENGTH = 255;

    /**
     * The violation code of a {@code type} that names no relation, a retired one ({@code impacts}, {@code replaces},
     * {@code delegates_to}) among them; the violation points at {@code /type} of the relationship.
     */
    static final String TYPE_UNKNOWN = "relationship_type_unknown";

    private RelationshipWire() {}

    /**
     * The relationship a relationship-route request body asks for, not yet bound: the registration judges its endpoint
     * kinds in its own order.
     *
     * @param wire the request body
     * @return the draft
     * @throws CharterRefusal with {@link RefusalCode#VALIDATION_FAILED} when an endpoint is longer than
     *     {@link #MAX_ENDPOINT_LENGTH} or not a URN of a known kind, the type is unknown (one violation
     *     {@value #TYPE_UNKNOWN} at {@code /type}), the edge is a bare self-loop or a tension of an artifact with a
     *     version of itself, or a source other than a tension's is pack-qualified
     */
    static RelationshipDraft fromWire(RelationshipRegistration wire) {
        try {
            return relationship(wire.getFrom(), wire.getTo(), wire.getType(), wire.getWhen(), wire.getRationale());
        } catch (IllegalArgumentException invalid) {
            throw refused(invalid);
        }
    }

    /**
     * The wire shape of a registered relationship.
     *
     * @param draft the relationship the request asked for
     * @return its response DTO
     */
    static RelationshipRegistration toWire(RelationshipDraft draft) {
        RelationshipRegistration dto = new RelationshipRegistration(
                draft.fromReference().toString(),
                draft.toReference().toString(),
                draft.type().token());
        dto.setRationale(draft.rationale().orElse(null));
        dto.setWhen(draft.when().orElse(null));
        return dto;
    }

    private static RelationshipDraft relationship(
            String from, String to, String type, @Nullable String when, @Nullable String rationale) {
        capped("from", from);
        capped("to", to);
        boolean tension = RelationType.TENSION.token().equals(type);
        PackQualifiedReference fromReference = parsed(from);
        if (fromReference.qualified() && !tension) {
            throw new IllegalArgumentException(
                    "a registered relationship is authored from an artifact of the pack, so its source is not"
                            + " pack-qualified: " + from);
        }
        Urn fromUrn = fromReference
                .asUrn()
                .orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + from));
        PackQualifiedReference toReference = parsed(to);
        Urn toUrn =
                toReference.asUrn().orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + to));
        for (Urn endpoint : List.of(fromUrn, toUrn)) {
            if (!endpoint.kind().edgeTarget()) {
                throw new IllegalArgumentException(GraphBinding.NOT_AUTHORED + endpoint);
            }
        }
        RelationType relationType = RelationType.fromToken(type).orElseThrow(() -> unknownType(type));
        if (relationType.derived()) {
            throw new IllegalArgumentException(
                    "relation type " + type + " is derived from an artifact's own field, never registered");
        }
        if (!fromUrn.kind().edgeSource() || !toUrn.kind().edgeTargetOf(relationType)) {
            throw new IllegalArgumentException(GraphBinding.TERM_ENDPOINT + from + " " + type + " " + to);
        }
        boolean bare =
                fromReference.packName().isEmpty() && toReference.packName().isEmpty();
        if (fromUrn.equals(toUrn) && (bare || relationType == RelationType.TENSION)) {
            // A tension of an artifact with another version of itself is no tension of two rules (ADR-0045 d1).
            throw new IllegalArgumentException("A relationship cannot be a self-loop: " + fromUrn);
        }
        return new RelationshipDraft(
                fromUrn,
                fromReference.packName(),
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

    /** A type that names no relation: the one violation at {@code /type}, so a client need not parse the detail. */
    private static CharterRefusal unknownType(String type) {
        String detail = "unknown relation type: " + type;
        return CharterRefusal.withViolations(
                RefusalCode.VALIDATION_FAILED, detail, List.of(new RefusalViolation("/type", TYPE_UNKNOWN, detail)));
    }

    private static CharterRefusal refused(IllegalArgumentException invalid) {
        return new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
    }
}
