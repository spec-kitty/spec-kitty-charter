package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveReadService.After;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.application.EffectiveReadService.StaleCursorException;
import ai.speckitty.charter.activations.application.EffectiveReadService.Viewed;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyReason;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
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
        return new MergedSnapshot(winners, Map.of(), List.of(), List.of());
    }

    private static ArtifactState state(ArtifactRef ref, State state, Reason reason, Optional<Bucket> bucket) {
        return new ArtifactState(ref, state, reason, false, Optional.empty(), Optional.empty(), bucket);
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
}
