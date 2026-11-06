package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"01ARZ3NDEKTSV4RRFFQ69G5FAV", "123e4567-e89b-12d3-a456-426614174000", "a"})
    void acceptsAUlidAUuidAndOneCharacter(String id) {
        assertThat(new ActivationId(id).value()).isEqualTo(id);
        assertThat(new ActivationId(id)).hasToString(id);
    }

    @Test
    void acceptsSixtyFourCharacters() {
        assertThat(new ActivationId("a".repeat(64)).value()).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-abc", "a/b", "a_b", "a b"})
    void refusesWhatTheGrammarRefuses(String id) {
        assertThatThrownBy(() -> new ActivationId(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesSixtyFiveCharacters() {
        String tooLong = "a".repeat(65);

        assertThatThrownBy(() -> new ActivationId(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mintsFromTheSuppliedGenerator() {
        assertThat(ActivationId.mint(() -> "01ARZ3NDEKTSV4RRFFQ69G5FAV"))
                .isEqualTo(new ActivationId("01ARZ3NDEKTSV4RRFFQ69G5FAV"));
        assertThatThrownBy(() -> ActivationId.mint(() -> "not/an/id")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void packRefKeepsItsSlugAndOptionalPin() {
        assertThat(PackRef.of("built-in")).isEqualTo(new PackRef("built-in", Optional.empty()));
        assertThat(PackRef.pinned("org_a", "1.2.0").version()).contains("1.2.0");
        assertThat(PackRef.pinned("org_a", "1.2.0")).isNotEqualTo(PackRef.of("org_a"));
        assertThatThrownBy(() -> PackRef.of("Built-In")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRef.of("-x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRef.of("x".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRef.pinned("x", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRef.pinned("x", "1".repeat(65))).isInstanceOf(IllegalArgumentException.class);
    }
}
