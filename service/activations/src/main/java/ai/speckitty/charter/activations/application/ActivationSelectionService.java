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
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Select and unselect one artifact of an activation (FR-011 to FR-014, ADR-0020 D6, ADR-0059 D2): the preconditions,
 * the one read of the packs and the compare-and-set are {@link ActivationWrites}'; the cascade is
 * {@link CascadeRules}'; {@code applied} and {@code cleared} are {@link ActivationChange#of}'s.
 *
 * <p>A mission type is selected like any kind (A7 Q1, rule W32 withdrawn): it joins {@code selected.mission_type}, and
 * with a cascade the walk crosses from it to the steps it holds like {@code requires} (rule W20).
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
     * Selects one artifact, or one mission type, whose request body may be malformed, with an unlimited cascade.
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
        return select(id, kind, artifactId, cascade, includeDeactivated, OptionalInt.empty(), bodyFaults, ifMatch);
    }

    /**
     * Selects one artifact, or one mission type, whose request body may be malformed.
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param cascade the body's {@code cascade} tokens; empty when absent
     * @param includeDeactivated whether the cascade takes denied artifacts back, clearing their deny entries
     * @param depth the body's {@code depth}: the {@code suggests} hops the cascade may cross; empty when absent, which
     *     is unlimited (IC-21, FR-043)
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
            OptionalInt depth,
            List<RefusalViolation> bodyFaults,
            @Nullable String ifMatch) {
        return writes.exclusively(() -> {
            StoredActivation loaded = writes.loaded(id, ifMatch);
            ActivationWrites.requireWellFormed(bodyFaults);
            ArtifactRef written = ActivationWrites.selectable(kind, artifactId);
            CascadeScope scope = ActivationWrites.scope(cascade);
            PacksSession session = writes.session(loaded);
            ArtifactRef target = ActivationWrites.declared(session.snapshot(), written, Set.of());
            ActivationWrites.requireHeld(session.snapshot(), target);
            ActivationDocument before = ActivationWrites.current(loaded, session.snapshot());
            Reach reach = ActivationWrites.walk(
                    session, CascadeRules.planSelect(before, target, scope, includeDeactivated, depth));
            ActivationChange change = CascadeRules.applySelect(before, target, scope, includeDeactivated, depth, reach);
            return writes.commit(loaded, change, session);
        });
    }

    /**
     * Unselects one artifact, a mission type included: it leaves {@code selected}, and nothing
     * else changes. One that is not selected is a no-op. An id the closure no longer holds is refused unless the stored
     * document still selects it: then it is dropped as a stale id and reported in {@code dropped} only (ADR-0059
     * amendment A5).
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     */
    public ActivationWrites.Outcome unselect(
            ActivationId id, String kind, String artifactId, @Nullable String ifMatch) {
        return writes.exclusively(() -> {
            StoredActivation loaded = writes.loaded(id, ifMatch);
            ArtifactRef written = ActivationWrites.selectable(kind, artifactId);
            PacksSession session = writes.session(loaded);
            ArtifactRef target = ActivationWrites.declared(
                    session.snapshot(), written, ActivationWrites.selectedOf(loaded.document(), written));
            ActivationWrites.requireHeldOrStale(
                    session.snapshot(), target, ActivationWrites.selects(loaded.document(), target));
            ActivationDocument before = ActivationWrites.current(loaded, session.snapshot());
            ActivationDocument after = before.withoutSelected(target.kind(), target.id());
            return writes.commit(loaded, ActivationChange.of(before, after, SkippedReport.empty()), session);
        });
    }
}
