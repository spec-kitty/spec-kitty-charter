package ai.speckitty.charter.profiles.infra.activations;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveArtifactAnswer;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Suggestion;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.profiles.application.EdgeWords;
import ai.speckitty.charter.profiles.application.EffectiveProfileRead;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the activations slice, over its application ring's service: the winner narrowed to the profile, the
 * facts of the winner and of each entry as the profiles slice's own types, the entries in force and the suggestions out
 * of force merged in URN order, and the service's refusals and its {@code 304} unchanged.
 */
class ActivationsEffectiveProfilesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final ArtifactRef P = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "P");
    private static final ArtifactRef PARENT = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "parent");
    private static final ArtifactRef D1 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D1");
    private static final ArtifactRef D2 = new ArtifactRef(ArtifactKind.DIRECTIVE, "D2");
    private static final ArtifactRef S = new ArtifactRef(ArtifactKind.STYLEGUIDE, "S");

    private final EffectiveReadService service = mock(EffectiveReadService.class);
    private final ActivationsEffectiveProfiles adapter = new ActivationsEffectiveProfiles(service);

    private static EffectiveArtifact fact(
            ArtifactRef ref, State state, Reason reason, Optional<Suggestion> suggestion) {
        return new EffectiveArtifact(
                ref, "main", PackLevel.ORG, state, false, Optional.empty(), Policy.OPEN, reason, suggestion);
    }

    private static EffectiveArtifact active(ArtifactRef ref) {
        return fact(ref, State.ACTIVE, Reason.SELECTED, Optional.empty());
    }

    private static EffectiveArtifactAnswer.Entry entry(
            EffectiveArtifact artifact, GovernanceFacts.Step step, RelationType relation, ArtifactRef via, int hops) {
        return new EffectiveArtifactAnswer.Entry(artifact, step, relation, via, hops, Optional.empty());
    }

    private static EffectiveArtifactAnswer answer(
            EffectiveArtifact winner,
            CharterArtifact artifact,
            Optional<EffectiveArtifactAnswer.Governance> governance) {
        StoredActivation stored = StoredActivation.of(
                ID,
                ActivationDocument.builder()
                        .packs(List.of(PackRef.of("main")))
                        .build()
                        .document());
        return new EffectiveArtifactAnswer(stored, "sha256:source", "sha256:closure", winner, artifact, governance);
    }

    private static EffectiveArtifactAnswer.Governance governance() {
        return new EffectiveArtifactAnswer.Governance(
                List.of(
                        entry(active(S), GovernanceFacts.Step.REQUIRES, RelationType.REQUIRES, P, 1),
                        entry(active(PARENT), GovernanceFacts.Step.LINEAGE, RelationType.SPECIALIZES_FROM, P, 1),
                        entry(
                                fact(D1, State.ACTIVE, Reason.REQUIRED, Optional.empty()),
                                GovernanceFacts.Step.REQUIRES,
                                RelationType.REQUIRES,
                                PARENT,
                                2)),
                List.of(new EffectiveArtifactAnswer.Suggested(
                        fact(
                                D2,
                                State.INACTIVE,
                                Reason.NOT_SELECTED,
                                Optional.of(new Suggestion(Optional.of("when it helps"), Optional.of("why it helps")))),
                        P,
                        1)));
    }

    private void answers(EffectiveArtifactAnswer answer, String etag) {
        when(service.artifact(eq(ID), eq(ArtifactKind.AGENT_PROFILE), eq("P"), eq(1), any()))
                .thenReturn(new Read.Found<>(answer, etag));
        when(service.listedKinds()).thenReturn(List.of(ArtifactKind.DIRECTIVE, ArtifactKind.AGENT_PROFILE));
    }

    private EffectiveProfileRead found(String ifNoneMatch) {
        EffectiveProfiles.Outcome outcome = adapter.read("a-1", "P", 1, ifNoneMatch);
        assertThat(outcome).isInstanceOf(EffectiveProfiles.Outcome.Found.class);
        return ((EffectiveProfiles.Outcome.Found) outcome).read();
    }

    @Test
    void theProfileIsTheWinnersEntityAndTheFactsAreTheWinnersAndTheFingerprintsAndTagAreTheReads() {
        answers(answer(active(P), leanProfile("main", "P"), Optional.of(governance())), "tag-1");

        EffectiveProfiles.Outcome outcome = adapter.read("a-1", "P", 1, "\"x\"");

        assertThat(outcome).isInstanceOfSatisfying(EffectiveProfiles.Outcome.Found.class, found -> {
            assertThat(found.etag()).isEqualTo("tag-1");
            EffectiveProfileRead read = found.read();
            assertThat(read.profile().localId()).isEqualTo("P");
            assertThat(read.activationId()).isEqualTo("a-1");
            assertThat(read.revision()).startsWith("sha256:");
            assertThat(read.sourceFingerprint()).isEqualTo("sha256:source");
            assertThat(read.governanceFingerprint()).isEqualTo("sha256:closure");
            assertThat(read.winner().urn().toString()).isEqualTo("agent_profile:P");
            assertThat(read.winner().pack().value()).isEqualTo("main");
            assertThat(read.winner().level()).isEqualTo(PackLevel.ORG);
            assertThat(read.winner().state()).isEqualTo("active");
            assertThat(read.winner().policy()).isEqualTo("open");
            assertThat(read.winner().reason()).isEqualTo("selected");
            assertThat(read.winner().required()).isFalse();
            assertThat(read.linkableKinds()).containsExactly(ArtifactKind.DIRECTIVE, ArtifactKind.AGENT_PROFILE);
        });
        verify(service).artifact(ID, ArtifactKind.AGENT_PROFILE, "P", 1, "\"x\"");
    }

    @Test
    void theEntriesInForceAndTheSuggestionsOutOfForceAreOneListInUrnOrderEachWithItsWalkFacts() {
        answers(answer(active(P), leanProfile("main", "P"), Optional.of(governance())), "t");

        List<EffectiveProfileRead.Entry> entries = found(null).governance();

        assertThat(entries)
                .extracting(entry -> entry.facts().urn().toString())
                .containsExactly("agent_profile:parent", "directive:D1", "directive:D2", "styleguide:S");
        assertThat(entries)
                .extracting(entry -> entry.step().token())
                .containsExactly("lineage", "requires", "suggests", "requires");
        assertThat(entries)
                .extracting(entry -> entry.relation().token())
                .containsExactly("specializes_from", "requires", "suggests", "requires");
        assertThat(entries).extracting(EffectiveProfileRead.Entry::hops).containsExactly(1, 2, 1, 1);
        assertThat(entries)
                .extracting(entry -> entry.via().toString())
                .containsExactly("agent_profile:P", "agent_profile:parent", "agent_profile:P", "agent_profile:P");
    }

    @Test
    void anEntryCarriesTheStateReasonAndSuggestionTheActivationGivesItAndNoneOfTheEdgesOfItsOwn() {
        answers(answer(active(P), leanProfile("main", "P"), Optional.of(governance())), "t");

        List<EffectiveProfileRead.Entry> entries = found(null).governance();

        EffectiveProfileRead.Facts required = entries.get(1).facts();
        assertThat(required.state()).isEqualTo("active");
        assertThat(required.reason()).isEqualTo("required");
        assertThat(required.suggestion()).isEmpty();
        EffectiveProfileRead.Facts suggested = entries.get(2).facts();
        assertThat(suggested.state()).isEqualTo("inactive");
        assertThat(suggested.reason()).isEqualTo("not_selected");
        assertThat(suggested.pack().value()).isEqualTo("main");
        assertThat(suggested.suggestion())
                .contains(new EdgeWords(Optional.of("when it helps"), Optional.of("why it helps")));
    }

    @Test
    void aSuggestsEntryInForceCarriesTheWordsOfItsEdgeAsItsSuggestion() {
        EffectiveArtifactAnswer.Entry suggestsInForce = new EffectiveArtifactAnswer.Entry(
                active(D1),
                GovernanceFacts.Step.SUGGESTS,
                RelationType.SUGGESTS,
                P,
                1,
                Optional.of(new Suggestion(Optional.of("when it applies"), Optional.of("why it applies"))));
        answers(
                answer(
                        active(P),
                        leanProfile("main", "P"),
                        Optional.of(new EffectiveArtifactAnswer.Governance(
                                List.of(
                                        suggestsInForce,
                                        entry(active(S), GovernanceFacts.Step.REQUIRES, RelationType.REQUIRES, P, 1)),
                                List.of()))),
                "t");

        List<EffectiveProfileRead.Entry> entries = found(null).governance();

        assertThat(entries.getFirst().facts().state()).as("in force").isEqualTo("active");
        assertThat(entries.getFirst().facts().suggestion())
                .contains(new EdgeWords(Optional.of("when it applies"), Optional.of("why it applies")));
        assertThat(entries.get(1).facts().suggestion()).isEmpty();
    }

    @Test
    void aSatisfiedByIsTheUrnOfTheOverride() {
        EffectiveArtifact required = new EffectiveArtifact(
                D1,
                "main",
                PackLevel.ORG,
                State.INACTIVE,
                true,
                Optional.of(D2),
                Policy.LOCKED,
                Reason.OVERRIDDEN,
                Optional.empty());
        answers(
                answer(
                        active(P),
                        leanProfile("main", "P"),
                        Optional.of(new EffectiveArtifactAnswer.Governance(
                                List.of(entry(required, GovernanceFacts.Step.REQUIRES, RelationType.REQUIRES, P, 1)),
                                List.of()))),
                "t");

        EffectiveProfileRead.Facts facts = found(null).governance().getFirst().facts();

        assertThat(facts.required()).isTrue();
        assertThat(facts.policy()).isEqualTo("locked");
        assertThat(facts.satisfiedBy().orElseThrow().toString()).isEqualTo("directive:D2");
    }

    @Test
    void aProfileWithNothingToWalkHasNoEntries() {
        answers(
                answer(
                        active(P),
                        leanProfile("main", "P"),
                        Optional.of(new EffectiveArtifactAnswer.Governance(List.of(), List.of()))),
                "t");

        assertThat(found(null).governance()).isEmpty();
    }

    @Test
    void aNotModifiedIsTheServicesTagAndAskedNothingElse() {
        when(service.artifact(eq(ID), eq(ArtifactKind.AGENT_PROFILE), eq("P"), eq(0), eq("\"t\"")))
                .thenReturn(new Read.NotModified<>("t"));

        assertThat(adapter.read("a-1", "P", 0, "\"t\"")).isEqualTo(new EffectiveProfiles.Outcome.NotModified("t"));
    }

    @Test
    void aRefusalOfTheServiceIsTheAdaptersRefusalUnchanged() {
        CharterRefusal refusal = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "nope");
        when(service.artifact(any(), any(ArtifactKind.class), any(), eq(0), any()))
                .thenThrow(refusal);

        assertThatThrownBy(() -> adapter.read("a-1", "P", 0, null)).isSameAs(refusal);
    }

    @Test
    void aProfileAnswerWithoutGovernanceOrWithAnotherKindOfEntityIsAFault() {
        answers(answer(active(P), leanProfile("main", "P"), Optional.empty()), "t");
        assertThatThrownBy(() -> adapter.read("a-1", "P", 1, null)).isInstanceOf(RuntimeException.class);

        CharterArtifact notAProfile = new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.DIRECTIVE;
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier("main");
            }

            @Override
            public String localId() {
                return "P";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "P";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
        answers(
                answer(
                        active(P),
                        notAProfile,
                        Optional.of(new EffectiveArtifactAnswer.Governance(List.of(), List.of()))),
                "t");
        assertThatThrownBy(() -> adapter.read("a-1", "P", 1, null)).isInstanceOf(ClassCastException.class);
    }

    @Test
    void everyStateReasonAndPolicyTheActivationsSliceSpellsIsOneTheContractSpells() {
        for (State state : State.values()) {
            assertThat(ai.speckitty.charter.api.model.EffectiveGovernanceEntry.StateEnum.fromValue(state.token()))
                    .isNotNull();
        }
        for (Reason reason : Reason.values()) {
            assertThat(ai.speckitty.charter.api.model.EffectiveGovernanceEntry.ReasonEnum.fromValue(reason.token()))
                    .isNotNull();
        }
        for (Policy policy : Policy.values()) {
            assertThat(ai.speckitty.charter.api.model.EffectiveGovernanceEntry.PolicyEnum.fromValue(policy.token()))
                    .isNotNull();
        }
    }

    @Test
    void theServiceIsRequired() {
        assertThatThrownBy(() -> new ActivationsEffectiveProfiles(null)).isInstanceOf(NullPointerException.class);
    }
}
