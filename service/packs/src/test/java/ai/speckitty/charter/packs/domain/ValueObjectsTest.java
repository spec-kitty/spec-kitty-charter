package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value objects and identifiers")
class ValueObjectsTest {

    @Test
    @DisplayName("tactic references with equal components are equal; one differing component is not")
    void tacticReferenceHasValueEquality() {
        var reference = new TacticReference("n", "directive", "D1", "when");

        assertThat(reference).isEqualTo(new TacticReference("n", "directive", "D1", "when"));
        assertThat(reference).isNotEqualTo(new TacticReference("n", "directive", "D2", "when"));
    }

    @Test
    @DisplayName("tactic steps compare their lists by order")
    void tacticStepListsAreOrderSensitive() {
        var first = new TacticStep("t", Optional.empty(), List.of("a", "b"), List.of());

        assertThat(first).isEqualTo(new TacticStep("t", Optional.empty(), List.of("a", "b"), List.of()));
        assertThat(first).isNotEqualTo(new TacticStep("t", Optional.empty(), List.of("b", "a"), List.of()));
    }

    @Test
    @DisplayName("a tactic step with a blank title is rejected")
    void blankStepTitleIsRejected() {
        assertThatThrownBy(() -> new TacticStep(" ", Optional.empty(), List.of(), List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A tactic step needs a title that is not blank.");
    }

    @Test
    @DisplayName("identifiers are equal by their wrapped value")
    void identifiersHaveValueEquality() {
        assertThat(new PackIdentifier("p")).isEqualTo(new PackIdentifier("p"));
        assertThat(new TacticIdentifier("t"))
                .isEqualTo(new TacticIdentifier("t"))
                .isNotEqualTo(new TacticIdentifier("u"));
    }

    @Test
    @DisplayName("blank identifiers are rejected")
    void blankIdentifiersAreRejected() {
        assertThatThrownBy(() -> new PackIdentifier(""))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A pack identifier must not be blank.");
        assertThatThrownBy(() -> new TacticIdentifier(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A tactic identifier must not be blank.");
    }

    @Test
    @DisplayName("pack paths are equal by both components")
    void packPathsHaveValueEquality() {
        var paths = new PackPaths(Optional.of("charter.yaml"), Optional.empty());

        assertThat(paths).isEqualTo(new PackPaths(Optional.of("charter.yaml"), Optional.empty()));
        assertThat(paths).isNotEqualTo(PackPaths.none());
    }
}
