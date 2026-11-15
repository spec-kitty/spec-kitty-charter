package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ProfileCandidates} and the activation's half of {@code selectAgentProfile}
 * ({@link EffectiveReadService#profileSelection}): one session answers the candidates of the asked role under the
 * stored document; the fallback conflict, then the pin conflict, is refused naming the packs, whatever the candidates;
 * and the tag is the {@link EffectiveTag#selection} of the role and the context, which no other read shares.
 */
class ProfileCandidatesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final EffectiveTag.SelectionQuery REVIEWER =
            new EffectiveTag.SelectionQuery("reviewer", Optional.empty(), Optional.empty());

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure(CLOSURE));
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("org-base")))
                                .selected(ArtifactKind.AGENT_PROFILE, List.of("reviewer-renata", "reviewer-rita"))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("org-base", "1.0.0")
                .held(ArtifactKind.AGENT_PROFILE, "reviewer-renata", "reviewer-rita")
                .fingerprint(SOURCE)
                .closureFingerprint(closureFingerprint)
                .build();
    }

    private static CharterArtifact profile(String id, String pack) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.AGENT_PROFILE;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier(pack);
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }

    private static ProfileCandidates.Candidate candidate(String id, String pack, int position, String... ancestors) {
        return new ProfileCandidates.Candidate(profile(id, pack), pack, position, ancestors.length, Set.of(ancestors));
    }

    private static ProfileCandidates tie() {
        return new ProfileCandidates(
                List.of(candidate("reviewer-renata", "built-in", 0), candidate("reviewer-rita", "org-base", 1)),
                new ProfileCandidates.Pin.None(),
                new ProfileCandidates.Fallback.Held(candidate("generic-agent", "built-in", 0), "built-in"));
    }

    // ---- the values

    @Test
    void aCandidateIsItsProfilesIdAndRefusesANegativePositionOrDepth() {
        assertThat(candidate("reviewer-rita", "org-base", 1).id()).isEqualTo("reviewer-rita");
        assertThatThrownBy(() -> new ProfileCandidates.Candidate(profile("p", "x"), "x", -1, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileCandidates.Candidate(profile("p", "x"), "x", 0, -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePinnedSpecializationsAreTheCandidatesWhoseLineageHoldsThePinnedProfile() {
        ProfileCandidates pinned = new ProfileCandidates(
                List.of(
                        candidate("implementer-ivan", "built-in", 0),
                        candidate("java-jenny", "built-in", 0, "implementer-ivan"),
                        candidate("java-spring", "project", 2, "java-jenny", "implementer-ivan"),
                        candidate("project-reviewer", "project", 2)),
                new ProfileCandidates.Pin.Pinned("implementer-ivan", "project"),
                new ProfileCandidates.Fallback.None());

        assertThat(pinned.pinnedSpecializations()).containsExactlyInAnyOrder("java-jenny", "java-spring");
        assertThat(tie().pinnedSpecializations())
                .as("no pin, no specializations")
                .isEmpty();
        assertThat(ProfileCandidates.NONE.candidates()).isEmpty();
        assertThat(ProfileCandidates.NONE.pin()).isEqualTo(new ProfileCandidates.Pin.None());
        assertThat(ProfileCandidates.NONE.fallback()).isEqualTo(new ProfileCandidates.Fallback.None());
    }

    // ---- the read

    @Test
    void theCandidatesOfTheRoleAreTheSessionsUnderTheStoredDocumentWithTheReadsFingerprintsAndTag() {
        ProfileCandidates tie = tie();
        packs.profileCandidates((role, query) -> tie);

        EffectiveReadService.SelectionRead read = service.profileSelection(ID, REVIEWER);

        assertThat(read.candidates()).isSameAs(tie);
        assertThat(read.candidates().candidates())
                .extracting(ProfileCandidates.Candidate::pack)
                .as("each candidate keeps the pack that holds it")
                .containsExactly("built-in", "org-base");
        assertThat(read.stored()).isEqualTo(stored);
        assertThat(read.query()).isEqualTo(REVIEWER);
        assertThat(read.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(read.governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(read.etag())
                .isEqualTo(EffectiveTag.selection(stored.revision(), closure(CLOSURE), REVIEWER)
                        .token());
        assertThat(packs.candidateAsks()).containsExactly("reviewer");
        assertThat(packs.calls()).as("one session for the whole read").isEqualTo(1);
    }

    @Test
    void anUnknownActivationIsRefusedBeforeThePacksAreRead() {
        assertThatThrownBy(() -> service.profileSelection(new ActivationId("unknown"), REVIEWER))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aFallbackConflictIsRefusedBeforeAPinConflictNamingThePacks() {
        packs.profileCandidates((role, query) -> new ProfileCandidates(
                tie().candidates(),
                new ProfileCandidates.Pin.Conflict(List.of("team-a", "team-b")),
                new ProfileCandidates.Fallback.Conflict(List.of("team-a", "team-c"))));

        assertThatThrownBy(() -> service.profileSelection(ID, REVIEWER))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.FALLBACK_PROFILE_CONFLICT);
                    assertThat(refused.getMessage()).contains("team-a, team-c").contains("a-1");
                });
    }

    @Test
    void aPinConflictIsRefusedNamingThePacksAndTheRole() {
        packs.profileCandidates((role, query) -> new ProfileCandidates(
                tie().candidates(), new ProfileCandidates.Pin.Conflict(List.of("team-a", "team-b")), tie().fallback()));

        assertThatThrownBy(() -> service.profileSelection(ID, REVIEWER))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.ROLE_PIN_CONFLICT);
                    assertThat(refused.getMessage()).contains("team-a, team-b").contains("reviewer");
                });
    }

    @Test
    void aRefusalOfTheSessionIsTheAnswerUnchanged() {
        packs.profileCandidates((role, query) -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
        });

        assertThatThrownBy(() -> service.profileSelection(ID, REVIEWER))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    // ---- the tag

    @Test
    void theSelectionTagNamesTheRoleAndTheContextAndNoOtherReadSharesIt() {
        ClosureSnapshot closure = closure(CLOSURE);
        EffectiveTag reviewer = EffectiveTag.selection(stored.revision(), closure, REVIEWER);
        Set<EffectiveTag> tags = Set.of(
                reviewer,
                EffectiveTag.selection(
                        stored.revision(),
                        closure,
                        new EffectiveTag.SelectionQuery("implementer", Optional.empty(), Optional.empty())),
                EffectiveTag.selection(
                        stored.revision(),
                        closure,
                        new EffectiveTag.SelectionQuery("reviewer", Optional.of("java"), Optional.empty())),
                EffectiveTag.selection(
                        stored.revision(),
                        closure,
                        new EffectiveTag.SelectionQuery("reviewer", Optional.empty(), Optional.of("java"))),
                EffectiveTag.selection(stored.revision(), closure(CLOSURE + "2"), REVIEWER),
                EffectiveTag.activation(stored.revision(), closure),
                EffectiveTag.profile(
                        stored.revision(),
                        closure,
                        new EffectiveTag.ArtifactQuery(ArtifactKind.AGENT_PROFILE, "reviewer", 0)));

        assertThat(tags)
                .as("each role, context, closure and read is a tag of its own")
                .hasSize(7);
        assertThat(EffectiveTag.selection(stored.revision(), closure, REVIEWER)).isEqualTo(reviewer);
    }
}
