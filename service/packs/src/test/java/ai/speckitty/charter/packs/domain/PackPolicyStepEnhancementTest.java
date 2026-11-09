package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Companion;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Edge;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Enhancement;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Entry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link PackPolicy} chooses the one code of every step enhancement and fragment fault (FR-004 to FR-006, FR-011): the
 * finding {@code PROMPT_FRAGMENT_INVALID} at the fault's file in validate mode, and the violation of that kind at load.
 */
class PackPolicyStepEnhancementTest {

    private static final String YAML = "missions/software-dev/enhancements/brief.step-enhancement.yaml";
    private static final String FRAGMENT = "missions/software-dev/enhancements/brief.prompt-fragment.md";

    private static Enhancement brief() {
        return new Enhancement(
                YAML, "software-dev", "brief", List.of(new Entry("tone:style", "brief.prompt-fragment.md")));
    }

    private static Edge enhances(String id, String target) {
        return new Edge(RelationType.ENHANCES, "step_enhancement:" + id, target, Optional.of("base"));
    }

    private static StepEnhancementFacts valid() {
        return new StepEnhancementFacts(
                List.of(brief()),
                List.of(Companion.read(FRAGMENT, false, false, "Be brief.\n")),
                Set.of(FRAGMENT),
                Set.of(),
                List.of(enhances("brief", "action:software-dev/plan")));
    }

