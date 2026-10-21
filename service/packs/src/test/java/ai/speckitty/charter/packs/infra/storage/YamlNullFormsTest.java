package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** R18: a plain {@code ~}, {@code Null} or {@code NULL} is null, as in core; nothing else about scalars changes. */
class YamlNullFormsTest {

    @SuppressWarnings("unchecked")
    private static Object valueOf(String source) {
        return ((Map<String, Object>) Yaml.load(source)).get("a");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: ~", "a: Null", "a: NULL", "a: null", "a:", "a: ~ # comment"})
    void thePlainNullFormsReadAsNull(String source) {
        assertThat(valueOf(source + "\n")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: '~'", "a: \"Null\"", "a: 'NULL'", "a: \"~\"", "a: nUll", "a: ~x", "a: Returning Null"})
    void aQuotedOrDifferentFormStaysAString(String source) {
        assertThat(valueOf(source + "\n")).isInstanceOf(String.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: True", "a: TRUE", "a: 0x1F", "a: 0o17", "a: +12"})
    void theOtherJsonSchemaDifferencesStayAsTheyAre(String source) {
        assertThat(valueOf(source + "\n")).isInstanceOf(String.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: .inf", "a: .nan"})
    void theInfinityAndNanFormsAreStillFloats(String source) {
        assertThat(valueOf(source + "\n")).isInstanceOf(Double.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "Null", "NULL"})
    void theWriterQuotesAStringThatWouldReadBackAsNull(String value) {
        String edited = YamlDocument.parse("a: x\n").withScalar(1, value).render();

        assertThat(edited).isEqualTo("a: \"" + value + "\"\n");
        assertThat(valueOf(edited)).isEqualTo(value);
    }
}
