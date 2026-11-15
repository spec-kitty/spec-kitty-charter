package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SubstepDelegationRule} over plain facts, and the compose's check of a candidate written with another pack's
 * name (FR-005).
 */
@DisplayName("SubstepDelegationRule (FR-005)")
class SubstepDelegationRuleTest {

    private static final String FILE = "missions/rx/rx.mission-type.yaml";

    private static MissionTypeFacts type(String id, String kind, String... candidates) {
        return new MissionTypeFacts(
                FILE,
                id,
                List.of("a"),
                List.of(new MissionTypeFacts.SubstepFacts(
                        "steps",
                        0,
                        "a",
                        0,
                        "run",
                        Optional.of(new MissionTypeFacts.Delegation(kind, List.of(candidates))))));
    }

    private static MissionStepLinkRule.Edge scope(String source, String target) {
        return new MissionStepLinkRule.Edge("action.graph.yaml", source, target, "scope");
    }

    @Test
    @DisplayName("each candidate has at most one fault, in written order, at its place")
    void eachCandidateHasAtMostOneFault() {
        List<SubstepDelegationRule.Fault> faults = SubstepDelegationRule.judge(
                "qa",
                List.of(type("rx", "tactic", "missing", "held", "scoped")),
                Set.of("tactic:held", "tactic:scoped"),
                List.of(scope("action:rx/a", "tactic:scoped")));

        assertThat(faults)
                .extracting(
                        SubstepDelegationRule.Fault::code,
                        fault -> fault.place().getLast())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(SubstepDelegationRule.UNRESOLVED, "0"),
                        org.assertj.core.groups.Tuple.tuple(SubstepDelegationRule.UNSCOPED, "1"));
    }

    @Test
    @DisplayName("an edge of another relation, or from another step's action, does not scope a candidate")
    void onlyTheScopeEdgeFromTheStepsActionCounts() {
        List<SubstepDelegationRule.Fault> faults = SubstepDelegationRule.judge(
                "qa",
                List.of(type("rx", "tactic", "held")),
                Set.of("tactic:held"),
                List.of(
                        new MissionStepLinkRule.Edge("action.graph.yaml", "action:rx/a", "tactic:held", "applies"),
                        scope("action:rx/b", "tactic:held"),
                        scope("other::action:rx/a", "tactic:held")));

        assertThat(faults)
                .extracting(SubstepDelegationRule.Fault::code)
                .containsExactly(SubstepDelegationRule.UNSCOPED);
    }

    @Test
    @DisplayName("a type whose id a second file repeats is judged once")
    void aRepeatedTypeIsJudgedOnce() {
        assertThat(SubstepDelegationRule.judge(
                        "qa",
                        List.of(type("rx", "tactic", "missing"), type("rx", "tactic", "missing")),
                        Set.of(),
                        List.of()))
                .hasSize(1);
    }

    @Test
    @DisplayName("a kind that is no scope target of an action is the schema's fault, not the rule's")
    void aKindTheSchemaRefusesIsNotJudged() {
        assertThat(SubstepDelegationRule.judge("qa", List.of(type("rx", "template", "missing")), Set.of(), List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("only a candidate written with another pack's name is left to the compose")
    void theQualifiedCandidatesAreTheOtherPacks() {
        assertThat(SubstepDelegationRule.qualifiedCandidates(
                        "qa",
                        List.of(type("rx", "directive", "DIRECTIVE_001", "qa::DIRECTIVE_003", "base::DIRECTIVE_009"))))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.reference().toString()).isEqualTo("base::directive:DIRECTIVE_009");
                    assertThat(candidate.file()).isEqualTo(FILE);
                    assertThat(candidate.stepId()).isEqualTo("a");
                    assertThat(candidate.substepId()).isEqualTo("run");
                });
    }

    // ---- the compose ------------------------------------------------------------------------------------------------

    private static PackUnderResolution pack(String id, String name, List<PackDependency> dependsOn, String... held) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        dependsOn),
                List.of(),
                List.of(held));
    }

    private static List<SubstepDelegationRule.QualifiedCandidate> candidates(String candidate) {
        return SubstepDelegationRule.qualifiedCandidates("top", List.of(type("rx", "tactic", candidate)));
    }

    @Test
    @DisplayName("a candidate the named pack of the closure does not hold refuses the compose, by its own code")
    void aCandidateTheNamedPackDoesNotHoldIsRefused() {
        PackUnderResolution base = pack("01BASE", "base", List.of(), "tactic:other");
        PackUnderResolution top = pack("01TOP", "top", List.of(new PackDependency("01BASE", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.refuseUnresolvedDelegations(
                        List.of(base, top), "01TOP", candidates("base::tdd")))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage())
                            .contains("delegates to base::tactic:tdd")
                            .contains(SubstepDelegationRule.UNRESOLVED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo(FILE);
                        assertThat(violation.code()).isEqualTo(SubstepDelegationRule.UNRESOLVED);
                    });
                    assertThat(refusal.packs()).extracting(pack -> pack.name()).containsExactly("top", "base");
                });
    }

    @Test
    @DisplayName(
            "control: the candidate the named pack holds passes, and a pack outside the closure is the reference's")
    void aHeldCandidateAndAPackOutsideTheClosureAreNotRefusedHere() {
        PackUnderResolution base = pack("01BASE", "base", List.of(), "tactic:tdd");
        PackUnderResolution top = pack("01TOP", "top", List.of(new PackDependency("01BASE", ">=1.0")));
        PackUnderResolution loose = pack("01LOOSE", "top", List.of());
        PackUnderResolution other = pack("01OTHER", "other", List.of());

        assertThatCode(() -> PackCompositionResolver.refuseUnresolvedDelegations(
                        List.of(base, top), "01TOP", candidates("base::tdd")))
                .doesNotThrowAnyException();
        assertThatCode(() -> PackCompositionResolver.refuseUnresolvedDelegations(
                        List.of(other, loose), "01LOOSE", candidates("other::tdd")))
                .doesNotThrowAnyException();
        assertThatCode(() -> PackCompositionResolver.refuseUnresolvedDelegations(
                        List.of(loose), "01LOOSE", candidates("nowhere::tdd")))
                .doesNotThrowAnyException();
    }
}
