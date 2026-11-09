package ai.speckitty.charter.kernel.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The slot id of ADR-0066 decision 2: {@code <namespace>:<name>}, each part {@code [a-z][a-z0-9-]*}. Every refused id
 * stands beside the accepted neighbour that differs in the one property the rule is about.
 */
@DisplayName("SlotId: the slot id grammar")
class SlotIdTest {

    @ParameterizedTest(name = "[{index}] {0} is a slot id")
    @ValueSource(strings = {"a:b", "spdd:reasons-block", "x1:y-2", "a-b:c-d", "z9:a"})
    @DisplayName("an id of two lowercase parts is accepted, and reads back as written")
    void accepted(String text) {
        Optional<SlotId> id = SlotId.tryParse(text);

        assertThat(id).isPresent();
        assertThat(id.orElseThrow().toString()).isEqualTo(text);
        assertThat(new SlotId(text)).isEqualTo(id.orElseThrow());
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' is refused")
    @ValueSource(
            strings = {
                "A:b", "a:B", "a", "a:b:c", ":b", "a:", "1a:b", "a:1b", "-a:b", "a:-b", "a_b:c", "a:b_c", "a b:c", ""
            })
    @DisplayName("an uppercase, missing, extra, digit-led or underscored part is refused, as an answer and as a build")
    void refused(String text) {
        assertThat(SlotId.tryParse(text)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new SlotId(text)).withMessageContaining("slot id");
    }

    @Test
    @DisplayName("a non-ASCII letter, a digit outside a-z0-9 and a trailing newline are refused")
    void nonAsciiIsRefused() {
        assertThat(SlotId.tryParse("é:b")).isEmpty();
        assertThat(SlotId.tryParse("a:b\u00e9")).isEmpty();
        assertThat(SlotId.tryParse("a:b\n")).isEmpty();
        assertThat(SlotId.tryParse("a:\u0661")).isEmpty();
    }

    @Test
    @DisplayName("namespace and name split at the one colon")
    void parts() {
        SlotId id = new SlotId("spdd:reasons-block");

        assertThat(id.namespace()).isEqualTo("spdd");
        assertThat(id.name()).isEqualTo("reasons-block");
    }

    @Test
    @DisplayName("ids compare by code point: a-b:c < a:c < ab:c, and equal ids compare equal")
    void orderByCodePoint() {
        List<SlotId> sorted = List.of(new SlotId("ab:c"), new SlotId("a:c"), new SlotId("a-b:c")).stream()
                .sorted()
                .toList();

        assertThat(sorted).extracting(SlotId::toString).containsExactly("a-b:c", "a:c", "ab:c");
        assertThat(new SlotId("a:c")).isEqualByComparingTo(new SlotId("a:c"));
        assertThat(new SlotId("a:c")).isLessThan(new SlotId("a:d"));
    }
}
