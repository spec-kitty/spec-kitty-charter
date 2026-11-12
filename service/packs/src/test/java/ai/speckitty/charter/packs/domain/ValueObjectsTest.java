package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value objects and identifiers")
class ValueObjectsTest {

    @Test
    @DisplayName("tactic steps compare their lists by order")
    void tacticStepListsAreOrderSensitive() {
        var first = new TacticStep("t", Optional.empty(), List.of("a", "b"));

        assertThat(first).isEqualTo(new TacticStep("t", Optional.empty(), List.of("a", "b")));
        assertThat(first).isNotEqualTo(new TacticStep("t", Optional.empty(), List.of("b", "a")));
    }

    @Test
    @DisplayName("a tactic step with a blank title is rejected")
    void blankStepTitleIsRejected() {
        assertThatThrownBy(() -> new TacticStep(" ", Optional.empty(), List.of()))
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
        var paths = new PackPaths(Optional.of("drg/fragment.yaml"), Optional.empty());

        assertThat(paths).isEqualTo(new PackPaths(Optional.of("drg/fragment.yaml"), Optional.empty()));
        assertThat(paths).isNotEqualTo(PackPaths.none());
    }
}
