package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.application.EffectivePromptQuery;
import ai.speckitty.charter.packs.application.GovernanceWalk;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ClosureViewSession#effectiveArtifact} (WP06, T031): the packs slice's winner read mapped into the activation
 * domain's {@link EffectiveArtifactRead} and {@link GovernanceFacts}, from the session's one view. The closure is merged
 * for real from hand-built packs; the entity of a winner is read from a counting source, so a test sees which pack was
 * asked and how often.
 */
class ClosureViewSessionArtifactTest {

    private static final Urn P = urn("agent_profile:P");
    private static final Urn D = urn("directive:D");
    private static final ArtifactRef P_REF = ref("agent_profile:P");
    private static final ArtifactRef D_REF = ref("directive:D");
    private static final ArtifactRef S_REF = ref("directive:S");

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static ArtifactRef ref(String text) {
        return ArtifactRef.of(urn(text)).orElseThrow();
    }

    private static InForceQuery selecting(ArtifactRef... refs) {
        return new InForceQuery(Set.of(refs), Set.of(), Optional.empty(), Map.of());
    }

    // ---- the winner

    @Test
    void theWinnerIsTheEntityOfThePackThatHoldsTheVersionInForceWithItsLevel() {
        Closure closure = new Closure()
                .pack("beta", PackLevel.BUILT_IN, List.of(), List.of("directive:D"))
                .pack("alpha", PackLevel.ORG, List.of("beta"), List.of("directive:D"));
        PacksSession session = closure.session();

        EffectiveArtifactRead read = session.effectiveArtifact(ArtifactKind.DIRECTIVE, "D", 0, selecting(D_REF))
                .orElseThrow();

        assertThat(read.packId()).as("the org copy replaces the built-in one").isEqualTo("alpha");
        assertThat(read.level()).isEqualTo(PackLevel.ORG);
        assertThat(read.artifact().localId()).isEqualTo("D");
        assertThat(read.artifact().packId()).isEqualTo(new PackIdentifier("alpha"));
        assertThat(read.governance()).as("only a profile has governance").isEmpty();
        assertThat(closure.reads).containsExactly("alpha:directive:D");
    }

    @Test
    void anArtifactNoPackHoldsAndAnIdThatIsNoIdAreEmptyAndNothingIsRead() {
        Closure closure = new Closure().pack("beta", PackLevel.BUILT_IN, List.of(), List.of("directive:D"));
        PacksSession session = closure.session();

        assertThat(session.effectiveArtifact(ArtifactKind.DIRECTIVE, "GHOST", 0, selecting(D_REF)))
                .isEmpty();
        assertThat(session.effectiveArtifact(ArtifactKind.TACTIC, "D", 0, selecting(D_REF)))
                .as("D is a directive")
                .isEmpty();
        assertThat(session.effectiveArtifact(ArtifactKind.DIRECTIVE, "not an id!", 0, selecting(D_REF)))
                .isEmpty();
        assertThat(closure.reads).isEmpty();
    }

