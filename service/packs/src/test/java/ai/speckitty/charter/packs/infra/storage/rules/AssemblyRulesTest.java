package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The store half's faults placed and stated once (#554, WP12, R2 row 19): the plain functions of {@link AssemblyRules}. */
@DisplayName("AssemblyRules: the store half's own faults (#554, WP12)")
class AssemblyRulesTest {

    @Test
    @DisplayName("a declared path resolves beside the file that declares it, . and .. resolved")
    void aDeclaredPathResolvesBesideItsFile() {
        assertThat(AssemblyRules.sibling("toolguides/a.toolguide.yaml", "a.guide.md"))
                .isEqualTo("toolguides/a.guide.md");
        assertThat(AssemblyRules.sibling("missions/x/x.mission-type.yaml", "./steps/../steps/a.prompt.md"))
                .isEqualTo("missions/x/steps/a.prompt.md");
        assertThat(AssemblyRules.sibling("a.yaml", "b.md")).isEqualTo("b.md");
        assertThat(AssemblyRules.sibling("a.yaml", "../out.md")).isEqualTo("../out.md");
        assertThat(AssemblyRules.sibling("d/a.yaml", "../../out.md")).isEqualTo("../out.md");
    }

    /**
     * {@link AssemblyRules#sameFault} of the two, after asserting that the index {@link AssemblyRules.Stated} answers the
     * same for the one violation it holds (#648): it copies the candidate dispatch of {@code sameFault}, so a mapping
     * added to one and not the other shows here, at the first fixture that exercises it.
     */
    private static boolean agreed(PackElementViolation violation, Reason reason) {
        boolean same = AssemblyRules.sameFault(violation, reason);
        assertThat(new AssemblyRules.Stated(List.of(reason)).states(violation))
                .as("the index agrees with sameFault for %s against %s", violation, reason)
                .isEqualTo(same);
        return same;
    }

    private static Reason stated(String file, ReasonCode code, String detail) {
        return new Reason(code.value(), Origin.NONE, Evaluation.FAIL, code, Location.in(file), detail);
    }

    @Test
    @DisplayName("a companion fault is the reader's fault at the companion, or its 'missing' at the artifact; nothing"
            + " else at the artifact is the same fault")
    void aCompanionFaultIsTheSameFaultOnlyByItsMapping() {
        PackElementViolation broken = PackElementViolation.wholeFile(
                "toolguides/a.toolguide.yaml", StorageCodes.COMPANION, "a.guide.md: structure: const violated");
        PackElementViolation missing = PackElementViolation.wholeFile(
                "toolguides/a.toolguide.yaml", StorageCodes.COMPANION, "a.guide.md: missing");

        assertThat(agreed(broken, stated("toolguides/a.guide.md", PackCodes.ARTIFACT_SCHEMA_ERROR, "bad")))
                .isTrue();
        assertThat(agreed(broken, stated("toolguides/a.toolguide.yaml", PackCodes.ARTIFACT_SCHEMA_ERROR, "other")))
                .as("another fault of the artifact")
                .isFalse();
        assertThat(agreed(
                        missing,
                        stated(
                                "toolguides/a.toolguide.yaml",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "toolguides/a.toolguide.yaml: names a.guide.md: missing")))
                .isTrue();
        assertThat(agreed(missing, stated("toolguides/a.guide.md", StorageCodes.COMPANION, "something")))
                .as("a missing companion has no fault of its own at its file")
                .isFalse();
        assertThat(agreed(
                        missing,
                        stated(
                                "toolguides/a.toolguide.yaml",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "toolguides/a.toolguide.yaml: the companion a.guide.md does not exist")))
                .as("the reader's 'does not exist', naming the companion as the artifact declares it")
                .isTrue();
        assertThat(agreed(
                        missing,
                        stated(
                                "toolguides/a.toolguide.yaml",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "toolguides/a.toolguide.yaml: names b.guide.md: missing")))
                .as("another companion missing is another fault")
                .isFalse();
        assertThat(agreed(
                        missing,
                        stated(
                                "toolguides/b.toolguide.yaml",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "toolguides/b.toolguide.yaml: names a.guide.md: missing")))
                .as("the same companion missing at another artifact is another fault")
                .isFalse();
    }

