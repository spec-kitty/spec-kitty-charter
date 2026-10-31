package ai.speckitty.charter.missions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The mission-type aggregate (ADR-0044): each invariant has an accepted neighbour and a refused twin that differs from
 * it in the one property the invariant guards.
 */
@DisplayName("Mission type (#194)")
class MissionTypeTest {

    private static final PackIdentifier PACK = new PackIdentifier("built-in");

    private static MissionStep step(String id) {
        return new MissionStep(
                id,
                "Step " + id,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ExpectedArtifact artifact(String key) {
        return new ExpectedArtifact(key, ArtifactClass.OUTPUT, "spec.md", false, Optional.empty());
    }

    private static ExpectedInput input(String key) {
        return new ExpectedInput(key, "spec.md", true);
    }

    private static MissionStep withArtifacts(List<ExpectedInput> inputs, List<ExpectedArtifact> outputs) {
        return new MissionStep(
                "plan",
                "Plan",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                inputs,
                outputs,
                List.of());
    }

    private static MissionType.Builder research() {
        return MissionType.builder()
                .id(new MissionTypeIdentifier("research"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Research")
                .description("Conduct research.")
                .step(step("scoping"))
                .step(step("methodology"));
    }

    @Test
    @DisplayName("a mission type is an artifact of its own kind, with its steps in order and no relationship")
    void aMissionTypeIsAnArtifactOfItsKind() {
        MissionType type = research().onDemandStep(step("retrospect")).build();

        assertThat(type.kind()).isEqualTo(ArtifactKind.MISSION_TYPE);
        assertThat(type.urn()).isEqualTo(Urn.parse("mission_type:research").orElseThrow());
        assertThat(type.label()).isEqualTo("Research");
        assertThat(type.steps()).extracting(MissionStep::id).containsExactly("scoping", "methodology");
        assertThat(type.onDemandSteps()).extracting(MissionStep::id).containsExactly("retrospect");
        assertThat(type.step("retrospect")).isPresent();
        assertThat(type.step("absent")).isEmpty();
        assertThat(type.relationships()).isEmpty();
    }

    @Test
    @DisplayName("references are kept in order on the type and its steps, and mint no relationship")
    void referencesAreKeptNeverMinted() {
        MissionReference directive =
                new MissionReference(ArtifactKind.DIRECTIVE, "DIRECTIVE_010", Optional.empty(), Optional.empty());
        MissionReference tactic =
                new MissionReference(ArtifactKind.TACTIC, "premortem", Optional.of("always"), Optional.empty());
        MissionStep scoping = new MissionStep(
                "scoping",
                "Scoping",
                Optional.empty(),
                Optional.of("researcher"),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(tactic, directive));

        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("research"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Research")
                .description("Conduct research.")
                .reference(tactic)
                .reference(directive)
                .step(scoping)
                .build();

        assertThat(type.references()).containsExactly(tactic, directive);
        assertThat(type.steps().get(0).references()).containsExactly(tactic, directive);
        assertThat(type.relationships()).isEmpty();
    }

    @Test
    @DisplayName("a type without steps is refused; one step is accepted")
    void aTypeNeedsAStep() {
        MissionType.Builder none = MissionType.builder()
                .id(new MissionTypeIdentifier("plan"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Plan")
                .description("Plan a change.");

        assertThatThrownBy(none::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("step");
        assertThatCode(() -> none.step(step("specify")).build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a step id that stands in the sequence and on demand is refused")
    void stepIdsAreUniqueAcrossBothLists() {
        assertThatCode(() -> research().onDemandStep(step("retrospect")).build())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> research().onDemandStep(step("scoping")).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("step id stands once");
        assertThatThrownBy(() -> research().step(step("scoping")).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("step id stands once");
    }

    @Test
    @DisplayName("a step closes over its inputs and outputs: preconditions, then postconditions")
    void aStepHasInputsAndOutputs() {
        MissionStep plan = withArtifacts(List.of(input("input.spec.main")), List.of(artifact("output.plan.main")));

        assertThat(plan.expectedInputArtifacts())
                .extracting(ExpectedInput::artifactKey)
                .containsExactly("input.spec.main");
        assertThat(plan.expectedOutputArtifacts())
                .extracting(ExpectedArtifact::artifactKey)
                .containsExactly("output.plan.main");
    }

    @Test
    @DisplayName("an output may be of any class, the mission's input document included; an input has no class")
    void anOutputMayBeTheMissionsInputDocument() {
        ExpectedArtifact spec = new ExpectedArtifact(
                "input.spec.main", ArtifactClass.INPUT, "spec.md", true, Optional.of("templates/s.md"));

        assertThat(withArtifacts(List.of(), List.of(spec)).expectedOutputArtifacts())
                .containsExactly(spec);
        assertThat(research().expectedOutputArtifact(spec).build().expectedOutputArtifacts())
                .containsExactly(spec);
    }

    @Test
    @DisplayName("an artifact key twice in one list is refused; the same key in two lists is accepted")
    void artifactKeysAreUniqueWithinOneList() {
        assertThatThrownBy(() -> research()
                        .expectedOutputArtifact(artifact("output.spec.main"))
                        .expectedOutputArtifact(artifact("output.spec.main"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> withArtifacts(
                        List.of(), List.of(artifact("output.tasks.per_wp"), artifact("output.tasks.per_wp"))))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> withArtifacts(List.of(input("input.spec.main"), input("input.spec.main")), List.of()))
                .isInstanceOf(ConstraintBreachException.class);

        assertThatCode(() -> research()
                        .expectedOutputArtifact(artifact("output.tasks.per_wp"))
                        .step(withArtifacts(List.of(), List.of(artifact("output.tasks.per_wp"))))
                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blank title, description or content is refused")
    void blankTextsAreRefused() {
        assertThatThrownBy(() -> research().title(" ").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> research().description("").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> research().content(" ").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> research().schemaVersion(" ").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatCode(() -> research().content("You are a research agent.").build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a step with a blank title or description is refused; the filled neighbour is accepted")
    void blankStepTextsAreRefused() {
        assertThatThrownBy(() -> new MissionStep(
                        "plan",
                        " ",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new MissionStep(
                        "plan",
                        "Plan",
                        Optional.of(""),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatCode(() -> new MissionStep(
                        "plan",
                        "Plan",
                        Optional.of("Plan it."),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "the id ''{0}'' is refused")
    @ValueSource(strings = {"9research", "Research", "re_search", "research-", "a/b", "../x", ""})
    void badIdsAreRefused(String id) {
        assertThatThrownBy(() -> new MissionTypeIdentifier(id)).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> step(id)).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a letter-first kebab id is accepted for a type and a step")
    void kebabIdsAreAccepted() {
        assertThatCode(() -> new MissionTypeIdentifier("software-dev")).doesNotThrowAnyException();
        assertThatCode(() -> step("tasks-outline")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("two mission types are equal by identity")
    void equalityIsIdentity() {
        assertThat(research().build()).isEqualTo(research().title("Other").build());
        assertThat(research().build()).hasSameHashCodeAs(research().build());
    }
}