    /** One fixture per fault, each failing in exactly the one rule; the pair of the file and the rule is the answer. */
    private static StepEnhancementFacts failing(StepEnhancementFault fault) {
        StepEnhancementFacts valid = valid();
        return switch (fault) {
            case FRAGMENT_BLANK ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        List.of(Companion.read(FRAGMENT, false, true, "")),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case FRAGMENT_FRONTMATTER ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        List.of(Companion.read(FRAGMENT, true, false, "Be brief.\n")),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case FRAGMENT_HAS_MARKER ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        List.of(Companion.read(FRAGMENT, false, false, "<!-- a:start -->\n")),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case SLOT_REPEATED ->
                new StepEnhancementFacts(
                        List.of(new Enhancement(
                                YAML,
                                "software-dev",
                                "brief",
                                List.of(
                                        new Entry("tone:style", "brief.prompt-fragment.md"),
                                        new Entry("tone:style", "other.prompt-fragment.md")))),
                        List.of(
                                Companion.read(FRAGMENT, false, false, "Be brief.\n"),
                                Companion.read(
                                        "missions/software-dev/enhancements/other.prompt-fragment.md",
                                        false,
                                        false,
                                        "Be.\n")),
                        Set.of(FRAGMENT, "missions/software-dev/enhancements/other.prompt-fragment.md"),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case COMPANION_MISSING ->
                new StepEnhancementFacts(
                        valid.enhancements(), List.of(), Set.of(), valid.unparsedEnhancementFiles(), valid.edges());
            case COMPANION_SHARED ->
                new StepEnhancementFacts(
                        List.of(
                                brief(),
                                new Enhancement(
                                        "missions/software-dev/enhancements/z.step-enhancement.yaml",
                                        "software-dev",
                                        "z",
                                        List.of(new Entry("tone:style", "brief.prompt-fragment.md")))),
                        valid.companions(),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        List.of(
                                enhances("brief", "action:software-dev/plan"),
                                enhances("z", "action:software-dev/plan")));
            case COMPANION_ORPHAN ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        valid.companions(),
                        Set.of(FRAGMENT, "missions/software-dev/enhancements/stray.prompt-fragment.md"),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case EDGE_COUNT ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        valid.companions(),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        List.of());
            case EDGE_TARGET_NOT_STEP ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        valid.companions(),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        List.of(enhances("brief", "tactic:x")));
            case OVERRIDES_EDGE ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        valid.companions(),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        List.of(
                                enhances("brief", "action:software-dev/plan"),
                                new Edge(
                                        RelationType.OVERRIDES,
                                        "step_enhancement:brief",
                                        "mission_type:software-dev",
                                        Optional.empty())));
            case DIRECTORY_MISMATCH ->
                new StepEnhancementFacts(
                        valid.enhancements(),
                        valid.companions(),
                        valid.fragmentFiles(),
                        valid.unparsedEnhancementFiles(),
                        List.of(enhances("brief", "action:research/plan")));
            case STEM_REPEATED ->
                new StepEnhancementFacts(
                        List.of(
                                brief(),
                                new Enhancement(
                                        "missions/software-dev/zz/brief.step-enhancement.yaml",
                                        "software-dev",
                                        "brief",
                                        List.of(new Entry("tone:style", "brief.prompt-fragment.md")))),
                        List.of(
                                Companion.read(FRAGMENT, false, false, "Be brief.\n"),
                                Companion.read(
                                        "missions/software-dev/zz/brief.prompt-fragment.md", false, false, "Be.\n")),
                        Set.of(FRAGMENT, "missions/software-dev/zz/brief.prompt-fragment.md"),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
            case NO_FRAGMENTS ->
                new StepEnhancementFacts(
                        List.of(new Enhancement(YAML, "software-dev", "brief", List.of())),
                        List.of(),
                        Set.of(),
                        valid.unparsedEnhancementFiles(),
                        valid.edges());
        };
    }

    /** The file each fault belongs to in {@link #failing}: the fragment for its content and orphan faults. */
    private static String fileOf(StepEnhancementFault fault) {
        return switch (fault) {
            case FRAGMENT_BLANK, FRAGMENT_FRONTMATTER, FRAGMENT_HAS_MARKER -> FRAGMENT;
            case COMPANION_ORPHAN -> "missions/software-dev/enhancements/stray.prompt-fragment.md";
            case COMPANION_SHARED -> "missions/software-dev/enhancements/z.step-enhancement.yaml";
            case STEM_REPEATED -> "missions/software-dev/zz/brief.step-enhancement.yaml";
            default -> YAML;
        };
    }

    @Test
    void aValidPackAnswersNoFindingAndNoViolation() {
        assertThat(PackPolicy.stepEnhancementFindings("enhancer", valid(), true))
                .isEmpty();
        assertThat(PackPolicy.stepEnhancementViolations(valid())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(StepEnhancementFault.class)
    void everyFaultIsOneBlockingPromptFragmentInvalidFindingAtItsFile(StepEnhancementFault fault) {
        List<PackFinding> findings = PackPolicy.stepEnhancementFindings("enhancer", failing(fault), true);

        assertThat(findings).hasSize(1);
        PackFinding finding = findings.getFirst();
        assertThat(finding.code()).isEqualTo(PackFindingCode.PROMPT_FRAGMENT_INVALID);
        assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
        assertThat(finding.file()).isEqualTo(fileOf(fault));
        assertThat(finding.place()).isEmpty();
        assertThat(finding.detail()).startsWith("enhancer/" + fileOf(fault) + ": ");
    }

    @ParameterizedTest
    @EnumSource(StepEnhancementFault.class)
    void everyFaultIsOneViolationOfTheWireTokenAtItsFile(StepEnhancementFault fault) {
        List<PackElementViolation> violations = PackPolicy.stepEnhancementViolations(failing(fault));

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().file()).isEqualTo(fileOf(fault));
        assertThat(violations.getFirst().kind()).isEqualTo("prompt_fragment_invalid");
        assertThat(violations.getFirst().reason()).isNotBlank();
        assertThat(violations.getFirst().reason()).doesNotContain("enhancer/");
    }

    @Test
    void theViolationsKeepTheRulesOrderOfPathThenRule() {
        StepEnhancementFacts both = new StepEnhancementFacts(
                List.of(
                        new Enhancement(
                                "missions/software-dev/enhancements/b.step-enhancement.yaml",
                                "software-dev",
                                "b",
                                List.of(new Entry("tone:style", "b.prompt-fragment.md"))),
                        new Enhancement(
                                "missions/software-dev/enhancements/a.step-enhancement.yaml",
                                "software-dev",
                                "a",
                                List.of(new Entry("tone:style", "a.prompt-fragment.md")))),
                List.of(
                        Companion.read("missions/software-dev/enhancements/b.prompt-fragment.md", false, false, "B.\n"),
                        Companion.read(
                                "missions/software-dev/enhancements/a.prompt-fragment.md", false, false, "A.\n")),
                Set.of(
                        "missions/software-dev/enhancements/b.prompt-fragment.md",
                        "missions/software-dev/enhancements/a.prompt-fragment.md"),
                Set.of(),
                List.of());

        assertThat(PackPolicy.stepEnhancementViolations(both))
                .extracting(PackElementViolation::file)
                .containsExactly(
                        "missions/software-dev/enhancements/a.step-enhancement.yaml",
                        "missions/software-dev/enhancements/b.step-enhancement.yaml");
    }

    @Test
    void theCodeProjectsOntoTheRefusalCodeOfTheSameToken() {
        assertThat(PackFindingCode.PROMPT_FRAGMENT_INVALID.refusal()).isEqualTo(RefusalCode.PROMPT_FRAGMENT_INVALID);
        assertThat(PackPolicy.violationKind(PackFindingCode.PROMPT_FRAGMENT_INVALID))
                .isEqualTo(RefusalCode.PROMPT_FRAGMENT_INVALID.token());
    }

    @Test
    void aCodeWithoutAViolationKindIsRefused() {
        assertThatThrownBy(() -> PackPolicy.violationKind(PackFindingCode.PACK_UNREADABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PACK_UNREADABLE");
    }
}
