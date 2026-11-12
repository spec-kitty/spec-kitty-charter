package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void everySpellingOfAnIdStoresTheSameDocumentUnderTheSameRevision() {
        StoredActivation declared = service.put(ID, selecting("D2"), null, null).stored();
        for (String spelling : List.of("directive:D2", "d2", "directive:d2")) {
            StoredActivation other =
                    service.put(ID, selecting(spelling), null, null).stored();

            assertThat(other.document()).as(spelling).isEqualTo(declared.document());
            assertThat(other.revision()).as(spelling).isEqualTo(declared.revision());
        }
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
    void aStaleIfMatchIsRefusedBeforeAnInvalidBodyIsJudgedAndTakesOnlyTheReadThatComputesTheTag() {
        StoredActivation old = stored(selecting("D1"));
        StoredActivation current = service.put(ID, selecting("D2"), null, null).stored();
        int before = packs.calls();

        assertThatThrownBy(() -> service.put(ID, selecting("NO_SUCH"), quoted(old), null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(packs.calls())
                .as("one read to see whether the header is the closure tag, none to judge the body")
                .isEqualTo(before + 1);
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
    void aStaleIfMatchOnADeleteTakesOnlyTheReadThatComputesTheTag() {
        StoredActivation old = stored(selecting("D1"));
        service.put(ID, selecting("D2"), null, null);
        int before = packs.calls();

        assertThatThrownBy(() -> service.delete(ID, quoted(old))).isInstanceOf(PreconditionException.class);
        assertThat(packs.calls()).isEqualTo(before + 1);
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

    /**
     * A put without If-Match is judged against the document it replaces, read just before the write; when another
     * writer wins every one of its eight compare-and-sets, it answers 412 (replaceActivation's description) and stores
     * nothing.
     */
    @Test
    void aPutWithoutIfMatchThatLosesEveryAttemptIsAFailedPreconditionAndStoresNothing() {
        StoredActivation before = stored(selecting("D1"));
        int[] attempts = {0};
        ActivationRepository alwaysLosing = new ActivationRepository() {
            @Override
            public List<StoredActivation> list() {
                return repository.list();
            }

            @Override
            public Optional<StoredActivation> find(ActivationId id) {
                return repository.find(id);
            }

            @Override
            public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
                return Optional.empty();
            }

            @Override
            public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
                attempts[0]++;
                return new CasResult.Stale(before.revision());
            }

            @Override
            public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
                return repository.delete(id, expected);
            }
        };
        ActivationService losing = new ActivationService(alwaysLosing, packs, new ActivationIdMint());

        assertThatThrownBy(() -> losing.put(ID, selecting("D2"), null, null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        stale -> assertThat(stale.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(attempts[0]).isEqualTo(8);
        assertThat(repository.find(ID)).contains(before);
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
    void a4APutOfAStoredDenyOnANowRequiredArtifactIsAcceptedAndKeepsTheRevision() {
        // The review's counter-example: the stored document denies D1, which a pack now requires (it was tolerated
        // since, ADR-0059 D4); the client sends back the body it read.
        StoredActivation stale = stored(denying("D1"));

        ActivationService.Written conditional = service.put(ID, denying("D1"), quoted(stale), null);
        ActivationService.Written unconditional = service.put(ID, denying("D1"), null, null);

        assertThat(conditional.stored()).isEqualTo(stale);
        assertThat(unconditional.stored()).isEqualTo(stale);
        assertThat(conditional.dropped()).isEmpty();
        assertThat(packs.calls()).isEqualTo(2);
    }

    @Test
    void aPutWithoutAValidatorIsJudgedAgainstTheDocumentAnotherWriterStoredInBetween() {
        stored(denying("D1"));
        ActivationRepository racing = new ActivationRepository() {
            private boolean raced;

            @Override
            public List<StoredActivation> list() {
                return repository.list();
            }

            @Override
            public Optional<StoredActivation> find(ActivationId id) {
                return repository.find(id);
            }

            @Override
            public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
                return repository.create(id, document);
            }

            @Override
            public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
                if (!raced) {
                    raced = true;
                    repository.replace(id, Optional.empty(), selecting("D2").document());
                }
                return repository.replace(id, expected, document);
            }

            @Override
            public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
                return repository.delete(id, expected);
            }
        };
        ActivationService raced = new ActivationService(racing, packs, new ActivationIdMint());

        assertThatThrownBy(() -> raced.put(ID, denying("D1"), null, null))
                .as("the deny entry the other writer removed is added again, so it is judged on the floor")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(repository.find(ID).orElseThrow().document())
                .isEqualTo(selecting("D2").document());
        assertThat(packs.calls()).isEqualTo(1);
    }

    @Test
    void aPutJudgesTheFloorOnADenyEntryItAddsNextToAToleratedOne() {
        StoredActivation stale = stored(denying("D1"));
        ActivationDocument.Built adding = ActivationDocument.builder()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("D1", "D2"))
                .build();
        packs.answer(ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D1", "D2")
                .requiredBy("directive:D1", "alpha")
                .requiredBy("directive:D2", "alpha")
                .build());

        assertThatThrownBy(() -> service.put(ID, adding, quoted(stale), null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
                    assertThat(refusal.getMessage()).contains("directive:D2").doesNotContain("directive:D1");
                });
        assertThat(repository.find(ID)).contains(stale);
    }

    @Test
    void a4AGetThenPutAfterAPackUpgradeRemovedAnIdDropsItAndReportsIt() {
        ActivationDocument.Built read = ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D2", "GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("OLD"))
                .build();
        StoredActivation before = stored(read);

        ActivationService.Written written = service.put(ID, read, quoted(before), null);

        assertThat(written.created()).isFalse();
        assertThat(written.dropped())
                .containsExactly(
                        new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"), new ArtifactRef(ArtifactKind.TACTIC, "OLD"));
        assertThat(written.stored().document())
                .isEqualTo(ActivationDocument.builder()
                        .selected(ArtifactKind.DIRECTIVE, List.of("D2"))
                        .build()
                        .document());
        assertThat(repository.find(ID)).contains(written.stored());
        assertThat(packs.calls()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"directive:GONE", "gone", "DIRECTIVE:gone"})
    void aPutNamingAStaleIdInAnotherSpellingReportsItAsStored(String spelling) {
        StoredActivation before = stored(ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D2", "GONE"))
                .deactivated(ArtifactKind.TACTIC, List.of("OLD"))
                .build());

        ActivationService.Written written = service.put(
                ID,
                ActivationDocument.builder()
                        .selected(ArtifactKind.DIRECTIVE, List.of("D2", spelling))
                        .build(),
                quoted(before),
                null);

        assertThat(written.dropped()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE"));
        assertThat(written.stored().document().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("D2");
    }

    @Test
    void aPutStillRefusesAnUnknownIdTheStoredDocumentNeverNamed() {
        StoredActivation before = stored(selecting("D2"));

        assertThatThrownBy(() -> service.put(ID, selecting("D2", "NO_SUCH"), quoted(before), null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(violation -> assertThat(violation.code()).isEqualTo("unknown_artifact"));
                });
        assertThat(repository.find(ID)).contains(before);
    }

    @Test
    void b1ACreateRefusesAPinNoPackHoldsAndAPutOfTheSameBodyDropsIt() {
        ActivationDocument.Built pinned = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("alpha", "0.9.0")))
                .selected(ArtifactKind.DIRECTIVE, List.of("D2"))
                .build();

        assertThatThrownBy(() -> service.create(pinned))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.violations())
                                .singleElement()
                                .satisfies(violation -> {
                                    assertThat(violation.pointer()).isEqualTo("/packs/0/version");
                                    assertThat(violation.code()).isEqualTo("version_not_held");
                                }));
        ActivationService.Written written = service.put(ID, pinned, null, null);

        assertThat(written.created()).isTrue();
        assertThat(written.droppedPins()).containsExactly(PackRef.pinned("alpha", "0.9.0"));
        assertThat(written.stored().document().packs()).contains(List.of(PackRef.of("alpha")));
        assertThat(written.stored().document().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("D2");
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
        assertThat(packs.calls())
                .as("only the stale tag-shaped header read the closure, to see whether it is the closure tag")
                .isEqualTo(1);
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

    // ---- C38: an activation write that would put an enhancement in force without its slot (squad A3)
    // ------------------

    private static final CharterRefusal SLOT_MISSING = new CharterRefusal(
            RefusalCode.PROMPT_SLOT_MISSING, "step_enhancement:x fills a slot the prompt in force does not declare");

    /** The fake judges the compose as the slot check would: a document that puts D2 in force is refused. */
    private void d2DoesNotCompose() {
        packs.composes(query -> query.selected().contains(new ArtifactRef(ArtifactKind.DIRECTIVE, "D2"))
                ? Optional.of(SLOT_MISSING)
                : Optional.empty());
    }

    @Test
    void createRefusesADocumentThatDoesNotComposeAndStoresNothing() {
        d2DoesNotCompose();

        assertThatThrownBy(() -> service.create(selecting("D2"))).isSameAs(SLOT_MISSING);

        assertThat(repository.list()).isEmpty();
        assertThat(packs.composabilityChecks())
                .singleElement()
                .satisfies(
                        query -> assertThat(query.selected()).contains(new ArtifactRef(ArtifactKind.DIRECTIVE, "D2")));
        assertThat(packs.queries())
                .as("the slot check is asked for itself, never reached through the floor's resolution")
                .isEmpty();
    }

    @Test
    void theTwinCreateOfADocumentThatComposesIsStoredAfterItsCheck() {
        d2DoesNotCompose();

        StoredActivation created = service.create(selecting("D1"));

        assertThat(repository.find(created.id())).contains(created);
        assertThat(packs.composabilityChecks()).hasSize(1);
    }

    @Test
    void putRefusesADocumentThatDoesNotComposeAndLeavesTheStoredOne() {
        StoredActivation before = stored(selecting("D1"));
        d2DoesNotCompose();

        assertThatThrownBy(() -> service.put(ID, selecting("D2"), quoted(before), null))
                .isSameAs(SLOT_MISSING);
        assertThatThrownBy(() -> service.put(new ActivationId("a-2"), selecting("D2"), null, "*"))
                .isSameAs(SLOT_MISSING);

        assertThat(repository.find(ID)).contains(before);
        assertThat(repository.find(new ActivationId("a-2"))).isEmpty();
    }

    @Test
    void theTwinPutOfADocumentThatComposesReplacesTheStoredOne() {
        StoredActivation before = stored(selecting("D1"));
        d2DoesNotCompose();

        ActivationService.Written written = service.put(ID, selecting("D1", "D1"), quoted(before), null);

        assertThat(written.stored().revision()).isEqualTo(before.revision());
        assertThat(packs.composabilityChecks()).isNotEmpty();
    }

    @Test
    void aPerArtifactWriteRefusesADocumentThatDoesNotComposeBeforeItIsStored() {
        StoredActivation before = stored(selecting("D1"));
        d2DoesNotCompose();
        ActivationSelectionService selections = new ActivationSelectionService(new ActivationWrites(repository, packs));

        assertThatThrownBy(() -> selections.select(
                        ID,
                        "directive",
                        "D2",
                        Optional.empty(),
                        false,
                        java.util.OptionalInt.empty(),
                        List.of(),
                        quoted(before)))
                .isSameAs(SLOT_MISSING);

        assertThat(repository.find(ID)).contains(before);
    }

    @Test
    void theTwinAPerArtifactWriteThatComposesIsStored() {
        StoredActivation before = stored(selecting("D2"));
        d2DoesNotCompose();
        ActivationSelectionService selections = new ActivationSelectionService(new ActivationWrites(repository, packs));

        ActivationWrites.Outcome outcome = selections.unselect(ID, "directive", "D2", quoted(before));

        assertThat(outcome.stored()
                        .document()
                        .selected(ArtifactKind.DIRECTIVE)
                        .<Set<String>>map(set -> set)
                        .orElse(Set.of()))
                .isEmpty();
        assertThat(repository.find(ID)).contains(outcome.stored());
    }
}
