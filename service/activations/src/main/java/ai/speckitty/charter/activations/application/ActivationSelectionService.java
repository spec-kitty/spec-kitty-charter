package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeRules;
import ai.speckitty.charter.activations.domain.CascadeScope;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Select and unselect one artifact of an activation (FR-011 to FR-014, ADR-0020 D6, ADR-0059 D2): the preconditions,
 * the one read of the packs and the compare-and-set are {@link ActivationWrites}'; the cascade is
 * {@link CascadeRules}'; {@code applied} and {@code cleared} are {@link ActivationChange#of}'s.
 *
 * <p>A select of kind {@code mission_type} adds the id to {@code missionTypes} and never cascades: no cascade edge
 * leaves a mission type here, so its {@code cascade} is checked for its shape and then ignored, and so is
 * {@code includeDeactivated}.
 */
@Service
public class ActivationSelectionService {

    private final ActivationWrites writes;

    /**
     * The select and the unselect over the shared write flow.
     *
     * @param writes the shared write flow
     */
    @Autowired
    public ActivationSelectionService(ActivationWrites writes) {
        this.writes = Objects.requireNonNull(writes, "writes");
    }

    /**
     * Selects one artifact, or one mission type, whose request body may be malformed.
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param cascade the body's {@code cascade} tokens; empty when absent
     * @param includeDeactivated whether the cascade takes denied artifacts back, clearing their deny entries
     * @param bodyFaults the faults the inbound adapter found in the body, refused once the preconditions hold
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     */
    public ActivationWrites.Outcome select(
            ActivationId id,
            String kind,
            String artifactId,
            Optional<List<String>> cascade,
            boolean includeDeactivated,
            List<RefusalViolation> bodyFaults,
            @Nullable String ifMatch) {
        StoredActivation loaded = writes.loaded(id, ifMatch);
        ActivationWrites.requireWellFormed(bodyFaults);
        ArtifactRef target = ActivationWrites.selectable(kind, artifactId);
        CascadeScope written = ActivationWrites.scope(cascade);
        CascadeScope scope = target.kind() == ArtifactKind.MISSION_TYPE ? CascadeScope.none() : written;
        PacksSession session = writes.session(loaded);
        ActivationWrites.requireHeld(session.snapshot(), target);
        ActivationDocument before = loaded.document();
        Reach reach =
                ActivationWrites.walk(session, CascadeRules.planSelect(before, target, scope, includeDeactivated));
        ActivationChange change = CascadeRules.applySelect(before, target, scope, includeDeactivated, reach);
        return writes.commit(loaded, change, session.snapshot());
    }

    /**
     * Unselects one artifact, or one mission type: it leaves {@code selected} (or {@code missionTypes}), and nothing
     * else changes. One that is not selected is a no-op.
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     */
    public ActivationWrites.Outcome unselect(
            ActivationId id, String kind, String artifactId, @Nullable String ifMatch) {
        StoredActivation loaded = writes.loaded(id, ifMatch);
        ArtifactRef target = ActivationWrites.selectable(kind, artifactId);
        PacksSession session = writes.session(loaded);
        ActivationWrites.requireHeld(session.snapshot(), target);
        ActivationDocument before = loaded.document();
        ActivationDocument after = before.withoutSelected(target.kind(), target.id());
        return writes.commit(loaded, ActivationChange.of(before, after, SkippedReport.empty()), session.snapshot());
    }
}