    @Test
    @DisplayName("any other store fault is the same fault only at its file, with its code, in words that end with its")
    void anyOtherFaultIsTheSameFaultByFileCodeAndWords() {
        PackElementViolation breach = PackElementViolation.wholeFile(
                "presets/dup.yaml", PackCodes.ARTIFACT_SCHEMA_ERROR, "Constraint breached: twice");

        assertThat(agreed(
                        breach,
                        stated(
                                "presets/dup.yaml",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "presets/dup.yaml: Constraint breached: twice")))
                .isTrue();
        assertThat(agreed(breach, stated("presets/dup.yaml", PackCodes.ARTIFACT_SCHEMA_ERROR, "id is not the stem")))
                .as("another fault of the same file")
                .isFalse();
        assertThat(agreed(breach, stated("presets/dup.yaml", StorageCodes.UNREADABLE, "Constraint breached: twice")))
                .isFalse();
        Reason placed = new Reason(
                PackCodes.ARTIFACT_SCHEMA_ERROR.value(),
                Origin.NONE,
                Evaluation.FAIL,
                PackCodes.ARTIFACT_SCHEMA_ERROR,
                new Location("presets/dup.yaml", "/steps/3"),
                "presets/dup.yaml: Constraint breached: twice");
        assertThat(agreed(breach, placed))
                .as("a fault with no place is stated by a violation at any place in its file")
                .isTrue();
        assertThat(agreed(
                        new PackElementViolation(
                                "presets/dup.yaml",
                                "/steps/4",
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                "Constraint breached: twice"),
                        placed))
                .as("a fault at another place is another fault")
                .isFalse();
    }

    @Test
    @DisplayName("a fragment's read fault the store states stands unless the reader states the fragment missing")
    void aFragmentFaultIsTheSameFaultOnlyWhenMissing() {
        String enhancement = "missions/x/enhancements/e.step-enhancement.yaml";
        PackElementViolation blank = PackElementViolation.wholeFile(
                enhancement, PackCodes.PROMPT_FRAGMENT_INVALID, "e.prompt-fragment.md: blank");
        PackElementViolation gone = PackElementViolation.wholeFile(
                enhancement, PackCodes.PROMPT_FRAGMENT_INVALID, "e.prompt-fragment.md: missing");
        Reason twice = stated(
                enhancement,
                PackCodes.PROMPT_FRAGMENT_INVALID,
                "slot s:x is named more than once by e.prompt-fragment.md");
        Reason absent = stated(
                enhancement,
                PackCodes.PROMPT_FRAGMENT_INVALID,
                "the companion missions/x/enhancements/e.prompt-fragment.md does not exist");

        assertThat(agreed(blank, twice)).isFalse();
        assertThat(agreed(blank, absent)).isFalse();
        assertThat(agreed(gone, absent)).isTrue();
        assertThat(AssemblyRules.unreadFragments(
                        List.of(stated(enhancement, PackCodes.PROMPT_FRAGMENT_INVALID, "e.prompt-fragment.md: blank"))))
                .containsExactly("missions/x/enhancements/e.prompt-fragment.md");
        assertThat(AssemblyRules.unreadFragments(List.of(stated(
                        enhancement, PackCodes.PROMPT_FRAGMENT_INVALID, "slot s:x is named more than once: twice"))))
                .as("a reason whose words before the colon are no one-word path names no fragment")
                .isEmpty();
    }

    @Test
    @DisplayName("review cycle 2: a companion fault needs the reader's companion or schema code at the companion; a"
            + " mission type breach is the reader's step fault only for a step id standing twice")
    void theMappingsMatchTheirCodeAndSubject() {
        PackElementViolation broken = PackElementViolation.wholeFile(
                "toolguides/a.toolguide.yaml", StorageCodes.COMPANION, "a.guide.md: structure: const violated");
        String type = "missions/x/x.mission-type.yaml";
        PackElementViolation stepTwice = PackElementViolation.wholeFile(
                type, PackCodes.ARTIFACT_SCHEMA_ERROR, "Constraint breached: " + AssemblyRules.STEP_STANDS_ONCE);
        PackElementViolation keyTwice = PackElementViolation.wholeFile(
                type,
                PackCodes.ARTIFACT_SCHEMA_ERROR,
                "Constraint breached: An artifact key stands once in a mission type's expected outputs.");
        Reason step = stated(
                type,
                PackCodes.ARTIFACT_SCHEMA_ERROR,
                type + ": step 'a' stands twice across steps and on_demand_steps");
        Reason names = stated(
                type, PackCodes.ARTIFACT_SCHEMA_ERROR, type + ": names templates/t.md (named by the type): missing");

        assertThat(agreed(broken, stated("toolguides/a.guide.md", StorageCodes.RETIRED, "retired")))
                .isFalse();
        assertThat(agreed(stepTwice, step)).isTrue();
        assertThat(agreed(stepTwice, names)).isFalse();
        assertThat(agreed(keyTwice, step)).isFalse();
        assertThat(agreed(keyTwice, names)).isFalse();
    }
}
