package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveReadService.After;
import ai.speckitty.charter.activations.application.EffectiveReadService.ContextRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.GraphRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.application.EffectiveReadService.StaleCursorException;
import ai.speckitty.charter.activations.application.EffectiveReadService.Viewed;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveContext;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyReason;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The effective reads over an in-memory register and a counting fake of the packs (IC-09, E19, NFR-007): every request
 * opens exactly one session, asks the merge and the in-force resolution of it once each, and a {@code 304} asks neither.
 * The effective list is ordered by kind and then by id, paged by a keyset cursor that binds the tag of its set, and its
 * {@code summary} counts the whole set. The tags are the {@link EffectiveTag}s of the representation read.
 */
class EffectiveReadServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.DIRECTIVE, "A");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.DIRECTIVE, "B");
    private static final ArtifactRef C = new ArtifactRef(ArtifactKind.DIRECTIVE, "C");
    private static final ArtifactRef T1 = new ArtifactRef(ArtifactKind.TACTIC, "t1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure(CLOSURE));
        packs.merged(merged());
        packs.resolves(query -> answer());
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main")))
                                .selected(ArtifactKind.DIRECTIVE, List.of("A"))
                                .selected(ArtifactKind.TACTIC, List.of("t1"))
                                .deactivated(ArtifactKind.DIRECTIVE, List.of("C"))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "A", "B", "C")
                .held(ArtifactKind.TACTIC, "t1")
                .fingerprint(SOURCE)
                .closureFingerprint(closureFingerprint)
                .build();
    }

    private static MergedSnapshot merged() {
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        for (ArtifactRef ref : List.of(T1, C, B, A)) {
            winners.put(ref, new MergedSnapshot.Winner("main", PackLevel.ORG, Policy.OPEN));
        }
        MergedSnapshot.Edge suggests =
                new MergedSnapshot.Edge(A, B, RelationType.SUGGESTS, Optional.of("when"), Optional.of("why"));
        return new MergedSnapshot(winners, Map.of(), List.of(suggests), List.of());
    }

    private static ArtifactState state(ArtifactRef ref, State state, Reason reason, Optional<Bucket> bucket) {
        return new ArtifactState(ref, state, reason, false, List.of(), List.of(), bucket);
    }

    private static InForceAnswer answer() {
        Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        states.put(T1, state(T1, State.ACTIVE, Reason.SELECTED, Optional.empty()));
        states.put(C, state(C, State.EXCLUDED, Reason.DEACTIVATED, Optional.of(Bucket.DEACTIVATED)));
        states.put(B, state(B, State.INACTIVE, Reason.NOT_SELECTED, Optional.empty()));
        states.put(A, state(A, State.ACTIVE, Reason.SELECTED, Optional.empty()));
        return new InForceAnswer(
                states,
                List.of(),
                List.of(new DenyNotApplied(B, DenyReason.LOCKED), new DenyNotApplied(A, DenyReason.REQUIRED)),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
    }

    private static PageQuery everything() {
        return new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty());
    }

    private Page page(PageQuery query) {
        return found(service.list(ID, query, null)).value();
    }

    /** The found answer, asserted to be one, so a read that is not found fails on an assertion. */
    private static <T> Read.Found<T> found(Read<T> read) {
        assertThat(read).isInstanceOf(Read.Found.class);
        return (Read.Found<T>) read;
    }

    private static EffectiveTag.Query tagQuery(PageQuery query) {
        return new EffectiveTag.Query(query.state(), query.kind(), query.after().map(After::cursor), query.pageSize());
    }

    private String tagOf(PageQuery query) {
        return EffectiveTag.artifacts(stored.revision(), closure(CLOSURE), tagQuery(query))
                .token();
    }

    // ---- listEffectiveArtifacts

    @Test
    void theListHoldsEveryArtifactOfTheMergedClosureOrderedByKindThenId() {
        Page page = page(everything());

        assertThat(page.items()).extracting(EffectiveArtifact::ref).containsExactly(A, B, C, T1);
        assertThat(page.items())
                .extracting(EffectiveArtifact::state)
                .containsExactly(State.ACTIVE, State.INACTIVE, State.EXCLUDED, State.ACTIVE);
        assertThat(page.next()).isEmpty();
        assertThat(page.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(page.governanceFingerprint()).as("the closure's own digest").isEqualTo(CLOSURE);
        assertThat(page.stored()).isEqualTo(stored);
    }

    @Test
    void theKindAndTheStateFilterTheListAndNotTheSummary() {
        Page directives =
                page(new PageQuery(Optional.of(ArtifactKind.DIRECTIVE), Optional.empty(), 50, Optional.empty()));
        Page active = page(new PageQuery(Optional.empty(), Optional.of(State.ACTIVE), 50, Optional.empty()));
        Page both = page(
                new PageQuery(Optional.of(ArtifactKind.TACTIC), Optional.of(State.INACTIVE), 50, Optional.empty()));

        assertThat(directives.items()).extracting(EffectiveArtifact::ref).containsExactly(A, B, C);
        assertThat(active.items()).extracting(EffectiveArtifact::ref).containsExactly(A, T1);
        assertThat(both.items()).isEmpty();
        assertThat(both.summary().active())
                .as("the summary counts the whole set")
                .isEqualTo(2);
        assertThat(directives.summary()).isEqualTo(active.summary());
    }

    @Test
    void aKeysetCursorPagesTheSetAndTheSummaryIsTheSameOnEveryPage() {
        Page first = page(new PageQuery(Optional.empty(), Optional.empty(), 3, Optional.empty()));

        assertThat(first.items()).extracting(EffectiveArtifact::ref).containsExactly(A, B, C);
        assertThat(first.next()).isPresent();
        assertThat(first.next().orElseThrow().position()).isEqualTo(C);
        After after = new After(C, first.next().orElseThrow().setTag(), "cursor-1");

        Page second = page(new PageQuery(Optional.empty(), Optional.empty(), 3, Optional.of(after)));

        assertThat(second.items()).extracting(EffectiveArtifact::ref).containsExactly(T1);
        assertThat(second.next()).isEmpty();
        assertThat(second.summary()).isEqualTo(first.summary());
        assertThat(second.summary().active()).isEqualTo(2);
        assertThat(second.summary().excluded()).isEqualTo(1);
    }

    @Test
    void aPageHoldsAtMostItsPageSizeAndStopsStrictlyAfterTheCursorsPosition() {
        Page first = page(new PageQuery(Optional.empty(), Optional.empty(), 1, Optional.empty()));
        After after = new After(A, first.next().orElseThrow().setTag(), "c");

        Page second = page(new PageQuery(Optional.empty(), Optional.empty(), 2, Optional.of(after)));

        assertThat(first.items()).extracting(EffectiveArtifact::ref).containsExactly(A);
        assertThat(second.items()).extracting(EffectiveArtifact::ref).containsExactly(B, C);
        assertThat(second.next().orElseThrow().position()).isEqualTo(C);
    }

    @Test
    void theCursorBindsTheTagOfItsSetAndAnotherSetRefusesIt() {
        Page first = page(new PageQuery(Optional.empty(), Optional.of(State.ACTIVE), 1, Optional.empty()));
        String setTag = first.next().orElseThrow().setTag();

        PageQuery sameSet =
                new PageQuery(Optional.empty(), Optional.of(State.ACTIVE), 1, Optional.of(new After(A, setTag, "c")));
        PageQuery anotherFilter =
                new PageQuery(Optional.empty(), Optional.of(State.INACTIVE), 1, Optional.of(new After(A, setTag, "c")));
        PageQuery anotherKind = new PageQuery(
                Optional.of(ArtifactKind.TACTIC), Optional.of(State.ACTIVE), 1, Optional.of(new After(A, setTag, "c")));
        PageQuery foreign = new PageQuery(
                Optional.empty(),
                Optional.of(State.ACTIVE),
                1,
                Optional.of(new After(A, "sha256:" + "0".repeat(64), "c")));

        assertThat(page(sameSet).items()).extracting(EffectiveArtifact::ref).containsExactly(T1);
        for (PageQuery refused : List.of(anotherFilter, anotherKind, foreign)) {
            assertThatThrownBy(() -> service.list(ID, refused, null)).isInstanceOf(StaleCursorException.class);
        }
    }

    @Test
    void aCursorOfTheSetBeforeAPackOfTheClosureChangedIsRefused() {
        Page first = page(new PageQuery(Optional.empty(), Optional.empty(), 1, Optional.empty()));
        After after = new After(A, first.next().orElseThrow().setTag(), "c");
        packs.answer(closure("sha256:changed"));

        assertThatThrownBy(() -> service.list(
                        ID, new PageQuery(Optional.empty(), Optional.empty(), 1, Optional.of(after)), null))
                .isInstanceOf(StaleCursorException.class);
    }

    @Test
    void aCursorOfTheSetBeforeTheDocumentChangedIsRefused() {
        Page first = page(new PageQuery(Optional.empty(), Optional.empty(), 1, Optional.empty()));
        After after = new After(A, first.next().orElseThrow().setTag(), "c");
        repository.replace(
                ID,
                Optional.of(stored.revision()),
                ActivationDocument.builder()
                        .packs(List.of(PackRef.of("main")))
                        .build()
                        .document());

        assertThatThrownBy(() -> service.list(
                        ID, new PageQuery(Optional.empty(), Optional.empty(), 1, Optional.of(after)), null))
                .isInstanceOf(StaleCursorException.class);
    }

    @Test
    void theETagIsTheTagOfTheRepresentationReadAndNoOtherRepresentationHasIt() {
        PageQuery plain = everything();
        PageQuery active = new PageQuery(Optional.empty(), Optional.of(State.ACTIVE), 50, Optional.empty());
        PageQuery smaller = new PageQuery(Optional.empty(), Optional.empty(), 10, Optional.empty());

        Read.Found<Page> found = found(service.list(ID, plain, null));

        assertThat(found.etag()).isEqualTo(tagOf(plain));
        assertThat(tagOf(active)).isNotEqualTo(found.etag());
        assertThat(tagOf(smaller)).isNotEqualTo(found.etag());
        assertThat(found(service.list(ID, active, null)).etag()).isEqualTo(tagOf(active));
        assertThat(service.list(ID, active, EntityTags.quote(found.etag())))
                .as("the tag of another representation never matches")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void aMatchingIfNoneMatchIs304AndAsksNeitherTheMergeNorTheResolution() {
        String etag = tagOf(everything());

        Read<Page> read = service.list(ID, everything(), EntityTags.quote(etag));

        assertThat(read).isEqualTo(new Read.NotModified<Page>(etag));
        assertThat(packs.calls())
                .as("the one verified read that computes the tag")
                .isEqualTo(1);
        assertThat(packs.merges()).isZero();
        assertThat(packs.queries()).isEmpty();
    }

    @Test
    void aReadOpensOneSessionAndAsksTheMergeAndTheResolutionOnceWithTheDocumentsQuery() {
        page(everything());

        assertThat(packs.calls()).as("E19: one verified read").isEqualTo(1);
        assertThat(packs.lastRoots()).contains(List.of("main"));
        assertThat(packs.merges()).isEqualTo(1);
        assertThat(packs.queries()).hasSize(1);
        assertThat(packs.queries().getFirst().selected()).containsExactlyInAnyOrder(A, T1);
        assertThat(packs.queries().getFirst().deactivated()).containsExactly(C);
    }

    @Test
    void anUnknownActivationIs404AndReadsNothing() {
        assertThatThrownBy(() -> service.list(new ActivationId("nope"), everything(), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.skipped(new ActivationId("nope"), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.activation(new ActivationId("nope"), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aClosureThatNamesAPackNoLongerConfiguredIs500PackUnreadable() {
        packs.answer(ClosureSnapshot.unresolved(Set.of("main")));

        for (Runnable read : List.<Runnable>of(
                () -> service.list(ID, everything(), null),
                () -> service.skipped(ID, null),
                () -> service.activation(ID, null))) {
            assertThatThrownBy(read::run)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        }
    }

    @Test
    void aRefusalOfTheComposeOrTheMergeReachesTheCallerUnchangedAndTheAnswerIsNeverPartial() {
        CharterRefusal compose = new CharterRefusal(RefusalCode.PACK_SIBLING_CONFLICT, "two packs contest A");
        packs.refuse(compose);
        assertThatThrownBy(() -> service.list(ID, everything(), null)).isSameAs(compose);
        assertThatThrownBy(() -> service.skipped(ID, null)).isSameAs(compose);
        assertThatThrownBy(() -> service.activation(ID, null)).isSameAs(compose);

        packs.answer(closure(CLOSURE));
        CharterRefusal merge = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a layer override breaks the level");
        packs.resolves(query -> {
            throw merge;
        });
        assertThatThrownBy(() -> service.list(ID, everything(), null)).isSameAs(merge);
        assertThatThrownBy(() -> service.skipped(ID, null)).isSameAs(merge);
    }

    // ---- getActivationSkippedReport

    @Test
    void theSkippedReportHoldsTheTypedBucketsTheRevisionAndTheTwoFingerprints() {
        Read.Found<Report> found = found(service.skipped(ID, null));
        assertThat(found.etag())
                .isEqualTo(EffectiveTag.skipped(stored.revision(), closure(CLOSURE))
                        .token());
        assertThat(found.value().stored()).isEqualTo(stored);
        assertThat(found.value().sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(found.value().governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(found.value().skipped().skippedDeactivated()).hasSize(1);
        assertThat(found.value().skipped().denyNotApplied()).hasSize(2);
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.merges()).isEqualTo(1);
        assertThat(packs.queries()).hasSize(1);
    }

    @Test
    void theSkippedReportsTagIsNotTheListsAndAMatchingIfNoneMatchIs304WithoutTheMerge() {
        String skippedTag =
                EffectiveTag.skipped(stored.revision(), closure(CLOSURE)).token();

        assertThat(skippedTag).isNotEqualTo(tagOf(everything()));
        assertThat(service.skipped(ID, EntityTags.quote(skippedTag)))
                .isEqualTo(new Read.NotModified<Report>(skippedTag));
        assertThat(service.skipped(ID, EntityTags.quote(tagOf(everything()))))
                .as("the list's tag is no tag of the report")
                .isInstanceOf(Read.Found.class);
        assertThat(packs.merges()).as("only the second read merged").isEqualTo(1);
    }

    // ---- getActivation

    @Test
    void getActivationAnswersTheClosureTagAndTheDenyEntriesTheFloorOrALockOverrides() {
        Read.Found<Viewed> found = found(service.activation(ID, null));
        assertThat(found.etag())
                .isEqualTo(EffectiveTag.activation(stored.revision(), closure(CLOSURE))
                        .token());
        assertThat(found.etag()).isNotEqualTo(stored.revision().token());
        assertThat(found.value().stored()).isEqualTo(stored);
        assertThat(found.value().denyNotApplied()).containsExactly(A, B);
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.queries()).hasSize(1);
    }

    @Test
    void getActivationOfADocumentWithNothingNotAppliedHasNoDenyEntries() {
        packs.resolves(query -> InForceAnswer.empty());

        Read.Found<Viewed> found = found(service.activation(ID, null));

        assertThat(found.value().denyNotApplied()).isEmpty();
    }

    @Test
    void getActivationTakesTheClosureTagAsIfNoneMatchAndNotTheRevision() {
        String tag =
                EffectiveTag.activation(stored.revision(), closure(CLOSURE)).token();

        assertThat(service.activation(ID, EntityTags.quote(tag))).isEqualTo(new Read.NotModified<Viewed>(tag));
        assertThat(packs.queries()).as("a 304 resolves nothing").isEmpty();
        assertThat(service.activation(ID, EntityTags.quote(stored.revision().token())))
                .as("the revision is no tag of this read")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void getActivationsTagChangesWithAPackOfTheClosure() {
        String before = found(service.activation(ID, null)).etag();
        packs.answer(closure("sha256:changed"));

        assertThat(service.activation(ID, EntityTags.quote(before))).isInstanceOf(Read.Found.class);
    }

    // ---- getEffectiveGraph and getEffectiveActionContext (IC-11)

    private static final EffectiveTag.ContextQuery IMPLEMENT =
            new EffectiveTag.ContextQuery("software-dev", "implement", 1);
    private static final ArtifactRef STEP_ENHANCER = new ArtifactRef(ArtifactKind.STEP_ENHANCEMENT, "x");

    private static GraphSnapshot graph() {
        return new GraphSnapshot(
                List.of(NodeRef.of(A), NodeRef.of(B)),
                List.of(new GraphSnapshot.Edge(NodeRef.of(A), RelationType.REQUIRES, NodeRef.of(B))),
                List.of());
    }

    private static ActionContext walked() {
        return new ActionContext(
                true,
                List.of(new ActionContext.Reached(A, List.of(T1), ActionContext.AppliesThrough.ACTION)),
                List.of(new ActionContext.SuggestionCandidate(
                        B, Optional.of("when"), Optional.of("why"), ActionContext.AppliesThrough.ACTION)),
                List.of(STEP_ENHANCER));
    }

    private ContextRead context(EffectiveTag.ContextQuery query) {
        return found(service.actionContext(ID, query, null)).value();
    }

    @Test
    void theGraphIsTheSessionsGraphOfTheDocumentsRequestUnderTheTagOfTheGraph() {
        List<InForceQuery> asked = new ArrayList<>();
        packs.graphs(query -> {
            asked.add(query);
            return graph();
        });

        Read.Found<GraphRead> found = found(service.graph(ID, null));

        assertThat(found.value().graph()).isEqualTo(graph());
        assertThat(found.value().stored()).isEqualTo(stored);
        assertThat(found.value().sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(found.value().governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(found.etag())
                .isEqualTo(
                        EffectiveTag.graph(stored.revision(), closure(CLOSURE)).token());
        assertThat(asked).hasSize(1);
        assertThat(asked.getFirst().selected()).containsExactlyInAnyOrder(A, T1);
        assertThat(asked.getFirst().deactivated()).containsExactly(C);
        assertThat(packs.calls()).as("one verified read").isEqualTo(1);
    }

    @Test
    void theGraphAnswers304ToItsOwnTagAndToNoOtherReadsTag() {
        String graphTag =
                EffectiveTag.graph(stored.revision(), closure(CLOSURE)).token();
        String skippedTag =
                EffectiveTag.skipped(stored.revision(), closure(CLOSURE)).token();

        assertThat(service.graph(ID, EntityTags.quote(graphTag))).isEqualTo(new Read.NotModified<GraphRead>(graphTag));
        assertThat(packs.graphReads()).as("a 304 reads no graph").isZero();
        assertThat(service.graph(ID, EntityTags.quote(skippedTag)))
                .as("the skipped report's tag is no tag of the graph")
                .isInstanceOf(Read.Found.class);
        assertThat(packs.graphReads()).isEqualTo(1);
    }

    @Test
    void theContextAnswers304ToItsOwnTagForItsOwnMissionTypeActionAndDepthOnly() {
        String tag = EffectiveTag.context(stored.revision(), closure(CLOSURE), IMPLEMENT)
                .token();
        packs.contexts(ask -> walked());

        assertThat(service.actionContext(ID, IMPLEMENT, EntityTags.quote(tag)))
                .isEqualTo(new Read.NotModified<ContextRead>(tag));
        assertThat(packs.contextAsks()).as("a 304 walks nothing").isEmpty();
        assertThat(service.actionContext(
                        ID, new EffectiveTag.ContextQuery("software-dev", "implement", 0), EntityTags.quote(tag)))
                .as("another depth is another representation")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void theGraphAndTheContextAnswerTagsOfTheirOwnAndAChangeOfThePacksOfTheClosureMovesBoth() {
        packs.contexts(ask -> walked());
        String graph = found(service.graph(ID, null)).etag();
        String context = found(service.actionContext(ID, IMPLEMENT, null)).etag();
        String skipped = found(service.skipped(ID, null)).etag();

        assertThat(new HashSet<>(List.of(graph, context, skipped)))
                .as("three reads, three tags")
                .hasSize(3);
        packs.answer(closure("sha256:changed"));
        assertThat(found(service.graph(ID, null)).etag()).isNotEqualTo(graph);
        assertThat(found(service.actionContext(ID, IMPLEMENT, null)).etag()).isNotEqualTo(context);
    }

    @Test
    void theContextListsTheWalksEntriesAndItsSuggestionThroughTheEffectiveSetsCarrier() {
        packs.contexts(ask -> walked());

        ContextRead read = context(IMPLEMENT);

        assertThat(read.context().artifacts())
                .extracting(item -> item.artifact().ref())
                .as("A is in force and walked, B is not selected and suggested; T1 is in force but not reached")
                .containsExactly(A, B);
        assertThat(read.context().artifacts().get(1).artifact().suggestion())
                .contains(new EffectiveSet.Suggestion(Optional.of("when"), Optional.of("why")));
        assertThat(read.context().enhancements())
                .containsExactly(
                        new EffectiveContext.Enhancement(
                                new NodeRef(NodeKind.ACTION, "software-dev/implement"), List.of(STEP_ENHANCER)),
                        new EffectiveContext.Enhancement(NodeRef.of(A), List.of(T1)));
        assertThat(read.query()).isEqualTo(IMPLEMENT);
        assertThat(read.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(read.governanceFingerprint()).isEqualTo(CLOSURE);
    }

    @Test
    void theContextNamesThePackThatHoldsTheMissionTypesVersionInForceAndNoneWhenTheSetDoesNotListIt() {
        packs.contexts(ask -> walked());

        assertThat(context(IMPLEMENT).missionTypePack())
                .as("the set lists no mission type")
                .isEmpty();

        ArtifactRef type = new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev");
        packs.answer(ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "A", "B", "C")
                .held(ArtifactKind.TACTIC, "t1")
                .held(ArtifactKind.MISSION_TYPE, "software-dev")
                .fingerprint(SOURCE)
                .closureFingerprint("sha256:with-a-mission-type")
                .build());
        MergedSnapshot base = merged();
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>(base.winners());
        winners.put(type, new MergedSnapshot.Winner("proj", PackLevel.PROJECT, Policy.OPEN));
        packs.merged(new MergedSnapshot(winners, base.languages(), base.edges(), base.layerOverrides()));
        packs.resolves(query -> {
            InForceAnswer plain = answer();
            Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>(plain.states());
            states.put(type, state(type, State.INACTIVE, Reason.NOT_SELECTED, Optional.empty()));
            return new InForceAnswer(
                    states,
                    plain.staleIds(),
                    plain.denyNotApplied(),
                    plain.overrideNotApplied(),
                    plain.requiredNotHeld(),
                    plain.requiredNotInForce(),
                    plain.enhancedTargets());
        });

        assertThat(context(IMPLEMENT).missionTypePack()).contains("proj");
    }

    @Test
    void theContextAsksTheSessionOnceForTheWalkAndResolvesTheInForceStatesOnceForTheSet() {
        packs.contexts(ask -> walked());

        context(IMPLEMENT);

        assertThat(packs.contextAsks()).hasSize(1);
        assertThat(packs.contextAsks().getFirst().missionType()).isEqualTo("software-dev");
        assertThat(packs.contextAsks().getFirst().action()).isEqualTo("implement");
        assertThat(packs.contextAsks().getFirst().depth()).isEqualTo(1);
        assertThat(packs.contextAsks().getFirst().query().selected()).containsExactlyInAnyOrder(A, T1);
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.merges()).isEqualTo(1);
        assertThat(packs.queries()).hasSize(1);
    }

    @Test
    void aMissionTypeNotInForceAnswersAnEmptyContextWithTheMissionTypeInTheSkippedReport() {
        packs.contexts(ask -> ActionContext.notInForce());

        ContextRead read = context(IMPLEMENT);

        assertThat(read.context().artifacts()).isEmpty();
        assertThat(read.context().enhancements()).isEmpty();
        assertThat(read.context().skipped().missionTypeNotInForce())
                .containsExactly(new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev"));
    }

    @Test
    void anUnknownMissionTypeOrActionIsTheRefusalTheSessionThrowsAndNothingElseIsRead() {
        for (RefusalCode code : List.of(RefusalCode.MISSION_TYPE_NOT_FOUND, RefusalCode.ACTION_NOT_FOUND)) {
            packs.contexts(ask -> {
                throw new CharterRefusal(code, "no such name");
            });

            assertThatThrownBy(() -> service.actionContext(ID, IMPLEMENT, "*"))
                    .as("a wildcard If-None-Match matches any tag, so only the name check can refuse first")
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refused -> assertThat(refused.code()).isEqualTo(code));
        }
        assertThat(packs.merges())
                .as("a name refused is refused before the set is built")
                .isZero();
    }

    @Test
    void anUnknownActivationOrOneWhosePackIsNoLongerConfiguredRefusesBothReads() {
        ActivationId unknown = new ActivationId("unknown");

        assertThatThrownBy(() -> service.graph(unknown, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.actionContext(unknown, IMPLEMENT, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        packs.answer(ClosureSnapshot.unresolved(Set.of("main")));
        assertThatThrownBy(() -> service.graph(ID, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThatThrownBy(() -> service.actionContext(ID, IMPLEMENT, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void anUnknownNameIsRefusedBeforeTheTagIsComparedWhateverIfNoneMatchSays() {
        EffectiveTag.ContextQuery unknown = new EffectiveTag.ContextQuery("software-dev", "missing", 0);
        String tag = EffectiveTag.context(stored.revision(), closure(CLOSURE), unknown)
                .token();
        packs.contexts(ask -> {
            if (ask.action().equals("missing")) {
                throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, "no such action");
            }
            return walked();
        });

        assertThatThrownBy(() -> service.actionContext(ID, unknown, EntityTags.quote(tag)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
        assertThat(packs.contextAsks())
                .as("the names alone were judged, nothing was walked")
                .isEmpty();
        assertThat(packs.merges()).isZero();
        String known = EffectiveTag.context(stored.revision(), closure(CLOSURE), IMPLEMENT)
                .token();
        assertThat(service.actionContext(ID, IMPLEMENT, EntityTags.quote(known)))
                .as("a known name keeps its 304")
                .isEqualTo(new Read.NotModified<ContextRead>(known));
    }

    // ---- the effective prompt (WP07; FR-003, FR-004; research R10) ----

    private static final EffectiveTag.PromptQuery PLAN = new EffectiveTag.PromptQuery("software-dev", "plan");
    private static final ArtifactRef SOFTWARE_DEV = new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev");
    private static final EffectivePrompt PROMPT = new EffectivePrompt(
            "# Plan\n",
            Optional.of("main"),
            List.of(new EffectivePrompt.Slot(
                    "spdd:reasons-block",
                    List.of(new EffectivePrompt.Fragment("main", "main-pack", PackLevel.ORG, "spdd-plan", "Fill.\n")))),
            List.of());

    private static CharterRefusal refusal(RefusalCode code) {
        return new CharterRefusal(code, code.token());
    }

    @Test
    void thePromptIsTheSessionsPromptOfTheStepUnderItsOwnTagWithTheReadsFingerprints() {
        packs.active((ref, query) -> ref.equals(SOFTWARE_DEV));
        packs.prompts(ask -> PROMPT);

        Read.Found<EffectiveReadService.PromptRead> read = found(service.prompt(ID, PLAN, null));

        assertThat(read.value().prompt()).isEqualTo(PROMPT);
        assertThat(read.value().query()).isEqualTo(PLAN);
        assertThat(read.value().sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(read.value().governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(read.etag())
                .isEqualTo(EffectiveTag.prompt(stored.revision(), closure(CLOSURE), PLAN)
                        .token());
        assertThat(packs.promptAsks()).singleElement().satisfies(ask -> {
            assertThat(ask.missionType()).isEqualTo("software-dev");
            assertThat(ask.action()).isEqualTo("plan");
            assertThat(ask.query().selected()).containsExactlyInAnyOrder(A, T1);
        });
        assertThat(packs.nameChecks()).containsExactly("software-dev/plan");
        assertThat(packs.activeAsks()).containsExactly(SOFTWARE_DEV);
        assertThat(packs.calls()).as("one session for the whole read").isEqualTo(1);
    }

    @Test
    void aMatchingIfNoneMatchIs304AndReadsNoPromptWhileAnotherTagReadsIt() {
        packs.active((ref, query) -> true);
        packs.prompts(ask -> PROMPT);
        String tag =
                EffectiveTag.prompt(stored.revision(), closure(CLOSURE), PLAN).token();

        assertThat(service.prompt(ID, PLAN, EntityTags.quote(tag)))
                .isEqualTo(new Read.NotModified<EffectiveReadService.PromptRead>(tag));
        assertThat(service.prompt(ID, PLAN, "*")).isEqualTo(new Read.NotModified<EffectiveReadService.PromptRead>(tag));
        assertThat(packs.promptAsks()).as("a 304 reads no prompt").isEmpty();
        String contextTag = EffectiveTag.context(stored.revision(), closure(CLOSURE), IMPLEMENT)
                .token();
        assertThat(service.prompt(ID, PLAN, EntityTags.quote(contextTag)))
                .as("the tag of another read is no tag of the prompt")
                .isInstanceOf(Read.Found.class);
        packs.answer(closure("sha256:changed"));
        assertThat(service.prompt(ID, PLAN, EntityTags.quote(tag)))
                .as("a change of a pack of the closure moves the tag")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void eachNotFoundIsJudgedBeforeTheInForceCheckAndBeforeThe304() {
        packs.active((ref, query) -> true);

        assertThatThrownBy(() -> service.prompt(new ActivationId("unknown"), PLAN, "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        for (RefusalCode code : List.of(RefusalCode.MISSION_TYPE_NOT_FOUND, RefusalCode.ACTION_NOT_FOUND)) {
            packs.contexts(ask -> {
                throw refusal(code);
            });

            assertThatThrownBy(() -> service.prompt(ID, PLAN, "*"))
                    .as("a wildcard If-None-Match matches any tag, so only the name check can refuse first")
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refused -> assertThat(refused.code()).isEqualTo(code));
        }
        assertThat(packs.activeAsks())
                .as("a name refused asks nothing of the in-force resolution")
                .isEmpty();
        assertThat(packs.promptAsks()).isEmpty();
    }

    @Test
    void aMissionTypeTheClosureHoldsAndTheActivationDoesNotPutInForceIs409BeforeTheSlotCheckAndThe304() {
        packs.active((ref, query) -> false);
        packs.resolves(query -> {
            throw refusal(RefusalCode.PROMPT_SLOT_MISSING);
        });

        assertThatThrownBy(() -> service.prompt(ID, PLAN, "*"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_IN_FORCE);
                    assertThat(refused.getMessage()).contains("software-dev").contains("a-1");
                });
        assertThat(packs.queries()).as("the slot check is not reached").isEmpty();
        assertThat(packs.promptAsks()).isEmpty();
    }

    @Test
    void theTwinInForceMeetsTheSlotChecksRefusalBeforeThe304() {
        packs.active((ref, query) -> true);
        packs.resolves(query -> {
            throw refusal(RefusalCode.PROMPT_SLOT_MISSING);
        });

        assertThatThrownBy(() -> service.prompt(ID, PLAN, "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PROMPT_SLOT_MISSING));
        assertThat(packs.promptAsks()).isEmpty();
    }

    @Test
    void theContextSaysWhetherItsMissionTypeIsInForce() {
        packs.contexts(ask -> walked());
        for (State state : State.values()) {
            packs.resolves(query -> {
                Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>(answer().states());
                states.put(SOFTWARE_DEV, state(SOFTWARE_DEV, state, Reason.SELECTED, Optional.empty()));
                return new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
            });
            Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>(merged().winners());
            winners.put(SOFTWARE_DEV, new MergedSnapshot.Winner("main", PackLevel.ORG, Policy.OPEN));
            packs.merged(new MergedSnapshot(winners, Map.of(), merged().edges(), List.of()));

            assertThat(context(IMPLEMENT).missionTypeInForce())
                    .as(state.token())
                    .isEqualTo(state == State.ACTIVE);
        }
        packs.merged(merged());
        packs.resolves(query -> answer());
        assertThat(context(IMPLEMENT).missionTypeInForce())
                .as("a mission type the set does not list is not in force")
                .isFalse();
    }
}
