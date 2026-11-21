package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Origin: the opaque identifier of the element a reason is about (#614, ADR-0082 decision 4)")
class OriginTest {

    @Test
    @DisplayName("an origin carries the element identifier it was given, as it was written")
    void anOriginCarriesItsId() {
        assertThat(new Origin("acme::agent_profile:generic-agent").id()).isEqualTo("acme::agent_profile:generic-agent");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("an origin identifies an element: a blank identifier is refused")
    void anOriginRefusesABlankId(String blank) {
        assertThatThrownBy(() -> new Origin(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin.NONE");
    }

    @Test
    @DisplayName("a reason about no element has the one named origin NONE, which is no element's identifier")
    void noneIsTheOriginOfNoElement() {
        assertThat(Origin.NONE).isNotNull();
        assertThat(Origin.NONE.id()).isNotBlank();
        assertThat(Origin.NONE).isNotEqualTo(new Origin("acme"));
    }

    @Test
    @DisplayName("an origin refuses a null identifier")
    void anOriginRefusesANullId() {
        assertThatThrownBy(() -> new Origin(null)).isInstanceOf(NullPointerException.class);
    }
}
