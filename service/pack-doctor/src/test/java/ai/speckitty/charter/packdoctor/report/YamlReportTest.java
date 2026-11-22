package ai.speckitty.charter.packdoctor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link YamlReport}: the YAML report is the report's tree in block style (FR-008, plan D6), the default of
 * {@code --report}; loaded back by a YAML 1.2 loader, it is the same tree.
 */
class YamlReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode load(String yaml) {
        return JSON.valueToTree(new Load(LoadSettings.builder().build()).loadFromString(yaml));
    }

    private static void assertRoundTrips(JsonNode tree) {
        JsonNode back = load(YamlReport.write(tree));

        assertThat(back).isEqualTo(tree);
        assertThat(JSON.writeValueAsString(back)).isEqualTo(JSON.writeValueAsString(tree));
    }

    @Test
    void theTreeInBlockStyleInItsKeyOrder() {
        JsonNode tree = JSON.readTree("""
                {"tool": {"name": "pack-doctor", "version": "1.0"},
                 "checks": ["PACK"],
                 "problems": [{"status": 422, "violations": [{"code": "c"}]}],
                 "nothing": [],
                 "options": {"full": false}}""");

        assertThat(YamlReport.write(tree)).isEqualTo("""
                tool:
                  name: pack-doctor
                  version: '1.0'
                checks:
                  - PACK
                problems:
                  - status: 422
                    violations:
                      - code: c
                nothing: []
                options:
                  full: false
                """);
        assertRoundTrips(tree);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "yes",
                "null",
                "~",
                "1",
                "1.0",
                "true",
                "",
                " ",
                "a: b",
                "- x",
                "# not a comment",
                "'quoted'",
                "\"double\"",
                "a line\nand another\n",
                "a carriage return\r\n",
                "a control \u0001 character",
                "é ✓ 😀",
                "file:///work/packs/qa:org/"
            })
    void aStringReadsBackAsItWas(String text) {
        ObjectNode tree = JSON.createObjectNode();
        tree.put("detail", text);
        tree.putArray("checks").add(text);

        assertRoundTrips(tree);
    }

    @Test
    void aLongDetailStaysOnOneLine() {
        String detail = "The pack cannot be registered with 101 faults, the first at f0.yaml. "
                .repeat(5)
                .strip();
        ObjectNode tree = JSON.createObjectNode().put("detail", detail);

        assertThat(YamlReport.write(tree)).isEqualTo("detail: " + detail + "\n");
        assertRoundTrips(tree);
    }

    @Test
    void anIntegerBeyondAnIntReadsBackAsALong() {
        assertRoundTrips(JSON.readTree("{\"n\": 4294967296, \"m\": -1}"));
    }

    @Test
    void aValueNoReportHoldsIsRefusedNamingWhereItIs() {
        assertThatThrownBy(() -> YamlReport.write(JSON.readTree("{\"a\": [1.5]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/a/0");
        assertThatThrownBy(() -> YamlReport.write(JSON.readTree("{\"a\": null}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/a");
        assertThatThrownBy(() -> YamlReport.write(JSON.readTree("[]"))).isInstanceOf(IllegalArgumentException.class);
    }
}
