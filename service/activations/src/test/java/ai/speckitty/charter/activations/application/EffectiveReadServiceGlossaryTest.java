package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.TermHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The glossary reads under an activation (#106, WP05; research R5 and R7): the context's literal route of
 * {@link EffectiveReadService#artifact} answers the layer-merge winner of a context whatever its state, under a tag of
 * its own; {@link EffectiveReadService#heldWinners} lists the context winners; and
 * {@link EffectiveReadService#glossaryTerm} finds a term in the winning context, with the context's facts, judging the
 * activation, the context and the term in that order, each before any {@code If-None-Match}.
 *
 * <p>The closure: core (built-in) and proj (project) both hold the context spec-kitty-core, and proj's copy wins with
 * the term mission alone (core's copy, which it replaces whole, also holds lane). core holds qa, which the activation
 * does not select, and legacy, which it deactivates.
 */
class EffectiveReadServiceGlossaryTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final ArtifactRef CORE = new ArtifactRef(ArtifactKind.GLOSSARY, "spec-kitty-core");
    private static final ArtifactRef QA = new ArtifactRef(ArtifactKind.GLOSSARY, "qa");
    private static final ArtifactRef LEGACY = new ArtifactRef(ArtifactKind.GLOSSARY, "legacy");
    private static final EffectiveHeldWinner CORE_WINNER =
            new EffectiveHeldWinner(CORE, "proj", PackLevel.PROJECT, Policy.OPEN);
    private static final EffectiveHeldWinner QA_WINNER =
            new EffectiveHeldWinner(QA, "core", PackLevel.BUILT_IN, Policy.OPEN);

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private EffectiveReadService service;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure());
        packs.listedKinds(List.of(ArtifactKind.DIRECTIVE, ArtifactKind.SKILL, ArtifactKind.GLOSSARY));
        packs.heldWinners(kind -> kind == ArtifactKind.GLOSSARY ? List.of(QA_WINNER, CORE_WINNER) : List.of());
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        winners.put(CORE, new MergedSnapshot.Winner("proj", PackLevel.PROJECT, Policy.OPEN));
        winners.put(QA, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        winners.put(LEGACY, new MergedSnapshot.Winner("core", PackLevel.BUILT_IN, Policy.OPEN));
        packs.merged(new MergedSnapshot(winners, Map.of(), List.of(), List.of()));
        packs.resolves(query -> new InForceAnswer(
                Map.of(
                        CORE, state(CORE, State.ACTIVE, Reason.SELECTED),
                        QA, state(QA, State.INACTIVE, Reason.NOT_SELECTED),
                        LEGACY, state(LEGACY, State.EXCLUDED, Reason.DEACTIVATED)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()));
        packs.winners(ask -> Optional.of(new EffectiveArtifactRead(
                context(ask.id(), ask.id().equals(CORE.id()) ? Set.of("mission") : Set.of("flaky-test")),
                ask.id().equals(CORE.id()) ? "proj" : "core",
                ask.id().equals(CORE.id()) ? PackLevel.PROJECT : PackLevel.BUILT_IN,
                Optional.empty())));
        service = new EffectiveReadService(repository, packs);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(
                                        PackRef.of(new PackIdentifier("core")), PackRef.of(new PackIdentifier("proj"))))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ArtifactState state(ArtifactRef ref, State state, Reason reason) {
        return new ArtifactState(ref, state, reason, false, List.of(), List.of(), Optional.empty());
    }

    private static ClosureSnapshot closure() {
        return ClosureSnapshots.snapshot()
                .pack("core", "1.0.0")
                .pack("proj", "1.0.0")
                .held(ArtifactKind.GLOSSARY, "spec-kitty-core")
                .held(ArtifactKind.GLOSSARY, "qa")
                .held(ArtifactKind.GLOSSARY, "legacy")
                .fingerprint(SOURCE)
                .closureFingerprint(CLOSURE)
                .build();
    }

    private static <T> T found(Read<T> read) {
        assertThat(read).isInstanceOf(Read.Found.class);
        return ((Read.Found<T>) read).value();
    }

    private static <T> String etag(Read<T> read) {
        return switch (read) {
            case Read.Found<T> found -> found.etag();
            case Read.NotModified<T> notModified -> notModified.etag();
        };
    }

    private static String quoted(String etag) {
        return "\"" + etag + "\"";
    }

    // ---- the context

    @Test
    void theContextIsTheProjectCopyThatWinsAcrossTheLevelsWithItsFactsAndItsEntity() {
        EffectiveArtifactAnswer answer = found(service.artifact(ID, ArtifactKind.GLOSSARY, "spec-kitty-core", 0, null));

        assertThat(answer.winner().packId()).isEqualTo("proj");
        assertThat(answer.winner().level()).isEqualTo(PackLevel.PROJECT);
        assertThat(answer.artifact().localId()).isEqualTo("spec-kitty-core");
        assertThat(answer.artifact().packId()).isEqualTo(new PackIdentifier("proj"));
        assertThat(answer.governance()).isEmpty();
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint()).isEqualTo(CLOSURE);
    }

    @ParameterizedTest
    @CsvSource({"spec-kitty-core, ACTIVE, SELECTED", "qa, INACTIVE, NOT_SELECTED", "legacy, EXCLUDED, DEACTIVATED"})
    void aContextIsAnsweredWhateverItsStateWithItsReason(String contextId, State state, Reason reason) {
        EffectiveArtifactAnswer answer = found(service.artifact(ID, ArtifactKind.GLOSSARY, contextId, 0, null));

        assertThat(answer.winner().state()).isEqualTo(state);
        assertThat(answer.winner().reason()).isEqualTo(reason);
    }

    @Test
    void theContextsRouteHasATagOfItsOwnReadThatIsNotTheGenericReadsTag() {
        Read<EffectiveArtifactAnswer> read = service.artifact(ID, ArtifactKind.GLOSSARY, "spec-kitty-core", 0, null);
        String context = etag(read);

        assertThat(context)
                .isEqualTo(EffectiveTag.winner(
                                EffectiveTag.Read.GLOSSARY,
                                stored.revision(),
                                closure(),
                                new EffectiveTag.ArtifactQuery(ArtifactKind.GLOSSARY, "spec-kitty-core", 0))
                        .token());
        assertThat(etag(service.artifact(ID, "glossary", "spec-kitty-core", 0, null)))
                .as("the generic route answers another body for the same context, so it has another tag")
                .isNotEqualTo(context);
        assertThat(service.artifact(ID, ArtifactKind.GLOSSARY, "spec-kitty-core", 0, quoted(context)))
                .isEqualTo(new Read.NotModified<EffectiveArtifactAnswer>(context));
    }

    @Test
    void aContextNoPackOfTheClosureHoldsIsArtifactNotFoundNamingItWhateverIfNoneMatchSays() {
        for (String ifNoneMatch : new String[] {null, "*"}) {
            assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.GLOSSARY, "ghost", 0, ifNoneMatch))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                        assertThat(refusal.getMessage()).isEqualTo("glossary context `ghost` is not held");
                    });
        }
        assertThat(packs.effectiveAsks()).as("no entity is read").isEmpty();
    }

    @Test
    void anArtifactOfAnotherKindKeepsTheRefusalOfTheEffectiveReads() {
        assertThatThrownBy(() -> service.artifact(ID, ArtifactKind.SKILL, "ghost", 0, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage())
                                .isEqualTo("No pack of the activation's closure holds the skill ghost."));
    }

    // ---- the list

    @Test
    void theListIsTheContextWinnersUnderATagOfItsOwnReadAndNoStateIsResolved() {
        Read<EffectiveReadService.HeldWinners> read = service.heldWinners(ID, ArtifactKind.GLOSSARY, null);

        assertThat(found(read).winners()).containsExactly(QA_WINNER, CORE_WINNER);
        assertThat(packs.queries()).as("no in-force resolution").isEmpty();
        String list = etag(read);
        assertThat(list)
                .isEqualTo(EffectiveTag.winners(
                                EffectiveTag.Read.GLOSSARIES, ArtifactKind.GLOSSARY, stored.revision(), closure())
                        .token());
        assertThat(service.heldWinners(ID, ArtifactKind.GLOSSARY, quoted(list)))
                .isEqualTo(new Read.NotModified<EffectiveReadService.HeldWinners>(list));
    }

    // ---- the term

    @Test
    void aTermTheWinningContextHoldsIsAnsweredWithTheContextsFactsAndEntityFromOneSession() {
        EffectiveArtifactAnswer answer = found(service.glossaryTerm(ID, "spec-kitty-core", "mission", null));

        assertThat(answer.winner().ref()).isEqualTo(CORE);
        assertThat(answer.winner().packId()).isEqualTo("proj");
        assertThat(answer.winner().state()).isEqualTo(State.ACTIVE);
        assertThat(answer.winner().reason()).isEqualTo(Reason.SELECTED);
        assertThat(answer.artifact().localId()).isEqualTo("spec-kitty-core");
        assertThat(answer.governance()).isEmpty();
        assertThat(answer.sourceFingerprint()).isEqualTo(SOURCE);
        assertThat(answer.governanceFingerprint()).isEqualTo(CLOSURE);
        assertThat(packs.calls()).as("one session").isEqualTo(1);
        assertThat(packs.merges()).as("one merge").isEqualTo(1);
        assertThat(packs.effectiveAsks())
                .containsExactly(new FakeAvailablePacks.EffectiveAsk(
                        ArtifactKind.GLOSSARY,
                        "spec-kitty-core",
                        0,
                        packs.queries().getFirst()));
    }

    @Test
    void aTermCarriesItsContextsStateAndReasonWhateverTheyAre() {
        EffectiveArtifactAnswer answer = found(service.glossaryTerm(ID, "qa", "flaky-test", null));

        assertThat(answer.winner().ref()).isEqualTo(QA);
        assertThat(answer.winner().state()).isEqualTo(State.INACTIVE);
        assertThat(answer.winner().reason()).isEqualTo(Reason.NOT_SELECTED);
    }

    @Test
    void aTermOnlyACopyTheWinnerReplacedHoldsIsArtifactNotFoundNamingTheTermWhateverIfNoneMatchSays() {
        for (String ifNoneMatch : new String[] {null, "*"}) {
            assertThatThrownBy(() -> service.glossaryTerm(ID, "spec-kitty-core", "lane", ifNoneMatch))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                        assertThat(refusal.getMessage())
                                .isEqualTo("term `lane` is not held in glossary context `spec-kitty-core`");
                    });
        }
    }

    @Test
    void aTermOfAContextNoPackHoldsIsArtifactNotFoundNamingTheContextAndNoEntityIsRead() {
        assertThatThrownBy(() -> service.glossaryTerm(ID, "ghost", "mission", "*"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("glossary context `ghost` is not held");
                });
        assertThat(packs.effectiveAsks()).isEmpty();
    }

    @Test
    void theTermsTagNamesTheContextAndTheTermAndAMatchingIfNoneMatchIsNotModified() {
        String term = etag(service.glossaryTerm(ID, "spec-kitty-core", "mission", null));
        String context = etag(service.artifact(ID, ArtifactKind.GLOSSARY, "spec-kitty-core", 0, null));

        assertThat(term)
                .isEqualTo(EffectiveTag.term(
                                stored.revision(), closure(), new EffectiveTag.TermQuery("spec-kitty-core", "mission"))
                        .token())
                .isNotEqualTo(context);
        int resolved = packs.queries().size();

        assertThat(service.glossaryTerm(ID, "spec-kitty-core", "mission", quoted(term)))
                .isEqualTo(new Read.NotModified<EffectiveArtifactAnswer>(term));
        assertThat(packs.queries()).as("a 304 resolves no state").hasSize(resolved);
        assertThat(service.glossaryTerm(ID, "spec-kitty-core", "mission", quoted(context)))
                .as("the context's tag never matches its term")
                .isInstanceOf(Read.Found.class);
    }

    @Test
    void anUnknownActivationIsActivationNotFoundBeforeThePacksAreRead() {
        assertThatThrownBy(() -> service.glossaryTerm(new ActivationId("nope"), "spec-kitty-core", "mission", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aTermIsKindNotFoundWhenThePacksSliceListsNoGlossaryAndNothingIsRead() {
        packs.listedKinds(List.of(ArtifactKind.DIRECTIVE));

        assertThatThrownBy(() -> service.glossaryTerm(ID, "spec-kitty-core", "mission", null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("The service serves no artifact kind glossary.");
                });
        assertThat(packs.calls()).isZero();
    }

    @Test
    void aWinnerThatHoldsNoTermsIsAFaultAndNotANotFound() {
        packs.winners(ask ->
                Optional.of(new EffectiveArtifactRead(plain(ask.id()), "proj", PackLevel.PROJECT, Optional.empty())));

        assertThatThrownBy(() -> service.glossaryTerm(ID, "spec-kitty-core", "mission", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The read of the term mission of the glossary context spec-kitty-core under activation a-1:"
                        + " the winner glossary_context:spec-kitty-core holds no terms.");
    }

    /** A glossary context as the session reads it: a kernel artifact that holds the given terms. */
    private static CharterArtifact context(String id, Set<String> terms) {
        return new HeldContext(id, terms);
    }

    /** An artifact of the kind that holds no terms: what a broken session would answer. */
    private static CharterArtifact plain(String id) {
        return new Plain(id);
    }

    private static class Plain implements CharterArtifact {

        private final String id;

        Plain(String id) {
            this.id = id;
        }

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.GLOSSARY;
        }

        @Override
        public PackIdentifier packId() {
            return new PackIdentifier(id.equals("spec-kitty-core") ? "proj" : "core");
        }

        @Override
        public String localId() {
            return id;
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.of("1.0");
        }

        @Override
        public String label() {
            return id;
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }
    }

    private static final class HeldContext extends Plain implements TermHolder {

        private final Set<String> terms;

        HeldContext(String id, Set<String> terms) {
            super(id);
            this.terms = terms;
        }

        @Override
        public boolean holdsTerm(String termId) {
            return terms.contains(termId);
        }
    }
}
