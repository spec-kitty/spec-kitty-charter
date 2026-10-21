package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A document the validator cannot walk, or that holds a value it cannot read, is a parse failure with a fixed first
 * line, and never an {@link Error} or an escaped exception (WP07 review, cycle 1).
 */
class ParsedYamlTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String HEADER = "schema_version: '1.0'\ngenerated_at: STATIC\n";
    private static final String DESCRIPTOR = "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\n";
    private static final String TOO_DEEP = "the document is nested too deeply";
    private static final String NOT_PLAIN = "a value is not a string, a number, a boolean or null";

    /** One nested value per nesting style and depth: flow lists and maps, block sequences. */
    static Stream<Named<String>> nested() {
        Stream<Named<String>> flow = Stream.of(10_000, 100_000)
                .flatMap(n -> Stream.of(
                        Named.of("flow lists x" + n, "[".repeat(n) + "]".repeat(n)),
                        Named.of("flow maps x" + n, "{a: ".repeat(n) + "1" + "}".repeat(n))));
        return Stream.concat(
                flow,
                Stream.of(
                        Named.of("block sequences x5000", "- ".repeat(5_000) + "x"),
                        Named.of("block sequences x20000", "- ".repeat(20_000) + "x"),
                        Named.of("flow lists x201", "[".repeat(201) + "]".repeat(201)),
                        Named.of("block sequences x201", "- ".repeat(201) + "x")));
    }

    private static List<PackFinding> shard(String value) {
        return findings(GraphDocumentReader.read(HEADER + "nodes: " + indent(value) + "\n", Shape.SHARD, SHARD));
    }

    private static List<PackFinding> fragment(String value) {
        return findings(GraphDocumentReader.read("nodes: " + indent(value) + "\n", Shape.FRAGMENT, FRAGMENT));
    }

    private static List<PackFinding> descriptor(String value) {
        FileRead<PackDescriptor> read = PackDescriptorReader.read(DESCRIPTOR + "extra: " + indent(value) + "\n");
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(((FileRead.Unreadable<PackDescriptor>) read).parse().firstLine())
                .isEqualTo(TOO_DEEP);
        return PackPolicy.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read);
    }

    /** A block sequence under a key needs its own lines; a flow value does not. */
    private static String indent(String value) {
        return value.startsWith("- ") ? "\n" + value : value;
    }

    private static List<PackFinding> findings(FileRead<RawGraph> read) {
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        return PackPolicy.unreadable("qa", (FileRead.Unreadable<RawGraph>) read);
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aShardNestedTooDeeplyIsADrgSchemaErrorWithTheFixedFirstLine(String value) {
        assertThat(shard(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("qa/tactic.graph.yaml is not valid YAML: " + TOO_DEEP);
        });
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aFragmentNestedTooDeeplyIsADrgSchemaErrorWithTheFixedFirstLine(String value) {
        assertThat(fragment(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("qa/drg/fragment.yaml is not valid YAML: " + TOO_DEEP);
        });
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aPackDescriptorNestedTooDeeplyIsPackUnreadableWithTheFixedFirstLine(String value) {
        assertThat(descriptor(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackFindingCode.PACK_UNREADABLE);
            assertThat(f.detail()).isEqualTo("pack.yaml of qa is not valid YAML");
        });
    }

    @Test
    void aDocumentNestedWithinTheBoundIsStillRead() {
        String within = "[".repeat(150) + "]".repeat(150);
        assertThat(ParsedYaml.parse("x: " + within + "\n", "x.yaml").failure()).isNull();
    }

    @Test
    void aTooDeepDocumentIsRefusedBeforeItIsComposedAndTheStackIsNotCaught() {
        ParsedYaml parsed = ParsedYaml.parse("[".repeat(1_000_000) + "]".repeat(1_000_000), "x.yaml");
        assertThat(parsed.failure()).isNotNull();
        assertThat(parsed.failure().firstLine()).isEqualTo(TOO_DEEP);
    }

    // ---- aliases that expand past the node budget ---------------------------------------------------

    private static final String TOO_LARGE = "the document is too large";

    /** About 300 bytes that expand to 3^16 (43 million) paths: each level lists the one below three times. */
    private static String aliasBomb(int levels) {
        StringBuilder sb = new StringBuilder("l0: &l0 [aaaaaaaaaaaaaaaaaaaaaaaaaa]\n");
        for (int i = 1; i <= levels; i++) {
            sb.append("l%d: &l%d [*l%d, *l%d, *l%d]\n".formatted(i, i, i - 1, i - 1, i - 1));
        }
        return sb.toString();
    }

    @Test
    void anAliasBombIsAPackUnreadableForPackYamlWithinTheTimeout() {
        FileRead<PackDescriptor> read = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> PackDescriptorReader.read(DESCRIPTOR + aliasBomb(16)));
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(((FileRead.Unreadable<PackDescriptor>) read).parse().firstLine())
                .isEqualTo(TOO_LARGE);
        assertThat(PackPolicy.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(PackFindingCode.PACK_UNREADABLE);
                    assertThat(f.detail()).isEqualTo("pack.yaml of qa is not valid YAML");
                });
    }

    @Test
    void anAliasBombInAShardOrFragmentIsADrgSchemaErrorWithinTheTimeout() {
        String bomb = aliasBomb(16);
        FileRead<RawGraph> shard = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> GraphDocumentReader.read(HEADER + "nodes: []\n" + bomb, Shape.SHARD, SHARD));
        assertThat(findings(shard)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("qa/tactic.graph.yaml is not valid YAML: " + TOO_LARGE);
        });
        FileRead<RawGraph> fragment = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> GraphDocumentReader.read("nodes: []\n" + bomb, Shape.FRAGMENT, FRAGMENT));
        assertThat(findings(fragment)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("qa/drg/fragment.yaml is not valid YAML: " + TOO_LARGE);
        });
    }

    @Test
    void aDocumentJustWithinTheNodeBudgetIsStillRead() {
        // 1 root + 1 key's list + 99_998 scalars = exactly MAX_NODES nodes.
        String within = "k: [" + "1, ".repeat(ParsedYaml.MAX_NODES - 3) + "1]\n";
        assertThat(ParsedYaml.parse(within, "x.yaml").failure()).isNull();
        String over = "k: [" + "1, ".repeat(ParsedYaml.MAX_NODES - 2) + "1]\n";
        assertThat(ParsedYaml.parse(over, "x.yaml").failure()).isNotNull();
    }

    // ---- a scalar that is no string, number, boolean or null --------------------------------------

    @Test
    void aBinaryIdInAFragmentIsAParseFailure() {
        assertThat(fragment("[{id: !!binary aGVsbG8=, kind: directives}]"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
                    assertThat(f.detail()).isEqualTo("qa/drg/fragment.yaml is not valid YAML: " + NOT_PLAIN);
                });
    }

    @Test
    void aBinaryRelationInAShardIsAParseFailureNotAnEmptyUnknownRelation() {
        String edges =
                HEADER + "nodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: !!binary aGVsbG8=}\n";
        assertThat(findings(GraphDocumentReader.read(edges, Shape.SHARD, SHARD)))
                .singleElement()
                .satisfies(
                        f -> assertThat(f.detail()).isEqualTo("qa/tactic.graph.yaml is not valid YAML: " + NOT_PLAIN));
    }

    @Test
    void aBinaryValueInPackYamlIsAParseFailure() {
        FileRead<PackDescriptor> read =
                PackDescriptorReader.read("pack_id: !!binary aGVsbG8=\npack_version: 1.0.0\nname: qa\n");
        assertThat(PackPolicy.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(PackFindingCode.PACK_UNREADABLE);
                    assertThat(f.detail()).isEqualTo("pack.yaml of qa is not valid YAML");
                });
        assertThat(((FileRead.Unreadable<PackDescriptor>) read).parse().firstLine())
                .isEqualTo(NOT_PLAIN);
    }

    @ParameterizedTest
    @MethodSource("plainScalars")
    void aStringNumberBooleanOrNullIsStillRead(String yaml) {
        assertThat(ParsedYaml.parse("k: " + yaml + "\n", "x.yaml").failure()).isNull();
    }

    static Stream<String> plainScalars() {
        return Stream.of(
                "text",
                "'1'",
                "1",
                "1.5",
                "true",
                "null",
                ".nan",
                "-.inf",
                "1e400",
                "123456789012345678901234567890123456789",
                "!!set {a, b}");
    }
}
