package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.PlantedFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.MissionTypeFacts.SubstepFacts;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StepRules (#554, T031)")
class StepRulesTest {

    private static final String FILE = "missions/rx/rx.mission-type.yaml";

    private static SubstepFacts substep(String list, int step, String stepId, int index, String id) {
        return new SubstepFacts(list, step, stepId, index, id, Optional.empty());
    }

    private static List<Reason> judge(MissionTypeFacts... types) {
        return StepRules.SUBSTEP_ID_REPEATED.apply(facts(List.of(), List.of(), List.of(types), List.of()));
    }

    @Test
    @DisplayName("a substep id repeated in one step is one violation at its second occurrence, worded once")
    void aRepeatedSubstepIdIsOneFindingAtItsSecondOccurrence() {
        List<Reason> findings = judge(new MissionTypeFacts(
                FILE,
                "rx",
                List.of("a"),
                List.of(
                        substep("steps", 0, "a", 0, "run"),
                        substep("steps", 0, "a", 1, "check"),
                        substep("steps", 0, "a", 2, "run"),
                        substep("steps", 0, "a", 3, "run"))));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.DUPLICATE_STEP_ID);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.isFailing()).isTrue();
            assertThat(finding.rule()).isEqualTo("duplicate_step_id");
            assertThat(finding.location()).isEqualTo(Location.in(FILE).at("/steps/0/substeps/2/id"));
            assertThat(finding.detail())
                    .isEqualTo(FILE + ": substep id 'run' is declared more than once in the step 'a'");
        });
    }

    @Test
    @DisplayName("one id in two steps, or in a step and an on-demand step of one index, is no repeat")
    void oneIdInTwoStepsIsNoRepeat() {
        assertThat(judge(new MissionTypeFacts(
                        FILE,
                        "rx",
                        List.of("a", "b", "c"),
                        List.of(
                                substep("steps", 0, "a", 0, "run"),
                                substep("steps", 1, "b", 0, "run"),
                                substep("on_demand_steps", 0, "c", 0, "run")))))
                .isEmpty();
    }

    @Test
    @DisplayName("each repeated id of each type is reported, the types in file order")
    void eachTypeIsJudged() {
        List<Reason> findings = judge(
                new MissionTypeFacts(
                        "missions/a/a.mission-type.yaml",
                        "a",
                        List.of("s"),
                        List.of(substep("steps", 0, "s", 0, "x"), substep("steps", 0, "s", 1, "x"))),
                new MissionTypeFacts(
                        "missions/b/b.mission-type.yaml",
                        "b",
                        List.of("t"),
                        List.of(
                                substep("on_demand_steps", 0, "t", 0, "y"),
                                substep("on_demand_steps", 0, "t", 1, "y"),
                                substep("on_demand_steps", 0, "t", 2, "z"),
                                substep("on_demand_steps", 0, "t", 3, "z"))));

        assertThat(findings)
                .extracting(Reason::location)
                .containsExactly(
                        Location.in("missions/a/a.mission-type.yaml").at("/steps/0/substeps/1/id"),
                        Location.in("missions/b/b.mission-type.yaml").at("/on_demand_steps/0/substeps/1/id"),
                        Location.in("missions/b/b.mission-type.yaml").at("/on_demand_steps/0/substeps/3/id"));
    }

    @Test
    @DisplayName("the holder's ruleset is its rules")
    void theRulesetIsItsRules() {
        MissionTypeFacts repeats = new MissionTypeFacts(
                FILE, "rx", List.of("a"), List.of(substep("steps", 0, "a", 0, "r"), substep("steps", 0, "a", 1, "r")));
        var contents = facts(List.of(), List.of(), List.of(repeats), List.of());

        assertThat(StepRules.RULES.apply(contents)).isEqualTo(StepRules.SUBSTEP_ID_REPEATED.apply(contents));
        assertThat(StepRules.RULES.apply(contents)).hasSize(1);
    }

    static List<Planted> planted() {
        return PlantedFixtures.all();
    }

    @ParameterizedTest
    @MethodSource("planted")
    @DisplayName("over every planted and example pack, the rule finds what today's validate mode finds")
    void theRuleFindsWhatValidateModeFinds(Planted planted) {
        assertThat(StepRules.RULES.apply(planted.contents()))
                .containsExactlyInAnyOrderElementsOf(
                        PlantedFixtures.policyFindings(planted, Set.of(PackCodes.DUPLICATE_STEP_ID)));
    }

    static List<Planted> expecting() {
        return PlantedFixtures.expecting(Set.of(PackCodes.DUPLICATE_STEP_ID.value()));
    }

    @ParameterizedTest
    @MethodSource("expecting")
    @DisplayName("a plant's expected.json is the oracle of the rule's findings")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        assertThat(StepRules.RULES.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyElementsOf(planted.expected().orElseThrow().stream()
                        .filter(expected -> PackCodes.DUPLICATE_STEP_ID.value().equals(expected.code()))
                        .toList());
    }
}
