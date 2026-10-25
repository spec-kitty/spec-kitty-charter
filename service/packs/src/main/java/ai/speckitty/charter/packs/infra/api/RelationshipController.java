package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RelationshipsApi;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.api.model.RelationshipRegistration1;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PreconditionException;
import ai.speckitty.charter.packs.application.RelationshipRegistrationService;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for registering a relationship edge: {@code PUT /packs/{packId}/relationships}, idempotent
 * on the unordered {@code {from, to}} pair (FR-009). It delegates to the slice's
 * kind-neutral {@link RelationshipRegistrationService}; the relationship endpoint is authored once here and reused by
 * every regular-artifact slice. A relationship write is a sub-resource write and
 * <strong>requires</strong> an {@code If-Match}: a missing precondition is a plain {@code 428} {@link Problem} and a
 * stale one a {@code 412}, neither a refusal code (ADR-0020). A {@code from} that does not resolve within the pack is
 * a {@code 409} refusal.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class RelationshipController implements RelationshipsApi {

    private final RelationshipRegistrationService registrations;

    /**
     * A controller over the registration use case.
     *
     * @param registrations the registration use case that upserts the relationship
     */
    public RelationshipController(RelationshipRegistrationService registrations) {
        this.registrations = registrations;
    }

    @Override
    public ResponseEntity<RelationshipRegistration> putPackRelationship(
            String packId, RelationshipRegistration1 body, @Nullable String ifMatch) {
        Relationship relationship = toRelationship(body);
        RelationshipRegistrationService.Registered result = registrations.registerRelationship(
                new PackIdentifier(packId), relationship, DirectiveController.unquote(ifMatch));
        return ResponseEntity.ok().eTag(result.revision().token()).body(toDto(relationship));
    }

    /** A stale {@code If-Match} (412) or a missing one (428) is a plain {@code Problem}, not a refusal code. */
    @ExceptionHandler(PreconditionException.class)
    ResponseEntity<Problem> precondition(PreconditionException failure) {
        HttpStatus status = failure.kind() == PreconditionException.Kind.MISSING
                ? HttpStatus.PRECONDITION_REQUIRED
                : HttpStatus.PRECONDITION_FAILED;
        Problem problem = new Problem(status.getReasonPhrase(), status.value());
        problem.setDetail(failure.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static Relationship toRelationship(RelationshipRegistration1 wire) {
        try {
            Urn from = Urn.parse(wire.getFrom())
                    .orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + wire.getFrom()));
            Urn to = Urn.parse(wire.getTo())
                    .orElseThrow(() -> new IllegalArgumentException("not a URN of a known kind: " + wire.getTo()));
            RelationType type = RelationType.fromToken(wire.getType())
                    .orElseThrow(() -> new IllegalArgumentException("unknown relation type: " + wire.getType()));
            return new Relationship(
                    from, to, type, Optional.ofNullable(wire.getWhen()), Optional.ofNullable(wire.getRationale()));
        } catch (IllegalArgumentException invalid) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private static RelationshipRegistration toDto(Relationship relationship) {
        RelationshipRegistration dto = new RelationshipRegistration(
                relationship.from().toString(),
                relationship.to().toString(),
                relationship.type().token());
        dto.setRationale(relationship.rationale().orElse(null));
        dto.setWhen(relationship.when().orElse(null));
        return dto;
    }
}
