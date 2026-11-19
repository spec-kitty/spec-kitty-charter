package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
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
    void packRefKeepsItsPackIdAndOptionalPin() {
        PackIdentifier builtIn = new PackIdentifier("built-in");
        PackIdentifier orgA = new PackIdentifier("org-a");
        assertThat(PackRef.of(builtIn)).isEqualTo(new PackRef(builtIn, Optional.empty()));
        assertThat(PackRef.pinned(orgA, "1.2.0").version()).contains("1.2.0");
        assertThat(PackRef.pinned(orgA, "1.2.0")).isNotEqualTo(PackRef.of(orgA));
        assertThat(PackRef.of(new PackIdentifier("x".repeat(PackIdentifier.MAX_LENGTH)))
                        .packId()
                        .value())
                .hasSize(PackIdentifier.MAX_LENGTH);
        PackIdentifier x = new PackIdentifier("x");
        assertThatThrownBy(() -> PackRef.pinned(x, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRef.pinned(x, "1".repeat(65))).isInstanceOf(IllegalArgumentException.class);
    }

    /** #583, D7: a pin names a pack by the pack id grammar, so it names no pack that cannot be registered. */
    @ParameterizedTest
    @ValueSource(strings = {"org_a", "Built-In", "-x", "a--b"})
    void aPackRefRefusesAnIdOutsideThePackIdGrammar(String id) {
        PackIdentifier notAPackId = new PackIdentifier(id);
        assertThatThrownBy(() -> PackRef.of(notAPackId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(id);
    }

    @Test
    void aPackRefRefusesAnIdOfSixtyFourCharacters() {
        PackIdentifier tooLong = new PackIdentifier("x".repeat(PackIdentifier.MAX_LENGTH + 1));
        assertThatThrownBy(() -> PackRef.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }
}
