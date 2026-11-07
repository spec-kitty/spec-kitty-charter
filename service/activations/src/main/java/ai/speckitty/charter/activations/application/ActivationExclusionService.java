package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeRules;
import ai.speckitty.charter.activations.domain.CascadeScope;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deactivate and re-activate one artifact of an activation (FR-007, FR-015, ADR-0013 D2-D5, ADR-0059 D2): the
 * preconditions, the one read of the packs and the compare-and-set are {@link ActivationWrites}'; the cascade is
 * {@link CascadeRules}'; {@code applied} and {@code cleared} are {@link ActivationChange#of}'s.
 *
 * <p>A mission type is denied and re-activated like any kind (A7 Q2), under {@code deactivated.mission_type}.
 *
 * <p>A deactivate refuses a target on the floor first ({@code 409 artifact_locked} or {@code artifact_required},
 * naming the packs), before any walk. It then walks the target's own reach and the shared reach of the other active
 * artifacts ({@link CascadeRules#planShared}), both on the same session, so the whole request reads the packs once
 * (NFR-007).
 */
@Service
public class ActivationExclusionService {

    private final ActivationWrites writes;

    /**
     * The deactivate and the re-activate over the shared write flow.
     *
     * @param writes the shared write flow
     */
    @Autowired
    public ActivationExclusionService(ActivationWrites writes) {
        this.writes = Objects.requireNonNull(writes, "writes");
    }

    /**
     * Deactivates one artifact, with an optional cascade of unlimited depth, whose request body may be malformed: only
     * the target gets a deny entry.
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param cascade the body's {@code cascade} tokens; empty when absent
     * @param bodyFaults the faults the inbound adapter found in the body, refused once the preconditions hold
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     * @throws CharterRefusal with {@code artifact_locked} or {@code artifact_required} for a target on the floor
     */
    public ActivationWrites.Outcome deactivate(
            ActivationId id,
            String kind,
            String artifactId,
            Optional<List<String>> cascade,
            List<RefusalViolation> bodyFaults,
            @Nullable String ifMatch) {
        return deactivate(id, kind, artifactId, cascade, OptionalInt.empty(), bodyFaults, ifMatch);
    }

    /**
     * Deactivates one artifact, with an optional cascade, whose request body may be malformed: only the target gets a
     * deny entry. A {@code depth} bounds the {@code suggests} hops of the target's own walk; the shared walk of the
     * other active artifacts is unlimited (decision DM-01M6TF3BA808JMW0W0A0CPXBNW).
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param cascade the body's {@code cascade} tokens; empty when absent
     * @param depth the body's {@code depth}; empty when absent, which is unlimited (IC-21, FR-043)
     * @param bodyFaults the faults the inbound adapter found in the body, refused once the preconditions hold
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     * @throws CharterRefusal with {@code artifact_locked} or {@code artifact_required} for a target on the floor
     */
    public ActivationWrites.Outcome deactivate(
            ActivationId id,
            String kind,
            String artifactId,
            Optional<List<String>> cascade,
            OptionalInt depth,
            List<RefusalViolation> bodyFaults,
            @Nullable String ifMatch) {
        StoredActivation loaded = writes.loaded(id, ifMatch);
        ActivationWrites.requireWellFormed(bodyFaults);
        ArtifactRef written = ActivationWrites.selectable(kind, artifactId);
        CascadeScope scope = ActivationWrites.scope(cascade);
        PacksSession session = writes.session(loaded);
        ClosureSnapshot snapshot = session.snapshot();
        ArtifactRef target = ActivationWrites.declared(snapshot, written, Set.of());
        ActivationWrites.requireHeld(snapshot, target);
        ActivationDocument before = ActivationWrites.current(loaded, snapshot);
        refuseFloor(before, target, snapshot);
        Reach own = ActivationWrites.walk(session, CascadeRules.planDeactivate(before, target, scope, depth));
        Set<ArtifactRef> floor = floorOf(snapshot);
        Reach others = ActivationWrites.walk(session, CascadeRules.planShared(before, target, scope, own, floor));
        ActivationChange change = CascadeRules.applyDeactivate(before, target, scope, depth, own, others, floor);
        return writes.commit(loaded, change, snapshot);
    }

    /**
     * Re-activates one artifact: its deny entry is removed (a deny list it empties is dropped), and nothing else
     * changes. One that is not denied is a no-op. An id the closure no longer holds is refused unless the stored
     * document still denies it: then it is dropped as a stale id and reported in {@code dropped} only, not in
     * {@code cleared} (ADR-0059 amendment A5).
     *
     * @param id the activation's id
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     */
    public ActivationWrites.Outcome reactivate(
            ActivationId id, String kind, String artifactId, @Nullable String ifMatch) {
        StoredActivation loaded = writes.loaded(id, ifMatch);
        ArtifactRef written = ActivationWrites.selectable(kind, artifactId);
        PacksSession session = writes.session(loaded);
        ArtifactRef target = ActivationWrites.declared(
                session.snapshot(), written, loaded.document().deactivated(written.kind()));
        ActivationWrites.requireHeldOrStale(
                session.snapshot(), target, ActivationWrites.denies(loaded.document(), target));
        ActivationDocument before = ActivationWrites.current(loaded, session.snapshot());
        ActivationDocument after = before.withoutDeny(target.kind(), target.id());
        return writes.commit(loaded, ActivationChange.of(before, after, SkippedReport.empty()), session.snapshot());
    }

    /**
     * The {@code 409} of a target on the floor, judged as {@link DocumentRules} judges a deny entry: the document's
     * packs and the target's deny entry alone, so the refusal names exactly the packs that require or lock it.
     */
    private static void refuseFloor(ActivationDocument before, ArtifactRef target, ClosureSnapshot snapshot) {
        ActivationDocument.Builder denying = ActivationDocument.builder();
        before.packs().ifPresent(denying::packs);
        denying.deactivated(target.kind(), List.of(target.id()));
        DocumentRules.check(denying.build().document(), snapshot)
                .filter(refusal -> refusal.violations().isEmpty())
                .ifPresent(refusal -> {
                    throw refusal;
                });
    }

    /** The artifacts the governing packs require or lock. */
    private static Set<ArtifactRef> floorOf(ClosureSnapshot snapshot) {
        Set<ArtifactRef> floor = new TreeSet<>();
        Stream.concat(snapshot.requiredBy().keySet().stream(), snapshot.lockedBy().keySet().stream())
                .flatMap(urn -> Urn.parse(urn).flatMap(ArtifactRef::of).stream())
                .forEach(floor::add);
        return floor;
    }
}
