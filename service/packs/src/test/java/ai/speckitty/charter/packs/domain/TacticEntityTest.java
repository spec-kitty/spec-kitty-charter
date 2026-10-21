package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tactic entity")
class TacticEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static Tactic.Builder valid(String id) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("Name of " + id)
                .step(new TacticStep("Do it", Optional.empty(), List.of(), List.of()));
    }

    @Test
    @DisplayName("the builder yields a valid entity carrying everything it was given")
    void builderBuildsValidEntity() {
        var reference = new TacticReference("ref", "directive", "DIRECTIVE_001", "always");
        Tactic tactic = valid("t-1")
                .purpose("why")
                .notes("note")
                .failureMode("rushing")
                .appliesToLanguage("java")
                .reference(reference)
                .build();

        assertThat(tactic.id()).isEqualTo(new TacticIdentifier("t-1"));
        assertThat(tactic.packId()).isEqualTo(PACK);
        assertThat(tactic.purpose()).contains("why");
        assertThat(tactic.notes()).contains("note");
        assertThat(tactic.failureModes()).containsExactly("rushing");
        assertThat(tactic.appliesToLanguages()).containsExactly("java");
        assertThat(tactic.references()).containsExactly(reference);
        assertThat(tactic.steps()).hasSize(1);
    }

    @Test
    @DisplayName("same identity with different fields is equal (entity identity, not value)")
    void sameIdentityDifferentFieldsAreEqual() {
        Tactic one = valid("t-1").purpose("one").failureMode("a").build();
        Tactic other = valid("t-1").name("Another name").purpose("other").build();

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("different identity with identical fields is not equal")
    void differentIdentityIsNotEqual() {
        Tactic one = valid("t-1").name("same").build();
        Tactic other = valid("t-2").name("same").build();

        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("steps keep authored order and duplicates")
    void stepsKeepOrderAndDuplicates() {
        var a = new TacticStep("A", Optional.empty(), List.of(), List.of());
        var b = new TacticStep("B", Optional.empty(), List.of(), List.of());
        Tactic tactic = Tactic.builder()
                .id(new TacticIdentifier("t"))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("n")
                .step(b)
                .step(a)
                .step(b)
                .build();

        assertThat(tactic.steps()).containsExactly(b, a, b);
    }

    @Test
    @DisplayName("a tactic without steps is rejected fail-fast with a full-sentence message")
    void noStepsIsRejected() {
        var builder = Tactic.builder()
                .id(new TacticIdentifier("t"))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("n");

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A tactic needs at least one step.");
    }

    @Test
    @DisplayName("a blank name is rejected")
    void blankNameIsRejected() {
        assertThatThrownBy(() -> valid("t").name(" ").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A tactic needs a name that is not blank.");
    }

    @Test
    @DisplayName("a builder missing its identity is rejected")
    void missingIdentityIsRejected() {
        assertThatThrownBy(() -> Tactic.builder().build()).isInstanceOf(NullPointerException.class);
    }
}
