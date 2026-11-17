package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.PlantedFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ArtifactRules (#554, T031)")
class ArtifactRulesTest {

    private static List<Reason> judge(ArtifactFacts... artifacts) {
        return ArtifactRules.ID_REPEATED.apply(facts(List.of(artifacts), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName(
            "an id two files of one kind declare is one violation at the later file in path order, naming the first")
    void aRepeatedIdIsOneFindingAtTheLaterFile() {
        List<Reason> findings = judge(
                new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "same"),
                new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same"));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.DUPLICATE_ARTIFACT_ID);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.isFailing()).isTrue();
            assertThat(finding.rule()).isEqualTo("duplicate_artifact_id");
            assertThat(finding.location()).isEqualTo(Location.in("tactics/b.tactic.yaml"));
            assertThat(finding.detail())
                    .isEqualTo("tactics/b.tactic.yaml: tactic id 'same' is already declared in tactics/a.tactic.yaml");
        });
    }

    @Test
    @DisplayName("path order is pathlib's: a directory sorts before a sibling that extends its name")
    void pathOrderIsSegmentBySegment() {
        List<Reason> findings = judge(
                new ArtifactFacts("tactics-x/a.tactic.yaml", "tactic", "same"),
                new ArtifactFacts("tactics/z/a.tactic.yaml", "tactic", "same"));

        assertThat(findings).extracting(reason -> reason.location().file()).containsExactly("tactics-x/a.tactic.yaml");
    }

    @Test
    @DisplayName("every later file of an id is reported, each naming the first, in path order")
    void everyLaterFileIsReported() {
        List<Reason> findings = judge(
                new ArtifactFacts("tactics/c.tactic.yaml", "tactic", "same"),
                new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same"),
                new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "same"));

        assertThat(findings)
                .extracting(Reason::detail)
                .containsExactly(
                        "tactics/b.tactic.yaml: tactic id 'same' is already declared in tactics/a.tactic.yaml",
                        "tactics/c.tactic.yaml: tactic id 'same' is already declared in tactics/a.tactic.yaml");
    }

    @Test
    @DisplayName("one id in two kinds, or two ids in one kind, is no repeat")
    void anIdIsUniqueWithinItsKindOnly() {
        assertThat(judge(
                        new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same"),
                        new ArtifactFacts("directives/a.directive.yaml", "directive", "same"),
                        new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "other")))
                .isEmpty();
    }

    @Test
    @DisplayName("a repeated asset or step enhancement id is another rule's, so it is not reported here")
    void anAssetOrStepEnhancementIsJudgedElsewhere() {
        assertThat(judge(
                        new ArtifactFacts("assets/a.asset.yaml", "asset", "same"),
                        new ArtifactFacts("assets/b.asset.yaml", "asset", "same"),
                        new ArtifactFacts("step_enhancements/x/a.step-enhancement.yaml", "step_enhancement", "e"),
                        new ArtifactFacts("step_enhancements/x/b.step-enhancement.yaml", "step_enhancement", "e")))
                .isEmpty();
    }

    @Test
    @DisplayName("the holder's ruleset is its rules")
    void theRulesetIsItsRules() {
        var contents = facts(
                List.of(
                        new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same"),
                        new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "same")),
                List.of(),
                List.of(),
                List.of());

        assertThat(ArtifactRules.RULES.apply(contents)).hasSize(1).isEqualTo(ArtifactRules.ID_REPEATED.apply(contents));
    }

    static List<Planted> planted() {
        return PlantedFixtures.all();
    }

    @ParameterizedTest
    @MethodSource("planted")
    @DisplayName("over every planted and example pack, with the asset rule, the rule finds what validate mode finds")
    void theRuleFindsWhatValidateModeFinds(Planted planted) {
        // Today's artifact rule judges every kind; the asset sidecars' repeat is the asset rule's now (S2).
        List<Reason> findings = new ArrayList<>(ArtifactRules.RULES.apply(planted.contents()));
        findings.addAll(AssetRules.ID_REPEATED.apply(planted.contents()));

        assertThat(findings)
                .containsExactlyInAnyOrderElementsOf(
                        PlantedFixtures.policyFindings(planted, Set.of(PackCodes.DUPLICATE_ARTIFACT_ID)));
    }

    static List<Planted> expecting() {
        return PlantedFixtures.expecting(Set.of(PackCodes.DUPLICATE_ARTIFACT_ID.value()));
    }

    @ParameterizedTest
    @MethodSource("expecting")
    @DisplayName("a plant's expected.json is the oracle of the rule's findings at the files of the kinds it judges")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        // A repeated asset id is the asset rule's (S2): its expected finding is filtered out by its file's kind.
        Set<String> judgedElsewhere = planted.contents().artifacts().stream()
                .filter(artifact -> "asset".equals(artifact.kind()))
                .map(ArtifactFacts::file)
                .collect(Collectors.toSet());
        assertThat(ArtifactRules.RULES.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyElementsOf(planted.expected().orElseThrow().stream()
                        .filter(expected ->
                                PackCodes.DUPLICATE_ARTIFACT_ID.value().equals(expected.code()))
                        .filter(expected -> !judgedElsewhere.contains(expected.file()))
                        .toList());
    }
}
