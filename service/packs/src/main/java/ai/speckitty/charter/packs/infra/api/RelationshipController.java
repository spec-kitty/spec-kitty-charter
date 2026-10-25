package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RelationshipsApi;
import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.api.model.RelationshipRegistration1;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.RelationshipRegistrationService;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound REST adapter for registering a relationship edge: {@code PUT /packs/{packId}/relationships}, idempotent
 * on the unordered {@code {from, to}} pair (FR-009): {@code 201} when the pair is new, {@code 200} when it replaces a
 * registered one (ADR-0018 D10 as amended). It delegates to the slice's
 * kind-neutral {@link RelationshipRegistrationService}; the relationship endpoint is authored once here and reused by
 * every regular-artifact slice. A relationship write is a sub-resource write and
 * <strong>requires</strong> an {@code If-Match}: a missing precondition is a plain {@code 428} problem and a
 * stale one a {@code 412}, neither a refusal code (ADR-0020); {@link RefusalHandler} answers both. A {@code from} that does not resolve within the pack is
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
        Relationship relationship = RelationshipWire.fromWire(body);
        RelationshipRegistrationService.Registered result =
                registrations.registerRelationship(new PackIdentifier(packId), relationship, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(RelationshipWire.toWire(relationship));
    }
}