    @Test
    void aRefusalOfTheEntityReadIsNotCaught() {
        Closure closure = new Closure().pack("beta", PackLevel.BUILT_IN, List.of(), List.of("directive:D"));
        closure.source = (pack, kind, id) -> {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
        };
        PacksSession session = closure.session();

        assertThatThrownBy(() -> session.effectiveArtifact(ArtifactKind.DIRECTIVE, "D", 0, selecting(D_REF)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    // ---- a profile's governance

    private Closure profile() {
        return new Closure()
                .pack(
                        "beta",
                        PackLevel.BUILT_IN,
                        List.of(),
                        List.of("agent_profile:P", "directive:D", "directive:S"),
                        relationship("agent_profile:P", RelationType.REQUIRES, "directive:D"),
                        relationship("agent_profile:P", RelationType.SUGGESTS, "directive:S"));
    }

    @Test
    void aProfilesGovernanceIsMappedWithStepRelationViaAndHopsAndTheSuggestionCandidates() {
        PacksSession session = profile().session();

        EffectiveArtifactRead read = session.effectiveArtifact(
                        ArtifactKind.AGENT_PROFILE, "P", 1, selecting(P_REF, D_REF))
                .orElseThrow();

        GovernanceFacts governance = read.governance().orElseThrow();
        assertThat(governance.profile()).isEqualTo(P_REF);
        assertThat(governance.entries())
                .containsExactly(new GovernanceFacts.Entry(
                        D_REF,
                        GovernanceFacts.Step.REQUIRES,
                        RelationType.REQUIRES,
                        P_REF,
                        1,
                        Optional.empty(),
                        Optional.empty()));
        assertThat(governance.suggestionCandidates())
                .containsExactly(new GovernanceFacts.Candidate(S_REF, P_REF, 1, Optional.empty(), Optional.empty()));
    }

    @Test
    void aProfileOutOfForceIsStillWalkedAndAnArtifactOutOfForceIsNotAnEntry() {
        PacksSession session = profile().session();

        EffectiveArtifactRead read = session.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 0, selecting())
                .orElseThrow();

        assertThat(read.governance().orElseThrow().entries())
                .as("D is not selected, so the predicate refuses it; the profile itself is exempt")
                .isEmpty();
        assertThat(read.artifact().localId()).isEqualTo("P");
    }

    @Test
    void theInForceResolutionOfTheReadIsTheOneThePacksSessionKeepsForTheSameQuery() {
        ClosureViewSession session = (ClosureViewSession) profile().session();
        InForceQuery query = selecting(P_REF, D_REF);

        session.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 0, query);

        assertThat(session.statesOf(query)).isSameAs(session.statesOf(query));
        assertThat(session.inForce(query).states()).containsKeys(P_REF, D_REF);
    }

    @Test
    void everyStepOfThePacksSlicesWalkHasItsOwnStepHereByAnExhaustiveSwitch() {
        for (GovernanceWalk.Step step : GovernanceWalk.Step.values()) {
            GovernanceFacts.Step mapped = ClosureViewSession.stepOf(step);

            assertThat(mapped.name()).as("the constants share their names").isEqualTo(step.name());
            assertThat(mapped.token()).isEqualTo(step.name().toLowerCase(java.util.Locale.ROOT));
        }
        assertThat(GovernanceFacts.Step.values()).hasSize(GovernanceWalk.Step.values().length);
    }

    @Test
    void anEntryThatIsNoArtifactIsDroppedAndAViaThatIsNoArtifactIsAFault() {
        Urn action = Urn.action("M", "s");
        ProfileGovernance.Governance walked = new ProfileGovernance.Governance(
                P,
                List.of(entry(D, GovernanceWalk.Step.REQUIRES, P), entry(action, GovernanceWalk.Step.VOCABULARY, P)),
                List.of());

        GovernanceFacts facts = ClosureViewSession.governanceOf(walked);

        assertThat(facts.entries()).extracting(GovernanceFacts.Entry::ref).containsExactly(D_REF);

        ProfileGovernance.Governance broken =
                new ProfileGovernance.Governance(P, List.of(entry(D, GovernanceWalk.Step.REQUIRES, action)), List.of());
        assertThatThrownBy(() -> ClosureViewSession.governanceOf(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action:M/s");
    }

    @Test
    void theWordsOfTheEdgeReachTheFactsOfEveryStepAndNotOnlyOfSuggests() {
        ProfileGovernance.Governance walked = new ProfileGovernance.Governance(
                P,
                List.of(
                        new ProfileGovernance.Entry(
                                D,
                                GovernanceWalk.Step.REQUIRES,
                                P,
                                1,
                                Optional.of("always"),
                                Optional.of("it is required"),
                                true,
                                Optional.empty()),
                        new ProfileGovernance.Entry(
                                urn("directive:S"),
                                GovernanceWalk.Step.SUGGESTS,
                                P,
                                1,
                                Optional.empty(),
                                Optional.empty(),
                                true,
                                Optional.empty())),
                List.of());

        GovernanceFacts facts = ClosureViewSession.governanceOf(walked);

        assertThat(facts.entries())
                .extracting(GovernanceFacts.Entry::when, GovernanceFacts.Entry::reason)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(Optional.of("always"), Optional.of("it is required")),
                        org.assertj.core.api.Assertions.tuple(Optional.empty(), Optional.empty()));
    }

    @Test
    void theKindsAClientCanReadAreTheListedKindsOfThePacksSliceAndIncludeTheAgentProfile() {
        PacksAvailability packs = new PacksAvailability(roots -> {
            throw new AssertionError("listing the kinds reads no pack");
        });

        assertThat(packs.listedKinds()).isEqualTo(ArtifactReadService.listed());
        assertThat(packs.listedKinds())
                .contains(ArtifactKind.DIRECTIVE, ArtifactKind.ASSET, ArtifactKind.SKILL, ArtifactKind.AGENT_PROFILE);
    }

    private static ProfileGovernance.Entry entry(Urn urn, GovernanceWalk.Step step, Urn via) {
        return new ProfileGovernance.Entry(
                urn, step, via, 1, Optional.empty(), Optional.empty(), true, Optional.empty());
    }

    private static Relationship relationship(String from, RelationType type, String to) {
        return new Relationship(urn(from), urn(to), type, Optional.empty(), Optional.empty());
    }

    /** A closure of hand-built packs, merged for real, whose winners are read from a source that records its reads. */
    // ---- the effective prompt (WP07): whether an artifact is in force, before the slot check, and the mapping

    @Test
    void anArtifactIsInForceExactlyWhenTheResolutionPutsItInForce() {
        PacksSession session = new Closure()
                .pack("beta", PackLevel.BUILT_IN, List.of(), List.of("directive:D", "directive:S"))
                .session();

        assertThat(session.isInForce(D_REF, selecting(D_REF))).as("selected").isTrue();
        assertThat(session.isInForce(S_REF, selecting(D_REF)))
                .as("held and not selected")
                .isFalse();
        assertThat(session.isInForce(ref("directive:GHOST"), selecting(D_REF)))
                .as("not held")
                .isFalse();
    }

    @Test
    void theEffectivePromptIsMappedAsTextWithItsPacksSlotsFragmentsAndDiagnostic() {
        EffectivePromptQuery.EffectivePrompt answer = new EffectivePromptQuery.EffectivePrompt(
                Payload.of("# Plan \u2014 legacy\n".getBytes(StandardCharsets.UTF_8)),
                Optional.of(new PackIdentifier("base")),
                List.of(
                        new EffectivePromptQuery.Slot(
                                new SlotId("spdd:reasons-block"),
                                List.of(new EffectivePromptQuery.Fragment(
                                        new PackIdentifier("base"),
                                        "base-pack",
                                        PackLevel.ORG,
                                        "spdd-plan",
                                        "Fill.\n"))),
                        new EffectivePromptQuery.Slot(new SlotId("team:checklist"), List.of())),
                List.of(new Diagnostic(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "slot spdd:reasons-block holds text")));

        EffectivePrompt prompt = ClosureViewSession.promptOf(answer);

        assertThat(prompt.prompt()).isEqualTo("# Plan \u2014 legacy\n");
        assertThat(prompt.promptPackId()).contains("base");
        assertThat(prompt.slots())
                .containsExactly(
                        new EffectivePrompt.Slot(
                                "spdd:reasons-block",
                                List.of(new EffectivePrompt.Fragment(
                                        "base", "base-pack", PackLevel.ORG, "spdd-plan", "Fill.\n"))),
                        new EffectivePrompt.Slot("team:checklist", List.of()));
        assertThat(prompt.diagnostics())
                .containsExactly(new EffectivePrompt.Diagnostic(
                        EffectivePrompt.Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "slot spdd:reasons-block holds text"));
        assertThat(ClosureViewSession.promptOf(EffectivePromptQuery.EffectivePrompt.NONE))
                .isEqualTo(EffectivePrompt.NONE);
    }

    @Test
    void aDiagnosticThePromptQueryNeverListsIsAFault() {
        EffectivePromptQuery.EffectivePrompt answer = new EffectivePromptQuery.EffectivePrompt(
                Payload.of("# Plan\n".getBytes(StandardCharsets.UTF_8)),
                Optional.of(new PackIdentifier("base")),
                List.of(),
                List.of(new Diagnostic(Diagnostic.Code.FILE_NOT_HELD, "notes.md")));

        assertThatThrownBy(() -> ClosureViewSession.promptOf(answer)).isInstanceOf(IllegalStateException.class);
    }

    private static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();
        private final List<String> reads = new ArrayList<>();
        private ClosureView.WinnerSource source = (pack, kind, id) -> {
            reads.add(pack.value() + ":" + kind.token() + ":" + id);
            return Optional.of(entity(pack, kind, id));
        };

        Closure pack(String name, PackLevel level, List<String> dependsOn, List<String> held, Relationship... edges) {
            String id = "id-" + name;
            PackDescriptor descriptor = new PackDescriptor(
                    id,
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependsOn.stream()
                            .map(other -> new PackDependency("id-" + other, ">=0"))
                            .toList());
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            MutabilityPolicySource policy = MutabilityPolicySource.empty();
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(), held, List.of(), policy, List.of(), Arrays.asList(edges), Map.of()));
            policies.put(id, policy);
            return this;
        }

        PacksSession session() {
            ClosureView view = new ClosureView(
                    List.of(),
                    Map.<ArtifactKind, Set<String>>of(),
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    new PerPackPolicy(policies),
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    "sha256:merged",
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(packs, facts, (pack, kind, id) -> source.read(pack, kind, id)),
                    "sha256:merged");
            return new ClosureViewSession(view, PacksAvailability.snapshotOf(view));
        }
    }

    private static CharterArtifact entity(PackIdentifier pack, ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return pack;
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
}
