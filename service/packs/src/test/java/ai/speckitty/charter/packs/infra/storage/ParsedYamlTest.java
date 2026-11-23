package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
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

    /** Only a hang cap, far above the work's time so load cannot trip it; the cause assertion is the guard (#320). */
    private static final Duration HANG_CAP = Duration.ofSeconds(30);

    private static final String SHARD = "tactic.graph.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String HEADER = "schema_version: '1.0'\ngenerated_at: STATIC\n";
    private static final String DESCRIPTOR = "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\n";
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

    private static List<Reason> shard(String value) {
        return findings(GraphDocumentReader.read(HEADER + "nodes: " + indent(value) + "\n", Shape.SHARD, SHARD));
    }

    private static List<Reason> fragment(String value) {
        return findings(GraphDocumentReader.read("nodes: " + indent(value) + "\n", Shape.FRAGMENT, FRAGMENT));
    }

    private static List<Reason> descriptor(String value) {
        FileRead<PackDescriptor> read = PackDescriptorReader.read(DESCRIPTOR + "extra: " + indent(value) + "\n");
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(((FileRead.Unreadable<PackDescriptor>) read).parse().firstLine())
                .isEqualTo(TOO_DEEP);
        return Wording.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read);
    }

    /** A block sequence under a key needs its own lines; a flow value does not. */
    private static String indent(String value) {
        return value.startsWith("- ") ? "\n" + value : value;
    }

    private static List<Reason> findings(FileRead<RawGraph> read) {
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        return Wording.unreadable("qa", (FileRead.Unreadable<RawGraph>) read);
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aShardNestedTooDeeplyIsADrgSchemaErrorWithTheFixedFirstLine(String value) {
        assertThat(shard(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("tactic.graph.yaml: " + TOO_DEEP);
        });
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aFragmentNestedTooDeeplyIsADrgSchemaErrorWithTheFixedFirstLine(String value) {
        assertThat(fragment(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("drg/fragment.yaml: " + TOO_DEEP);
        });
    }

    @ParameterizedTest
    @MethodSource("nested")
    void aPackDescriptorNestedTooDeeplyIsPackUnreadableWithTheFixedFirstLine(String value) {
        assertThat(descriptor(value)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
            assertThat(f.detail()).startsWith("pack.yaml of qa is not valid YAML: ");
        });
    }

    @Test
    void aDocumentNestedWithinTheBoundIsStillRead() {
        String within = "[".repeat(150) + "]".repeat(150);
        assertThat(ParsedYaml.parse("x: " + within + "\n", "x.yaml").failure()).isNull();
    }

    @Test
    void aTooDeepDocumentIsRefusedBeforeItIsComposedAndTheStackIsNotCaught() {
        // Half a million levels: the deepest text within the code-point cap.
        int depth = Yaml.MAX_CODE_POINTS / 2;
        ParsedYaml parsed = ParsedYaml.parse("[".repeat(depth) + "]".repeat(depth), "x.yaml");
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
        ParsedYaml parsed =
                assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(DESCRIPTOR + aliasBomb(16), "pack.yaml"));
        assertThat(parsed.failure())
                .as("the node budget refuses the bomb before anything walks it")
                .isNotNull();
        assertThat(parsed.failure().firstLine()).isEqualTo(TOO_LARGE);
        FileRead<PackDescriptor> read =
                assertTimeoutPreemptively(HANG_CAP, () -> PackDescriptorReader.read(DESCRIPTOR + aliasBomb(16)));
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(((FileRead.Unreadable<PackDescriptor>) read).parse().firstLine())
                .isEqualTo(TOO_LARGE);
        assertThat(Wording.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
                    assertThat(f.detail()).startsWith("pack.yaml of qa is not valid YAML: ");
                });
    }

    @Test
    void anAliasBombInAShardOrFragmentIsADrgSchemaErrorWithinTheTimeout() {
        String bomb = aliasBomb(16);
        ParsedYaml parsedShard =
                assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(HEADER + "nodes: []\n" + bomb, SHARD));
        assertThat(parsedShard.failure())
                .as("the node budget refuses the bomb before anything walks it")
                .isNotNull();
        assertThat(parsedShard.failure().firstLine()).isEqualTo(TOO_LARGE);
        FileRead<RawGraph> shard = assertTimeoutPreemptively(
                HANG_CAP, () -> GraphDocumentReader.read(HEADER + "nodes: []\n" + bomb, Shape.SHARD, SHARD));
        assertThat(findings(shard)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("tactic.graph.yaml: " + TOO_LARGE);
        });
        FileRead<RawGraph> fragment = assertTimeoutPreemptively(
                HANG_CAP, () -> GraphDocumentReader.read("nodes: []\n" + bomb, Shape.FRAGMENT, FRAGMENT));
        assertThat(findings(fragment)).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(f.detail()).isEqualTo("drg/fragment.yaml: " + TOO_LARGE);
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

    // ---- a document past the code-point cap ---------------------------------------------------------

    @Test
    void aDocumentPastTheCodePointCapIsTooLargeBeforeItIsScanned() {
        // One scalar of the cap's length: the scanner's window grows once per chunk it reads, so a single long scalar
        // costs the square of its length; a document past the cap is refused before the scanner sees it.
        String over = "k: " + "x".repeat(Yaml.MAX_CODE_POINTS) + "\n";
        ParsedYaml parsed = assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(over, "x.yaml"));
        assertThat(parsed.failure()).isNotNull();
        assertThat(parsed.failure().firstLine()).isEqualTo(TOO_LARGE);
    }

    /** {@code k: v} padded with comment lines to exactly {@code codePoints} code points of {@code filler}. */
    private static String paddedTo(int codePoints, String filler) {
        StringBuilder text = new StringBuilder("k: v\n");
        int line = 1_000;
        while (text.codePointCount(0, text.length()) + line + 2 <= codePoints) {
            text.append('#').append(filler.repeat(line)).append('\n');
        }
        int rest = codePoints - text.codePointCount(0, text.length()) - 2;
        return text.append('#').append(filler.repeat(rest)).append('\n').toString();
    }

    @Test
    void aDocumentOfExactlyTheCapIsStillRead() {
        String within = paddedTo(Yaml.MAX_CODE_POINTS, "x");
        assertThat(within.codePointCount(0, within.length())).isEqualTo(Yaml.MAX_CODE_POINTS);
        assertThat(assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(within, "x.yaml"))
                        .failure())
                .isNull();
        assertThat(ParsedYaml.parse(within + "#", "x.yaml").failure().firstLine())
                .isEqualTo(TOO_LARGE);
    }

    @Test
    void theCapCountsCodePointsNotUtf16Units() {
        // A supplementary character is one code point and two UTF-16 units.
        String within = paddedTo(Yaml.MAX_CODE_POINTS, "\uD83D\uDE00");
        assertThat(within.length()).isGreaterThan(Yaml.MAX_CODE_POINTS);
        assertThat(assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(within, "x.yaml"))
                        .failure())
                .isNull();
    }

    // ---- a scalar that is no string, number, boolean or null --------------------------------------

    @Test
    void aBinaryIdInAFragmentIsAParseFailure() {
        assertThat(fragment("[{id: !!binary aGVsbG8=, kind: directives}]"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
                    assertThat(f.detail()).isEqualTo("drg/fragment.yaml: " + NOT_PLAIN);
                });
    }

    @Test
    void aBinaryRelationInAShardIsAParseFailureNotAnEmptyUnknownRelation() {
        String edges =
                HEADER + "nodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: !!binary aGVsbG8=}\n";
        assertThat(findings(GraphDocumentReader.read(edges, Shape.SHARD, SHARD)))
                .singleElement()
                .satisfies(f -> assertThat(f.detail()).isEqualTo("tactic.graph.yaml: " + NOT_PLAIN));
    }

    @Test
    void aBinaryValueInPackYamlIsAParseFailure() {
        FileRead<PackDescriptor> read =
                PackDescriptorReader.read("pack_id: !!binary aGVsbG8=\npack_version: 1.0.0\nname: qa\n");
        assertThat(Wording.unreadable("qa", (FileRead.Unreadable<PackDescriptor>) read))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
                    assertThat(f.detail()).startsWith("pack.yaml of qa is not valid YAML: ");
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
