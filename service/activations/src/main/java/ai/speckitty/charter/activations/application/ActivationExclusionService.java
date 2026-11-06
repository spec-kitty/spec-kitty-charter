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
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * <p>A deactivate refuses a target on the floor first ({@code 409 artifact_locked} or {@code artifact_required},
 * naming the packs), before any walk. It then walks the target's own reach, and drives {@link CascadeRules#planShared}
 * to its fixed point from {@link Reach#empty()}: every step walks the same session, so the whole request reads the
 * packs once (NFR-007). The last answer is what the other active artifacts keep.
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
     * Deactivates one artifact, with an optional cascade, whose request body may be malformed: only the target gets a
     * deny entry.
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
        StoredActivation loaded = writes.loaded(id, ifMatch);
        ActivationWrites.requireWellFormed(bodyFaults);
        ArtifactRef target = ActivationWrites.deniable(kind, artifactId);
        CascadeScope scope = ActivationWrites.scope(cascade);
        PacksSession session = writes.session(loaded);
        ClosureSnapshot snapshot = session.snapshot();
        ActivationWrites.requireHeld(snapshot, target);
        ActivationDocument before = loaded.document();
        refuseFloor(before, target, snapshot);
        Reach own = ActivationWrites.walk(session, CascadeRules.planDeactivate(before, target, scope));
        Map<ArtifactRef, Set<ArtifactRef>> enhancers = own.reached().isEmpty()
                ? Map.of()
                : session.enhancersOf(own.reached().keySet());
        Set<ArtifactRef> floor = floorOf(snapshot);
        Shared shared = new Shared(session, before, target, scope, own, enhancers, floor);
        ActivationChange change =
                CascadeRules.applyDeactivate(before, target, scope, own, shared.fixedPoint(), enhancers, floor);
        return writes.commit(loaded, change, snapshot);
    }

    /**
     * Re-activates one artifact: its deny entry is removed (a deny list it empties is dropped), and nothing else
     * changes. One that is not denied is a no-op.
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
        ArtifactRef target = ActivationWrites.deniable(kind, artifactId);
        PacksSession session = writes.session(loaded);
        ActivationWrites.requireHeld(session.snapshot(), target);
        ActivationDocument before = loaded.document();
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

    /**
     * The fixed point of the shared walk ({@link CascadeRules#planShared}): from {@link Reach#empty()}, each step walks
     * the plan the previous answer gives, until the plan is the one before. The starts only grow, so it ends within one
     * step per artifact the target reached; the bound guards that promise.
     */
    private record Shared(
            PacksSession session,
            ActivationDocument before,
            ArtifactRef target,
            CascadeScope scope,
            Reach own,
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Set<ArtifactRef> floor) {

        Reach fixedPoint() {
            ReachPlan plan = plan(Reach.empty());
            for (int step = 0; step <= own.reached().size(); step++) {
                Reach answer = ActivationWrites.walk(session, plan);
                ReachPlan next = plan(answer);
                if (next.equals(plan)) {
                    return answer;
                }
                plan = next;
            }
            throw new IllegalStateException("The shared walk of " + target.urn() + " found no fixed point.");
        }

        private ReachPlan plan(Reach previous) {
            return CascadeRules.planShared(before, target, scope, own, enhancers, floor, previous);
        }
    }
}
