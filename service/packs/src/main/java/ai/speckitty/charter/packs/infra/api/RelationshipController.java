package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RelationshipsApi;
import ai.speckitty.charter.api.model.RelationshipRegistered;
import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.RelationshipRegistrationService;
import java.util.Map;
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
 *
 * <p>The answer is a {@code RelationshipRegistered} (R-13): the registered edge, unchanged, and {@code _links} holding
 * {@code artifacts}, the list of the pack's artifacts, built by {@link Hypermedia}. It has no {@code self} and no
 * {@code Location}: the relationships path has no {@code GET}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class RelationshipController implements RelationshipsApi {

    private final RelationshipRegistrationService registrations;
    private final Hypermedia links;

    /**
     * A controller over the registration use case.
     *
     * @param registrations the registration use case that upserts the relationship
     * @param links the link builder
     */
    RelationshipController(RelationshipRegistrationService registrations, Hypermedia links) {
        this.registrations = registrations;
        this.links = links;
    }

    @Override
    public ResponseEntity<RelationshipRegistered> putPackRelationship(
            String packId, RelationshipRegistration body, @Nullable String ifMatch) {
        Relationship relationship = RelationshipWire.fromWire(body);
        RelationshipRegistrationService.Registered result =
                registrations.registerRelationship(new PackIdentifier(packId), relationship, ifMatch);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.revision().token())
                .body(new RelationshipRegistered(
                        RelationshipWire.toWire(relationship),
                        Hypermedia.wire(Map.of(Relation.ARTIFACTS, links.link("packs", packId, "artifacts")))));
    }
}
