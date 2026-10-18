package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlDocumentTest {

    private static final String SOURCE = """
            # A comment that must survive.
            schema_version: '1.0'   # trailing comment
            name: built-in
            folded: >
              one
              two
            items: [a, "b c", 'it''s']
            multi: plain
              continued
            """;

    @Test
    void anUneditedDocumentRendersByteForByte() {
        assertThat(YamlDocument.parse(SOURCE).render()).isEqualTo(SOURCE);
        assertThat(YamlDocument.parse("").render()).isEmpty();
        assertThat(YamlDocument.parse("é: ü\r\n---\r\nx: 1\r\n").render()).isEqualTo("é: ü\r\n---\r\nx: 1\r\n");
    }

    @Test
    void anEditReplacesOneScalarAndKeepsEverythingElse() {
        YamlDocument doc = YamlDocument.parse(SOURCE);
        int index = doc.scalars().stream().map(YamlDocument.Scalar::value).toList().indexOf("built-in");
        String edited = doc.withScalar(index, "org").render();
        assertThat(edited).isEqualTo(SOURCE.replace("name: built-in", "name: org"));
    }

    @Test
    void aValueThePlainStyleCannotCarryIsQuoted() {
        YamlDocument doc = YamlDocument.parse("a: x\n");
        assertThat(doc.withScalar(1, "true").render()).isEqualTo("a: \"true\"\n");
        assertThat(doc.withScalar(1, "a: b").render()).isEqualTo("a: \"a: b\"\n");
        assertThat(doc.withScalar(1, "two\nlines").render()).isEqualTo("a: \"two\\nlines\"\n");
    }

    @Test
    void rewritingEveryScalarWithItselfLoadsTheSameData() {
        String rewritten = YamlDocument.parse(SOURCE).mapScalars(v -> v).render();
        assertThat(Yaml.loadAll(rewritten)).isEqualTo(Yaml.loadAll(SOURCE));
        assertThat(((Map<?, ?>) Yaml.load(rewritten)).get("multi")).isEqualTo("plain continued");
    }
}
