package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PackImpact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.application.ArtifactRemovalService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.RelationshipRemovalService;
import ai.speckitty.charter.packs.application.RevisionTrail;
import ai.speckitty.charter.web.InvalidParameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The inbound adapter of the {@code DELETE}s of a pack (ADR-0065 D4, IC-06; ADR-0081 D8): {@code deletePackArtifact},
 * {@code deletePackRelationship}, and {@code deletePack}, which {@link PackUninstallController} answers. Both operations carry the contract's {@code Registration} tag, so the generated
 * interface is {@code RegistrationApi}, which {@link PackRegistrationController} implements; its two methods hand the
 * request here, where the answer is made. Each is synchronous and answers {@code 204} with the pack's new revision as
 * its {@code ETag}. What refuses a removal is the services' and is answered by the refusal handler: a missing
 * {@code If-Match} is a plain {@code 428}, a stale one a plain {@code 412}.
 */
@Component
final class PackRemovalController {

    private final ArtifactRemovalService artifacts;
    private final RelationshipRemovalService relationships;
    private final PackUninstallController packs;

    /**
     * An adapter over the removal use cases.
     *
     * @param artifacts the removal of an artifact
     * @param relationships the removal of a registered relationship
     * @param packs the removal of a pack whole
     */
    @Autowired
    PackRemovalController(
            ArtifactRemovalService artifacts, RelationshipRemovalService relationships, PackUninstallController packs) {
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    ResponseEntity<PackImpact> deletePack(String packId, @Nullable String ifMatch, boolean dryRun) {
        return packs.delete(packId, ifMatch, dryRun);
    }

    ResponseEntity<Void> deleteArtifact(String packId, String kind, String artifactId, @Nullable String ifMatch) {
        AuditContext audit = AuditContext.of(currentRequest());
        PackRevision revision = artifacts.remove(new PackIdentifier(packId), kind, artifactId, ifMatch, trailOf(audit));
        return noContent(revision);
    }

    ResponseEntity<Void> deleteRelationship(
            String packId, String from, String to, @Nullable String relation, @Nullable String ifMatch) {
        Optional<RelationType> type = relationOf(relation);
        AuditContext audit = AuditContext.of(currentRequest());
        PackRevision revision = relationships.remove(
                new PackIdentifier(packId),
                reference("from", from),
                reference("to", to),
                type,
                ifMatch,
                trailOf(audit));
        return noContent(revision);
    }

    /**
     * The relation the removal names (ADR-0045 decision 5), judged before the pack: a token that is no value of the
     * contract's {@code Relation} is a 400 Problem.
     */
    private static Optional<RelationType> relationOf(@Nullable String relation) {
        if (relation == null) {
            return Optional.empty();
        }
        return Optional.of(RelationType.fromToken(relation)
                .filter(type -> !type.derived())
                .orElseThrow(() -> new InvalidParameter(
                        InvalidParameter.In.QUERY, "relation", relation, "which names no relation")));
    }

    /**
     * The revisions the removal found and left, entered in the audit entry of the request (NFR-010). The revision found
     * is entered as soon as the service has read it, so a removal refused after that (409, 412, 422) still carries it;
     * the revision left is entered only when the pack moved.
     */
    private static RevisionTrail trailOf(AuditContext audit) {
        return new RevisionTrail() {
            @Override
            public void found(PackRevision before) {
                audit.revisionBefore(before.token());
            }

            @Override
            public void moved(PackRevision before, PackRevision after) {
                audit.revisionBefore(before.token());
                audit.revisionAfter(after.token());
            }
        };
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        throw new IllegalStateException("no servlet request is being handled");
    }

    private static ResponseEntity<Void> noContent(PackRevision revision) {
        return ResponseEntity.noContent().eTag(revision.token()).build();
    }

    /** A query parameter that is no reference is a malformed request: a {@code 400} whose detail names it (#411). */
    private static PackQualifiedReference reference(String name, String text) {
        try {
            return PackQualifiedReference.parse(text);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidParameter(
                    InvalidParameter.In.QUERY, name, text, "which is no <kind>:<id> or <pack>::<kind>:<id> reference");
        }
    }
}
