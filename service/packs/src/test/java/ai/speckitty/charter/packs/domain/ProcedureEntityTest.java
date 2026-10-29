package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Procedure entity")
class ProcedureEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static ProcedureStep step(String title) {
        return new ProcedureStep(title, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Procedure.Builder valid(String id) {
        return Procedure.builder()
                .id(new ProcedureIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("What " + id + " is.")
                .entryCondition("ready")
                .exitCondition("done")
                .step(step("Do it"));
    }

    // ---- accepted ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the builder yields a valid entity carrying everything it was given")
    void builderBuildsValidEntity() {
        var operatorStep = new ProcedureStep(
                "Ask", Optional.of("Confirm."), Optional.of("operator"), Optional.of("go"), Optional.of("stop"));
        var antiPattern = new AntiPatternEntry(
                "Rushing", Optional.of("Do not rush."), Optional.of("skip review"), Optional.of("review first"));
        var reference =
                new ProcedureReference(ArtifactKind.TACTIC, "review-first", Optional.of("always"), Optional.empty());

        Procedure procedure = valid("p-1")
                .step(operatorStep)
                .antiPattern(antiPattern)
                .reference(reference)
                .notes("note")
                .appliesToLanguage("java")
                .build();

        assertThat(procedure.id()).isEqualTo(new ProcedureIdentifier("p-1"));
        assertThat(procedure.kind()).isEqualTo(ArtifactKind.PROCEDURE);
        assertThat(procedure.localId()).isEqualTo("p-1");
        assertThat(procedure.packId()).isEqualTo(PACK);
        assertThat(procedure.schemaVersion()).contains("1.0");
        assertThat(procedure.label()).isEqualTo("Title of p-1").isEqualTo(procedure.title());
        assertThat(procedure.description()).contains("What p-1 is.");
        assertThat(procedure.entryCondition()).isEqualTo("ready");
        assertThat(procedure.exitCondition()).isEqualTo("done");
        assertThat(procedure.steps()).containsExactly(step("Do it"), operatorStep);
        assertThat(procedure.antiPatterns()).containsExactly(antiPattern);
        assertThat(procedure.references()).containsExactly(reference);
        assertThat(procedure.notes()).contains("note");
        assertThat(procedure.appliesToLanguages()).containsExactly("java");
    }

    @Test
    @DisplayName("optional parts default to empty")
    void optionalPartsDefaultToEmpty() {
        Procedure procedure = valid("p").build();

        assertThat(procedure.notes()).isEmpty();
        assertThat(procedure.antiPatterns()).isEmpty();
        assertThat(procedure.references()).isEmpty();
        assertThat(procedure.appliesToLanguages()).isEmpty();
    }

    @Test
    @DisplayName("relationships() is empty even when the procedure carries references (research D3, #189)")
    void relationshipsAreNeverMinted() {
        Procedure procedure = valid("p")
                .reference(new ProcedureReference(ArtifactKind.TACTIC, "t", Optional.empty(), Optional.empty()))
                .reference(new ProcedureReference(
                        ArtifactKind.DIRECTIVE, "DIRECTIVE_001", Optional.of("w"), Optional.of("r")))
                .build();

        assertThat(procedure.references()).hasSize(2);
        assertThat(procedure.relationships()).isEmpty();
    }

    @Test
    @DisplayName("same identity with different fields is equal; different identity is not")
    void entityIdentity() {
        Procedure one = valid("p-1").notes("one").build();
        Procedure same = valid("p-1").title("Another").build();
        Procedure other = valid("p-2").build();

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    }

    @Test
    @DisplayName("a step role round-trips and an absent role stays absent")
    void aRoleRoundTripsAndAnAbsentRoleStaysAbsent() {
        var withRole =
                new ProcedureStep("s", Optional.empty(), Optional.of("operator"), Optional.empty(), Optional.empty());

        assertThat(withRole.role()).contains("operator");
        assertThat(step("s").role()).isEmpty();
    }

    // ---- refused ----------------------------------------------------------------------------------

    static Stream<Arguments> breaches() {
        return Stream.of(
                Arguments.of(
                        "blank title",
                        (UnaryOperator<Procedure.Builder>) b -> b.title(" "),
                        "A procedure needs a title that is not blank."),
                Arguments.of(
                        "blank description",
                        (UnaryOperator<Procedure.Builder>) b -> b.description(""),
                        "A procedure needs a description that is not blank."),
                Arguments.of(
                        "blank entry condition",
                        (UnaryOperator<Procedure.Builder>) b -> b.entryCondition(" "),
                        "A procedure needs an entry condition that is not blank."),
                Arguments.of(
                        "blank exit condition",
                        (UnaryOperator<Procedure.Builder>) b -> b.exitCondition(" "),
                        "A procedure needs an exit condition that is not blank."),
                Arguments.of(
                        "blank schema version",
                        (UnaryOperator<Procedure.Builder>) b -> b.schemaVersion(""),
                        "A procedure needs a schema version that is not blank."));
    }

    @ParameterizedTest(name = "{0} is refused")
    @MethodSource("breaches")
    void eachRuleBreachIsRefused(String name, UnaryOperator<Procedure.Builder> breach, String message) {
        assertThat(valid("p").build()).isNotNull();
        assertThatThrownBy(() -> breach.apply(valid("p")).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(message);
    }

    @Test
    @DisplayName("a procedure without steps is refused")
    void noStepsIsRefused() {
        var builder = Procedure.builder()
                .id(new ProcedureIdentifier("p"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("t")
                .description("d")
                .entryCondition("e")
                .exitCondition("x");

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A procedure needs at least one step.");
    }

    @Test
    @DisplayName("a builder missing its identity is refused")
    void missingIdentityIsRefused() {
        assertThatThrownBy(() -> Procedure.builder().build()).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "procedure id \"{0}\" is accepted")
    @ValueSource(strings = {"a", "demo-procedure", "qa-2-review"})
    void kebabIdentifiersAreAccepted(String id) {
        assertThat(new ProcedureIdentifier(id).value()).isEqualTo(id);
    }

    @ParameterizedTest(name = "procedure id \"{0}\" is refused")
    @ValueSource(strings = {"", "Demo", "demo_procedure", "-demo", "demo-", "demo--procedure"})
    void nonKebabIdentifiersAreRefused(String id) {
        assertThatThrownBy(() -> new ProcedureIdentifier(id))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A procedure identifier must match");
    }

    @Test
    @DisplayName("a blank step title is refused")
    void aBlankStepTitleIsRefused() {
        assertThatThrownBy(() -> step(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A procedure step needs a title that is not blank.");
    }

    @ParameterizedTest(name = "role \"{0}\" is accepted")
    @ValueSource(strings = {"operator", "reviewer", "qa-lead"})
    void roleTokensAreAccepted(String role) {
        assertThat(new ProcedureStep("s", Optional.empty(), Optional.of(role), Optional.empty(), Optional.empty())
                        .role())
                .contains(role);
    }

    @ParameterizedTest(name = "role \"{0}\" is refused")
    @ValueSource(strings = {"", "Operator", "1st", "qa_lead", "-op"})
    void nonRoleTokensAreRefused(String role) {
        assertThatThrownBy(() ->
                        new ProcedureStep("s", Optional.empty(), Optional.of(role), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A procedure step role must match");
    }

    @Test
    @DisplayName("an anti-pattern entry needs a title; its other parts may be absent")
    void anAntiPatternEntryNeedsATitle() {
        assertThat(new AntiPatternEntry("bare", Optional.empty(), Optional.empty(), Optional.empty()).content())
                .isEmpty();
        assertThatThrownBy(() -> new AntiPatternEntry(" ", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("An anti-pattern entry needs a title that is not blank.");
    }

    @Test
    @DisplayName("a procedure reference needs an id")
    void aReferenceNeedsAnId() {
        assertThat(new ProcedureReference(ArtifactKind.TACTIC, "t", Optional.empty(), Optional.empty()).id())
                .isEqualTo("t");
        assertThatThrownBy(() -> new ProcedureReference(ArtifactKind.TACTIC, "", Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A procedure reference needs an id that is not blank.");
    }
}
