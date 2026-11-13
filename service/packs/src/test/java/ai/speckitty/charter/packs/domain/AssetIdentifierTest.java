package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An asset id is one lowercase kebab-case segment of at most 64 characters (FR-006 of slice A2), the grammar a skill
 * id has, so it names one literal path segment of the asset reads.
 */
class AssetIdentifierTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"run", "logo", "carrier-lint", "a", "a1", "fixture-data-2"})
    @DisplayName("a lowercase kebab-case segment is an asset id")
    void aKebabSegmentIsAnId(String id) {
        assertThat(new AssetIdentifier(id).value()).isEqualTo(id);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"a/payload", "Run", "run\n", "run\nx", "", " ", "1run", "-run", "run-", "run--x", "run_x"})
    @DisplayName("a slash, an upper case letter, a newline or a text outside the grammar is refused")
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> new AssetIdentifier(id))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("An asset identifier must be lowercase letters and digits");
    }

    @Test
    @DisplayName("64 characters is the longest id; 65 is refused")
    void sixtyFourCharactersIsTheCap() {
        String atCap = "a".repeat(64);

        assertThat(new AssetIdentifier(atCap).value()).hasSize(64);
        assertThatThrownBy(() -> new AssetIdentifier(atCap + "b")).isInstanceOf(ConstraintBreachException.class);
    }
}
