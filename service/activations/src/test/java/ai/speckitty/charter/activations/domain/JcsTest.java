package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The RFC 8785 vectors the canonicaliser is pinned to (research R3, J9). */
class JcsTest {

    private static String text(Object value) {
        return new String(Jcs.canonical(value), StandardCharsets.UTF_8);
    }

    @Test
    void sortsMemberNamesByUtf16CodeUnitsNotByCodePoints() {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("\uFFFD", 2);
        members.put("😀", 1);
        members.put("b", 4);
        members.put("a", 3);

        // U+1F600 is the pair D83D DE00; 0xD83D < 0xFFFD, so it sorts first, though its code point is the larger.
        assertThat(text(members)).isEqualTo("{\"a\":3,\"b\":4,\"😀\":1,\"\uFFFD\":2}");
    }

    @Test
    void serialisesTheRfc8785Section3222ExampleString() {
        // The RFC's input literal, decoded: euro sign, $, U+000F, LF, A, ', B, quote, two backslashes, quote, slash.
        String decoded = "€$\u000F\nA'B\"\\\\\"/";

        byte[] bytes = Jcs.canonical(decoded);

        // The RFC's expected output, as UTF-8 bytes computed outside the JVM.
        assertThat(HexFormat.of().formatHex(bytes)).isEqualTo("22e282ac245c75303030665c6e4127425c225c5c5c5c5c222f22");
    }

    @Test
    void escapesControlCharactersShortOrAsLowercaseHex() {
        assertThat(text("\b\t\n\f\r")).isEqualTo("\"\\b\\t\\n\\f\\r\"");
        assertThat(text("\u0000\u001F\u000B")).isEqualTo("\"\\u0000\\u001f\\u000b\"");
        assertThat(text("a/b")).isEqualTo("\"a/b\"");
        assertThat(text("\u007Fé")).isEqualTo("\"\u007Fé\"");
    }

    @Test
    void emitsNonAsciiAsRawUtf8() {
        assertThat(Jcs.canonical("😀")).containsExactly(0x22, 0xF0 - 256, 0x9F - 256, 0x98 - 256, 0x80 - 256, 0x22);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "a\uD800b", "\uDC00", "\uDE00\uD83D", "x\uD83D"})
    void refusesLoneSurrogates(String broken) {
        assertThatThrownBy(() -> Jcs.canonical(broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("surrogate");
    }

    @Test
    void writesIntegersInEcmaScriptForm() {
        assertThat(text(List.of(1, 0, -5, 9_007_199_254_740_991L, -9_007_199_254_740_991L)))
                .isEqualTo("[1,0,-5,9007199254740991,-9007199254740991]");
    }

    @Test
    void refusesIntegersBeyondTwoToTheFiftyThirdMinusOne() {
        assertThatThrownBy(() -> Jcs.canonical(9_007_199_254_740_992L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Jcs.canonical(-9_007_199_254_740_992L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Jcs.canonical(Long.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesEveryOtherNumberType() {
        assertThatThrownBy(() -> Jcs.canonical(1.5d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Jcs.canonical((short) 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesBooleans() {
        assertThat(text(List.of(true, false))).isEqualTo("[true,false]");
    }

    @Test
    void refusesNullAnywhere() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("a", null);

        assertThatThrownBy(() -> Jcs.canonical(withNull)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Jcs.canonical(Arrays.asList(1, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANonStringMemberName() {
        assertThatThrownBy(() -> Jcs.canonical(Map.of(1, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsArrayOrderAndNestsObjects() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", List.of());
        inner.put("y", Map.of());

        assertThat(text(List.of(List.of(3, 1, 2), inner, "c", "a")))
                .isEqualTo("[[3,1,2],{\"y\":{},\"z\":[]},\"c\",\"a\"]");
    }
}
