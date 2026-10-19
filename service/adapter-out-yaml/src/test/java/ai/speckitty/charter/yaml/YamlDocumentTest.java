package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        int index =
                doc.scalars().stream().map(YamlDocument.Scalar::value).toList().indexOf("built-in");
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

    @Test
    void aSingleQuotedScalarStaysSingleQuoted() {
        YamlDocument doc = YamlDocument.parse("a: 'it''s'\n");
        assertThat(doc.withScalar(1, "it's").render()).isEqualTo("a: 'it''s'\n");
        assertThat(doc.withScalar(1, "don't").render()).isEqualTo("a: 'don''t'\n");
        assertThat(doc.withScalar(1, "two\nlines").render()).isEqualTo("a: \"two\\nlines\"\n");
    }

    @Test
    void aLiteralBlockWithClipChompingKeepsItsHeaderAndIndentation() {
        YamlDocument doc = YamlDocument.parse("a: |\n  one\n  two\nb: c\n");
        assertThat(doc.withScalar(1, "x\n\ny\n").render()).isEqualTo("a: |\n  x\n\n  y\nb: c\n");
        assertThat(doc.withScalar(1, "x").render()).isEqualTo("a: \"x\"\nb: c\n");
    }

    @Test
    void aLiteralBlockWithStripChompingKeepsItsHeaderAndIndentation() {
        YamlDocument doc = YamlDocument.parse("a: |-\n  one\n  two\nb: c\n");
        assertThat(doc.withScalar(1, "x\ny").render()).isEqualTo("a: |-\n  x\n  y\nb: c\n");
        assertThat(doc.withScalar(1, "x\n").render()).isEqualTo("a: \"x\\n\"\nb: c\n");
    }

    @Test
    void aLiteralBlockWithKeepChompingKeepsItsHeaderAndIndentation() {
        YamlDocument doc = YamlDocument.parse("a: |+\n  one\n\nb: c\n");
        assertThat(doc.withScalar(1, "x\n\n").render()).isEqualTo("a: |+\n  x\n\nb: c\n");
        assertThat(doc.withScalar(1, "x").render()).isEqualTo("a: \"x\"\n\nb: c\n");
    }

    @Test
    void aLiteralBlockFallsBackToDoubleQuotesWhenItsHeaderCannotCarryTheValue() {
        YamlDocument doc = YamlDocument.parse("a: |\n  one\nb: c\n");
        assertThat(doc.withScalar(1, " leading\n").render()).isEqualTo("a: \" leading\\n\"\nb: c\n");
        assertThat(doc.withScalar(1, "\n").render()).isEqualTo("a: \"\\n\"\nb: c\n");
        assertThat(YamlDocument.parse("a: |2\n   one\nb: c\n")
                        .withScalar(1, "x\n")
                        .render())
                .isEqualTo("a: \"x\\n\"\nb: c\n");
    }

    @Test
    void anEditedFoldedBlockIsDoubleQuotedAndKeepsTheLineBreaksItOwned() {
        YamlDocument doc = YamlDocument.parse("a: >\n  one\n  two\n\nb: c\n");
        assertThat(doc.withScalar(1, "x").render()).isEqualTo("a: \"x\"\n\nb: c\n");
        assertThat(Yaml.loadAll(doc.withScalar(1, "x").render())).isEqualTo(Yaml.loadAll("a: x\nb: c\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x]", "x, y", "{x}", "x[0]"})
    void aPlainScalarInAFlowCollectionIsQuotedWhenItsValueHoldsAFlowIndicator(String value) {
        String edited =
                YamlDocument.parse("items: [a, b]\n").withScalar(1, value).render();
        assertThat(edited).isEqualTo("items: [\"" + value + "\", b]\n");
        assertThat(Yaml.load(edited)).isEqualTo(Map.of("items", java.util.List.of(value, "b")));
    }

    @Test
    void aPlainScalarInABlockCollectionKeepsAFlowIndicatorPlain() {
        assertThat(YamlDocument.parse("a: x\n").withScalar(1, "x, y").render()).isEqualTo("a: x, y\n");
        assertThat(YamlDocument.parse("items: [a, b]\nc: d\n")
                        .withScalar(4, "x, y")
                        .render())
                .isEqualTo("items: [a, b]\nc: x, y\n");
    }

    @Test
    void aNegativeScalarIndexHasNoScalar() {
        assertThat(YamlDocument.parse("a: x\n").scalar(-1)).isEmpty();
        assertThat(YamlDocument.parse("a: x\n").scalar(1))
                .map(YamlDocument.Scalar::value)
                .contains("x");
        assertThat(YamlDocument.parse("a: x\n").scalar(2)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\x81", "\\x9f", "\\x7f", "\\x01", "\\ufffe", "\\uffff"})
    void aNonPrintableCodePointIsEscapedWhenItIsWrittenDoubleQuoted(String escape) {
        String source = "a: \"" + escape + "\"\n";
        assertThat(YamlDocument.parse(source).mapScalars(v -> v).render()).isEqualTo(source);
    }

    @Test
    void aPrintableCodePointIsWrittenAsItselfWhenItIsWrittenDoubleQuoted() {
        assertThat(YamlDocument.parse("a: x\n")
                        .withScalar(1, "\u0085\u00e9\ud83d\ude00 true")
                        .render())
                .isEqualTo("a: \"\u0085\u00e9\ud83d\ude00 true\"\n");
    }

    @Test
    void rewritingEveryScalarWithItselfIsByteForByteWhereTheStyleCarriesTheValue() {
        String source = "a: plain\nb: 'it''s'\nc: |\n  one\n  two\nd: |-\n  three\ne: |+\n  four\n\nf: [x, 'y']\n";
        assertThat(YamlDocument.parse(source).mapScalars(v -> v).render()).isEqualTo(source);
    }

    /** Values a quoted or block scalar cannot carry as written: a control, a noncharacter, a lone surrogate, a CR. */
    static java.util.stream.Stream<String> valuesNeedingEscapes() {
        return java.util.stream.Stream.of("x\u0001y", "x\ufffey", "\ud800", "x\ry");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("valuesNeedingEscapes")
    void aSingleQuotedScalarFallsBackToDoubleQuotesForAValueItCannotCarry(String value) {
        String edited = YamlDocument.parse("a: 'x'\n").withScalar(1, value).render();
        assertThat(edited).startsWith("a: \"");
        assertThat(Yaml.load(edited)).isEqualTo(Map.of("a", value));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("valuesNeedingEscapes")
    void aSingleQuotedScalarInAFlowCollectionFallsBackToDoubleQuotesForAValueItCannotCarry(String value) {
        String edited = YamlDocument.parse("a: [ 'x' ]\n").withScalar(1, value).render();
        assertThat(Yaml.load(edited)).isEqualTo(Map.of("a", java.util.List.of(value)));
    }

    @Test
    void aLiteralBlockFallsBackToDoubleQuotesForAValueWithAControlCharacter() {
        String value = "x\u0001y\n";
        String edited = YamlDocument.parse("a: |\n  one\n").withScalar(1, value).render();
        assertThat(edited).startsWith("a: \"");
        assertThat(Yaml.load(edited)).isEqualTo(Map.of("a", value));
    }

    @ParameterizedTest
    @ValueSource(strings = {":a", ":", "- a", "? a", "a: b", "a #b"})
    void aPlainScalarInAFlowCollectionIsQuotedWhenFlowParsingReadsItDifferently(String value) {
        String edited = YamlDocument.parse("[x, y]\n").withScalar(1, value).render();
        assertThat(Yaml.load(edited)).isEqualTo(java.util.List.of("x", value));
    }
}
