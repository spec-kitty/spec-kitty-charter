package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ArtifactSpellings;
import ai.speckitty.charter.activations.domain.CascadeScope;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.StaleIds;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The flow every per-artifact write shares (ADR-0020 D6, FR-010), so each use case keeps only its own computation:
 *
 * <ol>
 *   <li>the preconditions, before anything else is judged and before the packs are read: no {@code If-Match} is
 *       {@code 428}, an activation that is not stored {@code 404 activation_not_found}, and an {@code If-Match} that
 *       names neither the stored revision nor the current {@code getActivation} tag ({@link IfMatchTags}) {@code 412};
 *   <li>the body's faults the inbound adapter recorded ({@code 422 activation_invalid}, at their pointers), the path's
 *       kind ({@code 422}, a violation at {@code /kind}) and the body's cascade ({@code 422}, at {@code /cascade}), still
 *       before the packs are read;
 *   <li>one {@link PacksSession} (NFR-007), refused {@code 422} when a pack the document names is configured as none
 *       any more;
 *   <li>the path id against that closure, read in any known spelling ({@link #declared}: the declared id, its full
 *       URN, or another case, as {@link ArtifactSpellings} reads an id of the activation document; {@code 422} when it
 *       matches two declared ids that differ only by case; a mission type as any kind, A7), then held or not
 *       ({@code 404 artifact_not_found}, or {@code mission_type_not_found}, the contract's code for a mission type);
 *       an unselect or a re-activate also takes an id the closure no longer holds
 *       when the stored list it removes the id from still names it, since the drop below removes it (ADR-0059
 *       amendment A5);
 *   <li>the stored document without the ids the closure no longer declares and the version pins no pack of it holds
 *       ({@link #current}, {@link StaleIds}): the use case computes its change from it, and the ids and pins dropped
 *       are reported in {@code dropped} and {@code droppedPins} (ADR-0059 amendment A5; B1, decision
 *       DM-01M6RN5Y406SB4KA13QYMQ2P8J);
 *   <li>after the use case computed its {@link ActivationChange}: a document equal to the stored one is a no-op, which
 *       answers the stored revision and writes nothing, unless the write dropped a stale id or a pin: one that did is
 *       never a no-op, even when the change put the id back (a stale preset member the stored list names); any other is
 *       judged by {@link DocumentRules#checkWrite}, then by the slot check of its closure
 *       ({@link PacksSession#requireComposable}, C38: {@code 422 prompt_slot_missing}), and written by a compare-and-set
 *       at the loaded revision
 *       ({@code 412} when another writer came in between). Every id is judged (FR-017), but the floor only on the deny
 *       entries the write adds: a stored deny entry a pack upgrade put on the floor is tolerated, so it never makes a
 *       write of another id {@code 409}. An unknown pack is not dropped: it is still refused {@code 422}.
 * </ol>
 *
 * <p>A per-artifact write has no body list for a pointer to index, so a {@code 422} that {@link DocumentRules} raises on
 * the computed document points at the whole request ({@code ""}), keeping the codes and the details.
 */
@Component
public class ActivationWrites {

    private static final String KIND_POINTER = "/kind";
    private static final String ARTIFACT_ID_POINTER = "/artifactId";
    private static final String WHOLE_REQUEST = "";

    private final ActivationRepository repository;
    private final AvailablePacks packs;
    private final WriteCoordination coordination;

    /**
     * The shared flow over the register and the packs, with no coordination with pack registrations.
     *
     * @param repository the activation register
     * @param packs the packs, asked once per write
     */
    public ActivationWrites(ActivationRepository repository, AvailablePacks packs) {
        this(repository, packs, WriteCoordination.NONE);
    }

    /**
     * The shared flow over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, asked once per write
     * @param coordination what makes a write exclude a pack registration
     */
    @Autowired
    public ActivationWrites(ActivationRepository repository, AvailablePacks packs, WriteCoordination coordination) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.coordination = Objects.requireNonNull(coordination, "coordination");
    }

    /**
     * Runs a whole write, from its first read to its store, excluding every pack registration, so that the activation is
     * judged against the packs it is stored with and a registration's impact check sees it.
     *
     * @param work the write
     * @param <T> what it answers
     * @return what the work answers
     */
    <T> T exclusively(java.util.function.Supplier<T> work) {
        return coordination.exclusively(work);
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
        if (!IfMatchTags.names(ifMatch, stored, packs)) {
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
     * The artifact a per-artifact write names, a select, an unselect, a deactivate or a re-activate: any activatable
     * kind, {@code mission_type} as any other (A7: one kind model; Q2: a required or locked one is refused on the
     * floor, as any kind is).
     *
     * @param kind the path's kind token
     * @param artifactId the path's id
     * @return the reference
     * @throws CharterRefusal with {@code activation_invalid} for any other kind
     */
    static ArtifactRef selectable(String kind, String artifactId) {
        return activatable(kind, artifactId);
    }

    private static ArtifactRef activatable(String token, String artifactId) {
        ArtifactKind kind = ArtifactKind.fromToken(token)
                .filter(ArtifactKind::activatable)
                .orElseThrow(() -> invalid(new RefusalViolation(
                        KIND_POINTER, "unknown_kind", "This write takes no '" + token + "' artifacts.")));
        return new ArtifactRef(kind, artifactId);
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
     * The path's artifact with its id as declared (ADR-0020 D4, operator ruling of 2026-11-06): a known spelling of an
     * id the closure holds (the declared id, the full URN of the path's kind, or another case of either) is that
     * declared id, as {@link DocumentRules#normalize} reads an id of the document. A spelling the closure holds no id
     * for is read against the stored list the write removes the id from, so a stale id (ADR-0059 amendment A5) is
     * matched the same way. A spelling neither names is kept as written, for {@link #requireHeldOrStale} to refuse.
     * A mission type is read as any kind is (A7).
     *
     * @param snapshot the closure
     * @param target the path's artifact, as written
     * @param stored the ids of the stored list the write removes the id from; empty for a select or a deactivate
     * @return the artifact with its declared id, or as written when nothing declares it
     * @throws CharterRefusal with {@code activation_invalid}, a violation {@code ambiguous_artifact} at
     *     {@code /artifactId}, when the spelling matches two declared ids that differ only by case
     */
    static ArtifactRef declared(ClosureSnapshot snapshot, ArtifactRef target, Set<String> stored) {
        ArtifactSpellings.Resolution resolution =
                ArtifactSpellings.resolve(target.kind(), target.id(), snapshot.held(target.kind()));
        if (resolution instanceof ArtifactSpellings.Resolution.Unknown) {
            resolution = ArtifactSpellings.resolve(target.kind(), target.id(), stored);
        }
        return switch (resolution) {
            case ArtifactSpellings.Resolution.Declared found -> new ArtifactRef(target.kind(), found.id());
            case ArtifactSpellings.Resolution.Unknown _ -> target;
            case ArtifactSpellings.Resolution.Ambiguous ambiguous ->
                throw CharterRefusal.withViolations(
                        RefusalCode.ACTIVATION_INVALID,
                        "The request's artifact id matches several declared ids.",
                        List.of(new RefusalViolation(
                                ARTIFACT_ID_POINTER,
                                "ambiguous_artifact",
                                "'" + target.id() + "' matches " + String.join(", ", ambiguous.candidates())
                                        + ", which differ only by case; write the declared id.")));
        };
    }

    /**
     * The ids of the stored list an unselect removes an id from: {@code selected} of its kind.
     *
     * @param document the stored document
     * @param target the artifact
     * @return the ids; empty when the list is absent
     */
    static Set<String> selectedOf(ActivationDocument document, ArtifactRef target) {
        return document.selected(target.kind()).<Set<String>>map(set -> set).orElse(Set.of());
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
     * amendment A5). An id named in neither place is refused as {@link #requireHeld} refuses it (FR-010).
     *
     * @param snapshot the closure
     * @param target the path's artifact
     * @param stored whether the stored list the write removes the id from names it
     * @throws CharterRefusal with {@code artifact_not_found}, or {@code mission_type_not_found} for a mission type
     */
    static void requireHeldOrStale(ClosureSnapshot snapshot, ArtifactRef target, boolean stored) {
        boolean missionType = target.kind() == ArtifactKind.MISSION_TYPE;
        if (!snapshot.held(target.kind()).contains(target.id()) && !stored) {
            throw new CharterRefusal(
                    missionType ? RefusalCode.MISSION_TYPE_NOT_FOUND : RefusalCode.ARTIFACT_NOT_FOUND,
                    "The activation's packs hold no " + target.kind().token() + " '" + target.id() + "'.");
        }
    }

    /**
     * Whether a document selects an artifact: in {@code selected} of its kind.
     *
     * @param document the document
     * @param target the artifact
     * @return whether it is selected
     */
    static boolean selects(ActivationDocument document, ArtifactRef target) {
        return selectedOf(document, target).contains(target.id());
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
     * (ADR-0059 amendment A5) and the version pins no pack of it holds (B1). {@link #commit} reports them in
     * {@code dropped} and {@code droppedPins}.
     *
     * @param loaded the activation as loaded
     * @param snapshot the closure the document names, resolved
     * @return the stored document less its stale ids and unheld pins
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
     * Stores what a write computed from {@link #current}: nothing for a no-op (a computed document equal to the stored
     * one, with no stale id or pin dropped), otherwise the computed document after the rules, by a compare-and-set at
     * the loaded revision. The change reports the stale ids and the pins dropped from the stored document.
     *
     * @param loaded the activation as loaded
     * @param computed what the write computed from {@link #current}
     * @param session the request's session: its closure is what the rules judge the document against, and its in-force
     *     resolution gives the warnings of the change
     * @return the outcome
     * @throws CharterRefusal when the computed document breaks its closure, or with {@code prompt_slot_missing} when
     *     it would put a step enhancement in force without its slot ({@link PacksSession#requireComposable}, asked of
     *     the document before it is stored)
     * @throws PreconditionException ({@code STALE}) when another writer came in between
     */
    Outcome commit(StoredActivation loaded, ActivationChange computed, PacksSession session) {
        ClosureSnapshot snapshot = session.snapshot();
        ActivationChange change = computed.withDropped(StaleIds.drop(loaded.document(), snapshot));
        ActivationDocument after = change.after();
        if (after.equals(loaded.document())
                && change.dropped().isEmpty()
                && change.droppedPins().isEmpty()) {
            return new Outcome(loaded, change);
        }
        DocumentRules.checkWrite(loaded.document(), after, snapshot, session::inForce)
                .map(ActivationWrites::wholeRequest)
                .ifPresent(refusal -> {
                    throw refusal;
                });
        session.requireComposable(EffectiveSet.queryOf(after));
        ActivationChange reported = warned(change, loaded.document(), session);
        CasResult result = repository.replace(loaded.id(), Optional.of(loaded.revision()), after);
        return switch (result) {
            case CasResult.Written(StoredActivation stored, boolean _) -> new Outcome(stored, reported);
            case CasResult.Stale _, CasResult.Missing _ -> throw stale(loaded.id());
        };
    }

    /**
     * The change with its warnings (IC-17, FR-032): what the write added to {@code selected} that a read of the stored
     * document will not put in force. The resolution is asked once, of the session the write holds, before anything is
     * stored, so a closure whose resolution refuses ({@code pack_unreadable}) leaves the write refused and the document
     * as it was. A write that applied nothing asks nothing.
     */
    private static ActivationChange warned(ActivationChange change, ActivationDocument before, PacksSession session) {
        return change.applied().isEmpty()
                ? change
                : change.withWarnings(session.inForce(EffectiveSet.queryOf(change.after())), before);
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
