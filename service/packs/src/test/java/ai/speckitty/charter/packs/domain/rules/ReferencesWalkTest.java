package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.GraphCheck;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import ai.speckitty.charter.packs.domain.graph.PackNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@code REFERENCES} on a pack-graph walk (#639, ADR-0082 slice 4, operator ruling 01M8DT4CGSQZBPJCC6K9CN3ZX4): the
 * packs walk hands the check every member the scope selects, in the graph's order, and the phase gate is the stop rule.
 * The closure holds a delegation fault ({@code alpha}, {@code delta}), a reference fault ({@code beta}) and an
 * enhancement target fault ({@code gamma}), each in a member of its own.
 */
class ReferencesWalkTest {

    private static final String BASE = "base";

    /** Each member's facts, by configured identifier, read as the walk hands the member over. */
    private final Map<String, PackCompositionFacts> facts = Map.of(
            BASE,
            facts(List.of(), List.of(), List.of()),
            "alpha",
            facts(List.of(), List.of(), List.of(delegating("base::tactic:alpha_missing"))),
            "beta",
            facts(List.of("base::tactic:beta_missing"), List.of(), List.of()),
            "gamma",
            facts(
                    List.of(),
                    List.of(edge("step_enhancement:x", RelationType.ENHANCES, "base::action:M/gamma_missing")),
                    List.of()),
            "delta",
            facts(List.of(), List.of(), List.of(delegating("base::tactic:delta_missing"))));

    private final List<String> read = new ArrayList<>();

    /** A member's facts, read as the walk hands the member over; each read is recorded. */
    private PackCompositionFacts factsOf(PackUnderResolution member) {
        read.add(member.configured().id().value());
        return facts.get(member.configured().id().value());
    }

    @Test
    void theFirstCheckWithAFaultAnswersMemberByMemberInTheGraphsOrder() {
        List<PackUnderResolution> closure = closure(BASE, "alpha", "beta", "gamma", "delta");

        List<ClosureFault> faults = PackRulesets.references(closure, this::factsOf);

        assertThat(faults)
                .as("the delegations answer; the reference and enhancement target checks do not run")
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.PACK_UNREADABLE, RefusalCode.PACK_UNREADABLE);
        assertThat(faults.getFirst().detail()).contains("tactic:alpha_missing");
        assertThat(faults.get(1).detail()).contains("tactic:delta_missing");
        assertThat(read)
                .as("each member's facts are read once, as the walk hands it over, and only in the phase that ran")
                .containsExactly(BASE, "alpha", "beta", "gamma", "delta");
    }

    @Test
    void theOrderOfFindingsIsTheOrderTheClosureIsGivenIn() {
        List<PackUnderResolution> closure = closure(BASE, "delta", "gamma", "beta", "alpha");

        assertThat(PackRulesets.references(closure, this::factsOf))
                .extracting(ClosureFault::detail)
                .satisfiesExactly(
                        first -> assertThat(first).contains("tactic:delta_missing"),
                        second -> assertThat(second).contains("tactic:alpha_missing"));
    }

    @Test
    void aMemberOutsideTheSelectionIsNotJudged() {
        List<PackUnderResolution> closure = closure(BASE, "alpha", "beta", "gamma", "delta");

        assertThat(run(closure, "alpha", "delta"))
                .as("with the delegating members unselected, the references check answers")
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(fault.detail()).contains("tactic:beta_missing");
                });
        assertThat(run(closure, "alpha", "beta", "delta"))
                .as("with the referring member unselected too, the enhancement target check answers")
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(fault.detail()).contains("action:M/gamma_missing");
                });
        read.clear();
        assertThat(run(closure, "alpha", "beta", "gamma", "delta"))
                .as("no selected member has a fault")
                .isEmpty();
        assertThat(read).as("an unselected member's facts are never read").containsOnly(BASE);
    }

    @Test
    void aClosureThatPassesHasNoFault() {
        assertThat(PackRulesets.references(closure(BASE), this::factsOf)).isEmpty();
        assertThat(PackRulesets.references(List.of(), this::factsOf)).isEmpty();
    }

    /** The check over the closure's graph, every member a root, every member but the unselected ones selected. */
    private List<ClosureFault> run(List<PackUnderResolution> closure, String... unselected) {
        Set<String> excluded = Set.of(unselected);
        GraphCheck check = PackRulesets.referencesCheck(closure, this::factsOf);
        PackGraph graph = PackGraph.of(
                closure.stream()
                        .map(member -> new PackNode(member.configured(), member.descriptor()))
                        .toList(),
                pack -> {
                    throw new AssertionError("the packs walk reads no pack's facts: " + pack.value());
                });
        Scope scope = new Scope(
                closure.stream().map(member -> member.configured().id()).toList(),
                node -> node instanceof PackNode pack
                        && !excluded.contains(pack.pack().value()));
        return check.run(graph, scope).faults();
    }

    /** The closure in the order given: {@code base} holds the mission type, its step and a tactic; the rest use it. */
    private static List<PackUnderResolution> closure(String... names) {
        return Arrays.stream(names)
                .map(name -> name.equals(BASE)
                        ? pack(BASE, List.of(), "mission_type:M", "action:M/s", "tactic:T")
                        : pack(name, List.of(BASE)))
                .toList();
    }

    private static PackUnderResolution pack(String name, List<String> dependsOn, String... held) {
        PackDescriptor descriptor = new PackDescriptor(
                FixtureUlids.of(name),
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                dependsOn.stream()
                        .map(other -> new PackDependency(FixtureUlids.of(other), ">=0"))
                        .toList());
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG),
                descriptor,
                List.of(),
                Arrays.asList(held));
    }

    private static PackCompositionFacts facts(
            List<String> references,
            List<Relationship> edges,
            List<SubstepDelegationRule.QualifiedCandidate> delegations) {
        return new PackCompositionFacts(
                List.of(),
                List.of(),
                List.of(),
                references,
                MutabilityPolicySource.empty(),
                List.of(),
                edges,
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                delegations);
    }

    private static SubstepDelegationRule.QualifiedCandidate delegating(String reference) {
        return new SubstepDelegationRule.QualifiedCandidate(
                "missions/rx/mission.yaml", "rx", "implement", "delegate", PackQualifiedReference.parse(reference));
    }

    private static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return Relationship.of(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }
}
