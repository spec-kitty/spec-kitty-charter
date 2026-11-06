package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeScope;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.StaleIds;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The flow every per-artifact write shares (ADR-0020 D6, FR-010), so each use case keeps only its own computation:
 *
 * <ol>
 *   <li>the preconditions, before anything else is judged and before the packs are read: no {@code If-Match} is
 *       {@code 428}, an activation that is not stored {@code 404 activation_not_found}, and an {@code If-Match} that
 *       does not name the stored revision {@code 412};
 *   <li>the body's faults the inbound adapter recorded ({@code 422 activation_invalid}, at their pointers), the path's
 *       kind ({@code 422}, a violation at {@code /kind}) and the body's cascade ({@code 422}, at {@code /cascade}), still
 *       before the packs are read;
 *   <li>one {@link PacksSession} (NFR-007), refused {@code 422} when a pack the document names is configured as none
 *       any more;
 *   <li>the path id against that closure ({@code 404 artifact_not_found}, or {@code mission_type_not_found}); an
 *       unselect or a re-activate also takes an id the closure no longer holds when the stored list it removes the id
 *       from still names it, since the drop below removes it (ADR-0059 amendment R5);
 *   <li>the stored document without the ids the closure no longer declares ({@link #current}, {@link StaleIds}): the use
 *       case computes its change from it, and the ids dropped are reported in {@code dropped} (ADR-0059 amendment R5);
 *   <li>after the use case computed its {@link ActivationChange}: a document equal to the stored one is a no-op, which
 *       answers the stored revision and writes nothing, so a write that dropped a stale id is never one; any other is
 *       judged by {@link DocumentRules#checkWrite} and written by a compare-and-set at the loaded revision
 *       ({@code 412} when another writer came in between). Every id is judged (FR-017), but the floor only on the deny
 *       entries the write adds: a stored deny entry a pack upgrade put on the floor is tolerated, so it never makes a
 *       write of another id {@code 409}. An unknown pack and a pinned version the closure does not hold are not
 *       dropped: they are still refused {@code 422}.
 * </ol>
 *
 * <p>A per-artifact write has no body list for a pointer to index, so a {@code 422} that {@link DocumentRules} raises on
 * the computed document points at the whole request ({@code ""}), keeping the codes and the details.
 */
@Component
public class ActivationWrites {

    private static final String KIND_POINTER = "/kind";
    private static final String WHOLE_REQUEST = "";

    private final ActivationRepository repository;
    private final AvailablePacks packs;

    /**
     * The shared flow over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, asked once per write
     */
    @Autowired
    public ActivationWrites(ActivationRepository repository, AvailablePacks packs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The outcome of a per-artifact write.
     *
     * @param stored the activation as stored after the write: the loaded one, unchanged, for a no-op
     * @param change what the write changed
     */
    public record Outcome(StoredActivation stored, ActivationChange change) {

        /**
         * Checks the fields.
         *
         * @param stored the stored activation
         * @param change the change
         */
        public Outcome {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(change, "change");
        }
    }

    /**
     * The stored activation a write applies to, once its preconditions hold.
     *
     * @param id the activation's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the stored activation, at the revision the header names
     * @throws PreconditionException ({@code MISSING}) without the header, or ({@code STALE}) when it names another
     *     revision
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id
     */
    StoredActivation loaded(ActivationId id, @Nullable String ifMatch) {
        if (ifMatch == null) {
            throw new PreconditionException(
                    PreconditionException.Kind.MISSING,
                    "A per-artifact write of an activation requires If-Match with its current revision.");
        }
        StoredActivation stored = repository
                .find(id)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as '" + id + "'."));
        if (!EntityTags.matches(ifMatch, stored.revision().token())) {
            throw stale(id);
        }
        return stored;
    }

    /**
     * Refuses the faults of a write's body, at the {@code 422} step: after the preconditions, before the path and the
     * packs are judged. The inbound adapter records them while it reads the body instead of refusing them there, so a
     * bad body never comes before {@code 428}, {@code 404 activation_not_found} or {@code 412} (FR-010).
     *
     * @param violations the faults the adapter found, at their pointers into the body; empty for a well-formed body
     * @throws CharterRefusal with {@code activation_invalid} when there is one
     */
    static void requireWellFormed(List<RefusalViolation> violations) {
        if (!violations.isEmpty()) {
            throw CharterRefusal.withViolations(
                    RefusalCode.ACTIVATION_INVALID,
                    "The activation request body breaks its schema in " + violations.size() + " place(s).",
                    violations);
        }
    }

    /**
     * The artifact a select or an unselect names: a kind of {@link ActivationDocument#kinds()}, or
     * {@code mission_type}.
     *
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @return the reference
     * @throws CharterRefusal with {@code activation_invalid} for any other kind
     */
    static ArtifactRef selectable(String kind, String artifactId) {
        return new ArtifactRef(kindOf(kind, true), artifactId);
    }

    /**
     * The artifact a deactivate or a re-activate names: a kind of {@link ActivationDocument#kinds()}, which has a deny
     * list ({@code mission_type} has none).
     *
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @return the reference
     * @throws CharterRefusal with {@code activation_invalid} for any other kind
     */
    static ArtifactRef deniable(String kind, String artifactId) {
        return new ArtifactRef(kindOf(kind, false), artifactId);
    }

    private static ArtifactKind kindOf(String token, boolean missionTypes) {
        return ArtifactKind.fromToken(token)
                .filter(kind -> ActivationDocument.kinds().contains(kind)
                        || (missionTypes && kind == ArtifactKind.MISSION_TYPE))
                .orElseThrow(() -> invalid(new RefusalViolation(
                        KIND_POINTER, "unknown_kind", "This write takes no '" + token + "' artifacts.")));
    }

    /**
     * The cascade a body names.
     *
     * @param written the {@code cascade} member's tokens; empty when absent
     * @return the scope
     * @throws CharterRefusal with {@code activation_invalid} when {@code all} is mixed with kinds
     */
    static CascadeScope scope(Optional<List<String>> written) {
        CascadeScope.Parsed parsed = CascadeScope.parse(written);
        if (!parsed.violations().isEmpty()) {
            throw CharterRefusal.withViolations(
                    RefusalCode.ACTIVATION_INVALID, "The cascade of the request is malformed.", parsed.violations());
        }
        return parsed.scope();
    }

    /**
     * The one read of the packs a write takes (NFR-007).
     *
     * @param stored the activation the write applies to
     * @return the session over its closure
     * @throws CharterRefusal with {@code activation_invalid} when a pack the document names is configured as none, or
     *     with the refusal of the compose
     */
    PacksSession session(StoredActivation stored) {
        PacksSession session = packs.session(stored.document()
                .packs()
                .map(list -> list.stream().map(PackRef::packId).toList()));
        ClosureSnapshot snapshot = session.snapshot();
        if (!snapshot.resolved()) {
            throw CharterRefusal.withViolations(
                    RefusalCode.ACTIVATION_INVALID,
                    "The activation builds on " + String.join(", ", snapshot.unknownPacks())
                            + ", which no pack is configured as any more.",
                    snapshot.unknownPacks().stream()
                            .sorted()
                            .map(pack -> new RefusalViolation(
                                    WHOLE_REQUEST, "unknown_pack", "No pack is configured as '" + pack + "'."))
                            .toList());
        }
        return session;
    }

    /**
     * Refuses a path id the closure does not hold (FR-010).
     *
     * @param snapshot the closure
     * @param target the path's artifact
     * @throws CharterRefusal with {@code artifact_not_found}, or {@code mission_type_not_found} for a mission type
     */
    static void requireHeld(ClosureSnapshot snapshot, ArtifactRef target) {
        requireHeldOrStale(snapshot, target, false);
    }

    /**
     * Refuses a path id the closure does not hold, unless the stored list the write removes it from still names it:
     * then it is a stale id, which {@link #current} drops and {@link #commit} reports in {@code dropped} (ADR-0059
     * amendment R5). An id named in neither place is refused as {@link #requireHeld} refuses it (FR-010).
     *
     * @param snapshot the closure
     * @param target the path's artifact
     * @param stored whether the stored list the write removes the id from names it
     * @throws CharterRefusal with {@code artifact_not_found}, or {@code mission_type_not_found} for a mission type
     */
    static void requireHeldOrStale(ClosureSnapshot snapshot, ArtifactRef target, boolean stored) {
        boolean missionType = target.kind() == ArtifactKind.MISSION_TYPE;
        boolean held = missionType
                ? snapshot.missionTypes().contains(target.id())
                : snapshot.held(target.kind()).contains(target.id());
        if (!held && !stored) {
            throw new CharterRefusal(
                    missionType ? RefusalCode.MISSION_TYPE_NOT_FOUND : RefusalCode.ARTIFACT_NOT_FOUND,
                    "The activation's packs hold no " + target.kind().token() + " '" + target.id() + "'.");
        }
    }

    /**
     * Whether a document selects an artifact: in {@code selected}, or in {@code missionTypes} for a mission type.
     *
     * @param document the document
     * @param target the artifact
     * @return whether it is selected
     */
    static boolean selects(ActivationDocument document, ArtifactRef target) {
        Optional<SortedSet<String>> ids =
                target.kind() == ArtifactKind.MISSION_TYPE ? document.missionTypes() : document.selected(target.kind());
        return ids.map(set -> set.contains(target.id())).orElse(false);
    }

    /**
     * Whether a document denies an artifact.
     *
     * @param document the document
     * @param target the artifact
     * @return whether its deny list names it
     */
    static boolean denies(ActivationDocument document, ArtifactRef target) {
        return document.deactivated(target.kind()).contains(target.id());
    }

    /**
     * The document a write computes its change from: the stored one without the ids its closure no longer declares
     * (ADR-0059 amendment R5). {@link #commit} reports those ids in {@code dropped}.
     *
     * @param loaded the activation as loaded
     * @param snapshot the closure the document names, resolved
     * @return the stored document less its stale ids
     */
    static ActivationDocument current(StoredActivation loaded, ClosureSnapshot snapshot) {
        return StaleIds.drop(loaded.document(), snapshot).document();
    }

    /**
     * Walks a plan, or skips the call when it asks for no walk.
     *
     * @param session the request's session
     * @param plan the plan
     * @return the walk's answer; {@link Reach#empty()} for a plan that walks nothing
     */
    static Reach walk(PacksSession session, ReachPlan plan) {
        return plan.walks() ? session.reach(plan) : Reach.empty();
    }

    /**
     * Stores what a write computed from {@link #current}: nothing for a no-op, otherwise the computed document after the
     * rules, by a compare-and-set at the loaded revision. The change reports the stale ids dropped from the stored
     * document.
     *
     * @param loaded the activation as loaded
     * @param computed what the write computed from {@link #current}
     * @param snapshot the closure the rules judge the document against
     * @return the outcome
     * @throws CharterRefusal when the computed document breaks its closure
     * @throws PreconditionException ({@code STALE}) when another writer came in between
     */
    Outcome commit(StoredActivation loaded, ActivationChange computed, ClosureSnapshot snapshot) {
        ActivationChange change =
                computed.withDropped(StaleIds.drop(loaded.document(), snapshot).dropped());
        ActivationDocument after = change.after();
        if (after.equals(loaded.document())) {
            return new Outcome(loaded, change);
        }
        DocumentRules.checkWrite(loaded.document(), after, snapshot)
                .map(ActivationWrites::wholeRequest)
                .ifPresent(refusal -> {
                    throw refusal;
                });
        CasResult result = repository.replace(loaded.id(), Optional.of(loaded.revision()), after);
        return switch (result) {
            case CasResult.Written(StoredActivation stored, boolean _) -> new Outcome(stored, change);
            case CasResult.Stale _, CasResult.Missing _ -> throw stale(loaded.id());
        };
    }

    /** A refusal of the computed document, its violations pointing at the whole request. */
    private static CharterRefusal wholeRequest(CharterRefusal refusal) {
        if (refusal.violations().isEmpty()) {
            return refusal;
        }
        List<RefusalViolation> violations = refusal.violations().stream()
                .map(violation -> new RefusalViolation(WHOLE_REQUEST, violation.code(), violation.detail()))
                .distinct()
                .toList();
        return CharterRefusal.withViolations(
                refusal.code(),
                Objects.requireNonNullElse(refusal.getMessage(), "The activation breaks its closure."),
                violations);
    }

    private static CharterRefusal invalid(RefusalViolation violation) {
        return CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The request names a kind this write does not take.",
                List.of(violation));
    }

    private static PreconditionException stale(ActivationId id) {
        return new PreconditionException(
                PreconditionException.Kind.STALE,
                "The activation '" + id + "' is not at the revision the request names; read it again.");
    }
}
