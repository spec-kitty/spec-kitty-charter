package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The activation register's use cases over an in-memory register and a counting fake of the packs. Every test pins how
 * often the packs were read (NFR-007, R8): once per write and per closure read, never for a document read or a delete.
 */
class ActivationServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private ActivationService service;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D1", "D2")
                .requiredBy("directive:D1", "alpha")
                .fingerprint("sha256:first")
                .build());
        service = new ActivationService(repository, packs, new ActivationIdMint());
    }

    private static ActivationDocument.Built selecting(String... ids) {
        return ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of(ids))
                .build();
    }

    private static ActivationDocument.Built denying(String id) {
        return ActivationDocument.builder()
                .deactivated(ArtifactKind.DIRECTIVE, List.of(id))
                .build();
    }

    private static String quoted(StoredActivation stored) {
        return "\"" + stored.revision().token() + "\"";
    }

    private StoredActivation stored(ActivationDocument.Built built) {
        return repository.create(ID, built.document()).orElseThrow();
    }

    // ---- create

    @Test
    void createMintsAUlidAndStoresTheDocumentAfterOneRead() {
        StoredActivation created = service.create(selecting("D2", "D1"));

        assertThat(created.id().value()).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(repository.find(created.id())).contains(created);
        assertThat(created.document().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("D1", "D2");
        assertThat(packs.calls()).isEqualTo(1);
        assertThat(packs.lastRoots()).isEmpty();
    }

    @Test
    void createReadsTheClosureOfTheDocumentsPacks() {
        service.create(
                ActivationDocument.builder().packs(List.of(PackRef.of("alpha"))).build());

        assertThat(packs.lastRoots()).contains(List.of("alpha"));
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void createRefusesAnUnknownIdAndStoresNothing() {
        assertThatThrownBy(() -> service.create(selecting("NO_SUCH")))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations().getFirst().pointer()).isEqualTo("/selected/directive/0");
                });
        assertThat(packs.calls()).isEqualTo(1);
    }

    // ---- put

    @Test
    void putWithoutAValidatorCreatesThenReplaces() {
        ActivationService.Written first = service.put(ID, selecting("D1"), null, null);
        ActivationService.Written second = service.put(ID, selecting("D2"), null, null);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(repository.find(ID)).contains(second.stored());
        assertThat(packs.calls()).isEqualTo(2);
    }

    @Test
    void putOfTheSameDocumentKeepsTheRevision() {
        ActivationService.Written first = service.put(ID, selecting("D1", "D2"), null, null);
        ActivationService.Written again = service.put(ID, selecting("D2", "D1", "D2"), quoted(first.stored()), null);

        assertThat(again.created()).isFalse();
        assertThat(again.stored().revision()).isEqualTo(first.stored().revision());
    }

    @Test
    void ifNoneMatchStarCreatesOnlyWhenTheIdIsFree() {
        assertThat(service.put(ID, selecting("D1"), null, "*").created()).isTrue();

        assertThatThrownBy(() -> service.put(ID, selecting("D2"), null, "*"))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(repository.find(ID).orElseThrow().document())
                .isEqualTo(selecting("D1").document());
        assertThat(packs.calls())
                .as("the refused create-only write takes no snapshot")
                .isEqualTo(1);
    }

    // ---- preconditions before content (RFC 9110 section 13.2.2)

    @Test
    void aStaleIfMatchIsRefusedBeforeAnInvalidBodyIsJudgedAndTakesNoSnapshot() {
        StoredActivation old = stored(selecting("D1"));
        StoredActivation current = service.put(ID, selecting("D2"), null, null).stored();
        int before = packs.calls();

        assertThatThrownBy(() -> service.put(ID, selecting("NO_SUCH"), quoted(old), null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(packs.calls())
                .as("no closure snapshot on a failed precondition")
                .isEqualTo(before);
        assertThat(repository.find(ID)).contains(current);
    }

    @Test
    void anIfMatchOnAMissingDocumentIsRefusedBeforeAnInvalidBodyIsJudged() {
        assertThatThrownBy(() -> service.put(ID, selecting("NO_SUCH"), "*", null))
                .isInstanceOf(PreconditionException.class);
        assertThat(packs.calls()).isZero();
    }

    @Test
    void ifNoneMatchStarOnAnExistingIdIsRefusedBeforeAnInvalidBodyIsJudged() {
        StoredActivation existing = stored(selecting("D1"));

        assertThatThrownBy(() -> service.put(ID, selecting("NO_SUCH"), null, "*"))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(packs.calls()).isZero();
        assertThat(repository.find(ID)).contains(existing);
    }

    @Test
    void aStaleIfMatchOnADeleteIsJudgedFromTheHeadersAloneAndTakesNoSnapshot() {
        StoredActivation old = stored(selecting("D1"));
        service.put(ID, selecting("D2"), null, null);
        int before = packs.calls();

        assertThatThrownBy(() -> service.delete(ID, quoted(old))).isInstanceOf(PreconditionException.class);
        assertThat(packs.calls()).isEqualTo(before);
    }

    @Test
    void aFreshIfMatchWithAnInvalidBodyIsStillJudgedAfterThePrecondition() {
        StoredActivation current = stored(selecting("D1"));

        assertThatThrownBy(() -> service.put(ID, selecting("NO_SUCH"), quoted(current), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID));
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void anIfNoneMatchOtherThanStarIsMalformedAndReadsNothing() {
        assertThatThrownBy(() -> service.put(ID, selecting("D1"), null, "\"x\""))
                .isInstanceOf(MalformedPreconditionException.class);
        assertThat(packs.calls()).isZero();
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void bothValidatorsAreMalformedAndReadNothing() {
        StoredActivation current = stored(selecting("D1"));

        assertThatThrownBy(() -> service.put(ID, selecting("D2"), quoted(current), "*"))
                .isInstanceOf(MalformedPreconditionException.class);
        assertThat(packs.calls()).isZero();
        assertThat(repository.find(ID)).contains(current);
    }

    @Test
    void aFreshIfMatchReplaces() {
        StoredActivation current = stored(selecting("D1"));

        ActivationService.Written written = service.put(ID, selecting("D2"), quoted(current), null);

        assertThat(written.created()).isFalse();
        assertThat(written.stored().document()).isEqualTo(selecting("D2").document());
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aStaleIfMatchIsRefusedAndKeepsTheCurrentDocument() {
        StoredActivation old = stored(selecting("D1"));
        StoredActivation current = service.put(ID, selecting("D2"), null, null).stored();

        assertThatThrownBy(() -> service.put(ID, selecting("D1", "D2"), quoted(old), null))
                .isInstanceOf(PreconditionException.class);
        assertThat(repository.find(ID)).contains(current);
    }

    @Test
    void aWeakIfMatchNeverMatches() {
        StoredActivation current = stored(selecting("D1"));

        assertThatThrownBy(() -> service.put(ID, selecting("D2"), "W/" + quoted(current), null))
                .isInstanceOf(PreconditionException.class);
        assertThat(repository.find(ID)).contains(current);
    }

    @Test
    void ifMatchStarMatchesAnyStoredDocument() {
        stored(selecting("D1"));

        assertThat(service.put(ID, selecting("D2"), "*", null).created()).isFalse();
    }

    @Test
    void anIfMatchOnAMissingDocumentIsAFailedPrecondition() {
        assertThatThrownBy(() -> service.put(ID, selecting("D1"), "*", null)).isInstanceOf(PreconditionException.class);
        assertThatThrownBy(() -> service.put(
                        ID,
                        selecting("D1"),
                        "\"" + selecting("D1").document().revision().token() + "\"",
                        null))
                .isInstanceOf(PreconditionException.class);
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void aDenyOnARequiredArtifactIsRefusedNamingThePackAndNothingIsStored() {
        assertThatThrownBy(() -> service.put(ID, denying("D1"), null, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
                    assertThat(refusal.requiredBy().getFirst().packId()).isEqualTo("alpha");
                });
        assertThat(repository.find(ID)).isEmpty();
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aStoredDenyOnANowRequiredArtifactIsServedAndRefusedOnWrite() {
        StoredActivation stale = stored(denying("D1"));

        assertThat(service.get(ID, null))
                .isEqualTo(new ActivationService.Read.Found<>(
                        stale, stale.revision().token()));
        assertThatThrownBy(() -> service.put(ID, denying("D1"), quoted(stale), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(repository.find(ID)).contains(stale);
    }

    @Test
    void anUnconfiguredPackIsRefusedAtItsPositionAndNothingIsStored() {
        packs.answer(ClosureSnapshot.unresolved(Set.of("ghost")));

        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(List.of(PackRef.of("alpha"), PackRef.of("ghost")))
                .build();

        assertThatThrownBy(() -> service.put(ID, built, null, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo("/packs/1/packId");
                        assertThat(violation.code()).isEqualTo("unknown_pack");
                    });
                });
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void aRefusalOfTheComposePropagatesUnchanged() {
        CharterRefusal broken = new CharterRefusal(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE, "alpha needs beta 2");
        packs.refuse(broken);

        assertThatThrownBy(() -> service.put(ID, selecting("D1"), null, null)).isSameAs(broken);
        assertThat(repository.find(ID)).isEmpty();
    }

    // ---- get

    @Test
    void getAnswersTheStoredDocumentWithoutReadingThePacks() {
        StoredActivation current = stored(selecting("D1"));

        assertThat(service.get(ID, null))
                .isEqualTo(new ActivationService.Read.Found<>(
                        current, current.revision().token()));
        assertThat(service.get(ID, "\"sha256:other\"")).isInstanceOf(ActivationService.Read.Found.class);
        assertThat(packs.calls()).isZero();
    }

    @Test
    void getAnswersNotModifiedOnAStrongAWeakOrAStarTag() {
        StoredActivation current = stored(selecting("D1"));
        String etag = current.revision().token();

        for (String tag : List.of(quoted(current), "W/" + quoted(current), "*", "\"a\", " + quoted(current))) {
            assertThat(service.get(ID, tag)).as(tag).isEqualTo(new ActivationService.Read.NotModified<>(etag));
        }
        assertThat(packs.calls()).isZero();
    }

    @Test
    void getOfAMissingActivationIsNotFound() {
        assertThatThrownBy(() -> service.get(ID, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    // ---- delete

    @Test
    void deleteRemovesAndASecondDeleteIsNotFound() {
        stored(selecting("D1"));

        service.delete(ID, null);

        assertThat(repository.find(ID)).isEmpty();
        assertThatThrownBy(() -> service.delete(ID, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void deleteHonoursIfMatch() {
        StoredActivation current = stored(selecting("D1"));

        assertThatThrownBy(() -> service.delete(
                        ID, "\"" + selecting("D2").document().revision().token() + "\""))
                .isInstanceOf(PreconditionException.class);
        assertThat(repository.find(ID)).contains(current);

        service.delete(ID, quoted(current));
        assertThat(repository.find(ID)).isEmpty();
        assertThat(packs.calls()).isZero();
    }

    // ---- the closure read

    @Test
    void packsAnswersTheClosureUnderATagOverTheRevisionAndTheFingerprint() {
        StoredActivation current = stored(
                ActivationDocument.builder().packs(List.of(PackRef.of("alpha"))).build());

        ActivationService.Read<ClosureSnapshot> read = service.packs(ID, null);

        String etag = ActivationService.closureTag(current.revision(), "sha256:first");
        assertThat(etag).matches("sha256:[0-9a-f]{64}");
        assertThat(read).isInstanceOfSatisfying(ActivationService.Read.Found.class, found -> {
            assertThat(found.etag()).isEqualTo(etag);
            assertThat(((ClosureSnapshot) found.value()).packs())
                    .extracting(ClosureSnapshot.PackEntry::packId)
                    .containsExactly("alpha");
        });
        assertThat(packs.lastRoots()).contains(List.of("alpha"));
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void packsAnswersNotModifiedUntilTheDocumentOrThePacksChange() {
        StoredActivation current = stored(selecting("D1"));
        String etag = ActivationService.closureTag(current.revision(), "sha256:first");

        assertThat(service.packs(ID, "\"" + etag + "\"")).isEqualTo(new ActivationService.Read.NotModified<>(etag));

        packs.answer(ClosureSnapshots.snapshot().fingerprint("sha256:second").build());
        assertThat(service.packs(ID, "\"" + etag + "\"")).isInstanceOf(ActivationService.Read.Found.class);

        packs.answer(ClosureSnapshots.snapshot()
                .fingerprint("sha256:first")
                .held(ArtifactKind.DIRECTIVE, "D1")
                .build());
        service.put(ID, selecting("D1", "D1"), null, null);
        assertThat(service.packs(ID, "\"" + etag + "\"")).isInstanceOf(ActivationService.Read.NotModified.class);
        assertThat(packs.calls()).isEqualTo(4);
    }

    @Test
    void packsOfADocumentWhosePackIsNoLongerConfiguredIsUnreadable() {
        stored(ActivationDocument.builder().packs(List.of(PackRef.of("gone"))).build());
        packs.answer(ClosureSnapshot.unresolved(Set.of("gone")));

        assertThatThrownBy(() -> service.packs(ID, null)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains("gone");
        });
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void packsOfAMissingActivationIsNotFoundWithoutReadingThePacks() {
        assertThatThrownBy(() -> service.packs(ID, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void packsPropagatesARefusalOfTheCompose() {
        stored(selecting("D1"));
        CharterRefusal unreadable = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "beta is gone");
        packs.refuse(unreadable);

        assertThatThrownBy(() -> service.packs(ID, null)).isSameAs(unreadable);
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void theRootsOfTheClosureAreTheDocumentsPacksInOrder() {
        stored(ActivationDocument.builder()
                .packs(List.of(PackRef.of("beta"), PackRef.pinned("alpha", "1.0.0")))
                .build());

        service.packs(ID, null);

        assertThat(packs.lastRoots()).isEqualTo(Optional.of(List.of("beta", "alpha")));
    }
}
