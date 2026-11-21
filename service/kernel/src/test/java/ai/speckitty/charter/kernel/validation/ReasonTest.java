package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Reason: one rule's outcome, with the check that produced it (#614)")
class ReasonTest {

    private static final Origin COMPOSE = new Origin("closure");

    private static final Origin PRESETS = new Origin("closure.presets");

    private static Reason failing(Origin origin) {
        return new Reason(
                origin.name(), origin, Evaluation.FAIL, ReasonCode.of("pack_sibling_conflict"), Location.NOWHERE, "d");
    }

    @Test
    @DisplayName("a reason carries the origin it was given")
    void aReasonCarriesItsOrigin() {
        Reason reason = failing(PRESETS);

        assertThat(reason.origin()).isEqualTo(PRESETS);
        assertThat(reason.origin().name()).isEqualTo("closure.presets");
    }

    @Test
    @DisplayName("two reasons alike in all but their origin are not equal")
    void equalityCoversTheOrigin() {
        Reason compose = failing(COMPOSE);
        Reason presets = new Reason(
                compose.rule(), PRESETS, compose.result(), compose.code(), compose.location(), compose.detail());

        assertThat(presets).isNotEqualTo(compose);
        // control: the same parts, the origin included, are equal
        assertThat(failing(COMPOSE)).isEqualTo(compose).hasSameHashCodeAs(compose);
    }

    @Test
    @DisplayName("a relocated reason keeps its origin")
    void aRelocatedReasonKeepsItsOrigin() {
        assertThat(failing(PRESETS).at(Location.in("pack.yaml")).origin()).isEqualTo(PRESETS);
    }

    @Test
    @DisplayName("an invariant's reason comes from the invariant it states")
    void anInvariantsOriginIsItsRule() {
        assertThat(Reason.failed("an id is a slug", "`A b` is no slug").origin())
                .isEqualTo(new Origin("an id is a slug"));
        assertThat(Reason.passed("an id is a slug").origin()).isEqualTo(new Origin("an id is a slug"));
    }

    @Test
    @DisplayName("a reason refuses a null origin")
    void aReasonRefusesANullOrigin() {
        assertThatThrownBy(() -> new Reason("r", null, Evaluation.FAIL, ReasonCode.INVARIANT, Location.NOWHERE, "d"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("an origin names its check: a blank name is refused")
    void anOriginRefusesABlankName(String blank) {
        assertThatThrownBy(() -> new Origin(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names the check");
    }
}
