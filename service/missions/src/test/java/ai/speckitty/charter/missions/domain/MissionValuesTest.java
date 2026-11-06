package ai.speckitty.charter.missions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The mission values: each invariant with an accepted neighbour and a refused twin (ADR-0044 decision 3). */
@DisplayName("Mission values (#194)")
class MissionValuesTest {

    private static ExpectedArtifact artifact(String key, String path, Optional<String> template) {
        return new ExpectedArtifact(key, ArtifactClass.INPUT, path, true, template);
    }

    @ParameterizedTest(name = "the artifact key ''{0}'' is accepted")
    @ValueSource(strings = {"input.spec.main", "output.tasks.per_wp", "runtime.charter-lint.decay"})
    void dottedKeysAreAccepted(String key) {
        assertThatCode(() -> artifact(key, "spec.md", Optional.empty())).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "the artifact key ''{0}'' is refused")
    @ValueSource(strings = {"spec", "Input.spec", "1nput.spec", ".spec", "input.", ""})
    void undottedKeysAreRefused(String key) {
        assertThatThrownBy(() -> artifact(key, "spec.md", Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest(name = "the path ''{0}'' is refused")
    @ValueSource(strings = {"/spec.md", "../spec.md", "a/../../b", "", "..\\spec.md", "a\\b.md", "C:/spec.md"})
    void pathsThatLeaveAreRefused(String path) {
        assertThatThrownBy(() -> artifact("input.spec.main", path, Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> artifact("input.spec.main", "spec.md", Optional.of(path)))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a template path outside templates/ is refused, though it stays in the pack")
    void templatesLiveUnderTemplates() {
        assertThatThrownBy(() -> artifact("output.spec.main", "spec.md", Optional.of("steps/spec.md")))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("under templates/");
    }

    @Test
    @DisplayName("#299: a step's text breaks no markdown line rule when it is built, field by field")
    void stepTextsFollowTheMarkdownLineRules() {
        assertThatThrownBy(() -> new StepDocument(Optional.of("Two\nlines"), Optional.empty(), "", List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageEndingWith("A step document's title must be a single line");
        assertThatThrownBy(() -> new StepDocument(Optional.of("T"), Optional.empty(), "\ntext", List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageEndingWith("A step document's preamble must not start with a blank line");
        assertThatThrownBy(() -> new StepDocument(Optional.empty(), Optional.empty(), "---\nx", List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A step document's preamble must not start with a --- line");
        assertThatThrownBy(() -> new StepSection("Title ##", ""))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A step document's section title cannot be a heading's text");
        assertThatThrownBy(() -> new StepSection("S", "text\n\n# Heading"))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A step document's section content cannot stand inside a document");
        // The accepted neighbours, one per refusal above.
        assertThat(new StepDocument(Optional.of("Two lines"), Optional.empty(), "text", List.of()).preamble())
                .isEqualTo("text");
        assertThat(new StepDocument(Optional.empty(), Optional.of("Detail."), "---\nx", List.of()).preamble())
                .isEqualTo("---\nx");
        assertThat(new StepSection("Release #1", "text\n\n### Heading").content())
                .isEqualTo("text\n\n### Heading");
    }

    @Test
    @DisplayName("a step document's title is never blank")
    void stepDocumentTitlesAreNotBlank() {
        assertThatThrownBy(() -> new StepDocument(Optional.of(" "), Optional.empty(), "", List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("title is not blank");
    }

    @Test
    @DisplayName("a workspace glob and a template under templates/ are accepted")
    void relativePathsAreAccepted() {
        assertThatCode(() -> artifact("output.tasks.per_wp", "tasks/WP*.md", Optional.of("templates/tasks.md")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an expected input checks its key and path as an output does")
    void expectedInputsCheckKeyAndPath() {
        assertThatCode(() -> new ExpectedInput("input.spec.main", "spec.md", true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new ExpectedInput("spec", "spec.md", true))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new ExpectedInput("input.spec.main", "../spec.md", true))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("path conventions name at least one directory, none blank")
    void pathConventionsNameADirectory() {
        Optional<String> none = Optional.empty();

        assertThatThrownBy(() -> new PathConventions(none, none, none, none, none))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new PathConventions(Optional.of(" "), none, none, none, none))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatCode(() -> new PathConventions(none, none, none, none, Optional.of("data/")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a step's role is a lower-case token; an absent role stays absent")
    void stepRoles() {
        assertThatThrownBy(() -> new MissionStep(
                        "plan",
                        "Plan",
                        Optional.empty(),
                        Optional.of("Planner"),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThat(new MissionStep(
                                "plan",
                                "Plan",
                                Optional.empty(),
                                Optional.of("qa-strategist"),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                List.of())
                        .role())
                .contains("qa-strategist");
    }

    @Test
    @DisplayName("a step document keeps its sections in order")
    void stepDocumentsKeepOrder() {
        StepDocument document = new StepDocument(
                Optional.of("Plan"),
                Optional.empty(),
                "",
                List.of(new StepSection("B", "b"), new StepSection("A", "a")));

        assertThat(document.sections()).extracting(StepSection::title).containsExactly("B", "A");
        assertThat(ArtifactClass.fromToken("evidence")).contains(ArtifactClass.EVIDENCE);
        assertThat(ArtifactClass.fromToken("policy")).isEmpty();
    }

    @Test
    @DisplayName("a step description is at most 160 characters, counted as code points")
    void stepDescriptionsAreBounded() {
        assertThat(stepDescribedAs("x".repeat(MissionStep.DESCRIPTION_MAX_LENGTH))
                        .description())
                .hasValueSatisfying(text -> assertThat(text).hasSize(160));
        assertThat(stepDescribedAs("\uD83D\uDE80".repeat(160)).description()).isPresent();
        assertThatThrownBy(() -> stepDescribedAs("x".repeat(161)))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("at most 160 characters");
    }

    @Test
    @DisplayName("a step document's detailed description is never blank")
    void detailedDescriptionsAreNotBlank() {
        assertThat(new StepDocument(Optional.empty(), Optional.of("Longer."), "", List.of()).description())
                .contains("Longer.");
        assertThatThrownBy(() -> new StepDocument(Optional.empty(), Optional.of(" "), "", List.of()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a step's guidelines carry no detailed description; its prompt may")
    void guidelinesCarryNoDetailedDescription() {
        StepDocument detailed = new StepDocument(Optional.empty(), Optional.of("Longer."), "", List.of());
        StepDocument plain = new StepDocument(Optional.empty(), Optional.empty(), "", List.of());

        assertThatThrownBy(() -> stepWith(Optional.of(plain), Optional.of(detailed)))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("guidelines carry no detailed description");
        assertThat(stepWith(Optional.of(detailed), Optional.of(plain)).prompt()).contains(detailed);
    }

    private static MissionStep stepWith(Optional<StepDocument> prompt, Optional<StepDocument> guidelines) {
        return new MissionStep(
                "plan", "Plan", Optional.empty(), Optional.empty(), prompt, guidelines, List.of(), List.of());
    }

    private static MissionStep stepDescribedAs(String description) {
        return new MissionStep(
                "plan",
                "Plan",
                Optional.of(description),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of());
    }
}
