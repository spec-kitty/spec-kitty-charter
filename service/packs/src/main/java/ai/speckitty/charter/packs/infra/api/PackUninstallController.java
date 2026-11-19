package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PackImpact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackUninstallService;
import ai.speckitty.charter.packs.application.RevisionTrail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The inbound adapter of {@code DELETE /packs/{packId}} (ADR-0081 D8), which {@link PackRegistrationController} hands the
 * request to: the generated {@code RegistrationApi} carries the operation. A removal answers {@code 204} with no body;
 * a dry run answers {@code 200} with the {@link PackImpact}. What refuses a removal is the service's and is answered by
 * the refusal handler. The revision the removal found is entered in the request's audit entry (NFR-010); a dry run is
 * not audited (the audit filter skips it).
 */
@Component
final class PackUninstallController {

    private final PackUninstallService uninstalls;
    private final PackRegistrationMapper mapper;

    /**
     * An adapter over the removal of a pack.
     *
     * @param uninstalls the removal use case
     * @param mapper the contract shapes
     */
    @Autowired
    PackUninstallController(PackUninstallService uninstalls, PackRegistrationMapper mapper) {
        this.uninstalls = Objects.requireNonNull(uninstalls, "uninstalls");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    ResponseEntity<PackImpact> delete(String packId, @Nullable String ifMatch, boolean dryRun) {
        AuditContext audit = AuditContext.of(currentRequest());
        PackUninstallService.Outcome outcome = uninstalls.uninstall(
                new PackIdentifier(packId),
                ifMatch,
                dryRun ? PackRegistrationService.Mode.DRY_RUN : PackRegistrationService.Mode.COMMIT,
                trailOf(audit));
        if (outcome.dryRun()) {
            return ResponseEntity.ok(mapper.impact(outcome));
        }
        return ResponseEntity.noContent().build();
    }

    /** The revision the removal found, entered as soon as it is read, so a removal refused after it still carries it. */
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
}
