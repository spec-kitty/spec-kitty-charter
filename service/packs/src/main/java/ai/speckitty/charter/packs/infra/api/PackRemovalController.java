package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.packs.application.ArtifactRemovalService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.RelationshipRemovalService;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The inbound adapter of the two {@code DELETE}s of a pack (ADR-0065 D4, IC-06): {@code deletePackArtifact} and
 * {@code deletePackRelationship}. Both operations carry the contract's {@code Registration} tag, so the generated
 * interface is {@code RegistrationApi}, which {@link PackRegistrationController} implements; its two methods hand the
 * request here, where the answer is made. Each is synchronous and answers {@code 204} with the pack's new revision as
 * its {@code ETag}. What refuses a removal is the services' and is answered by the refusal handler: a missing
 * {@code If-Match} is a plain {@code 428}, a stale one a plain {@code 412}.
 */
@Component
final class PackRemovalController {

    private final ArtifactRemovalService artifacts;
    private final RelationshipRemovalService relationships;

    /**
     * An adapter over the removal use cases.
     *
     * @param artifacts the removal of an artifact
     * @param relationships the removal of a registered relationship
     */
    @Autowired
    PackRemovalController(ArtifactRemovalService artifacts, RelationshipRemovalService relationships) {
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
    }

    ResponseEntity<Void> deleteArtifact(String packId, String kind, String artifactId, @Nullable String ifMatch) {
        PackRevision revision = artifacts.remove(new PackIdentifier(packId), kind, artifactId, ifMatch);
        return noContent(revision);
    }

    ResponseEntity<Void> deleteRelationship(String packId, String from, String to, @Nullable String ifMatch) {
        PackRevision revision =
                relationships.remove(new PackIdentifier(packId), reference(from), reference(to), ifMatch);
        return noContent(revision);
    }

    private static ResponseEntity<Void> noContent(PackRevision revision) {
        return ResponseEntity.noContent().eTag(revision.token()).build();
    }

    /** A query parameter that is no reference is a malformed request, a plain {@code 400}. */
    private static PackQualifiedReference reference(String text) {
        try {
            return PackQualifiedReference.parse(text);
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'" + text + "' is no <kind>:<id> or <pack>::<kind>:<id> reference",
                    malformed);
        }
    }
}
