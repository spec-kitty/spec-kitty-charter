package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PackPolicyOrderTest {

    private static SchemaViolation fallback(String file, String... pointer) {
        return new SchemaViolation(file, List.of(pointer), "uniqueItems", Optional.empty(), Optional.empty(), Map.of());
    }

    private static SchemaViolation unknownKey(String file, String key, String... pointer) {
        return new SchemaViolation(
                file, List.of(pointer), "additionalProperties", Optional.of(key), Optional.empty(), Map.of());
    }

    private static FileRead<RawGraph> broken(String file, SchemaViolation... violations) {
        return new FileRead.Unreadable<>(file, null, List.of(violations));
    }

    private static FileRead<PackDescriptor> descriptor(Optional<PackLevel> declared) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor("01ABC", "1.0.0", Optional.empty(), "qa", declared, Optional.empty(), List.of()));
    }

    private static PackContents contents(
            FileRead<PackDescriptor> descriptor, List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                shards,
                fragment,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static List<String> places(FileRead<RawGraph> shard) {
        return PackPolicy.judge(contents(descriptor(Optional.of(PackLevel.ORG)), List.of(shard), emptyFragment()))
                .findings()
                .stream()
                .map(f -> "/" + String.join("/", f.place()))
                .toList();
    }

    /** A fragment that was read and is empty: an org pack has one, so no {@code org_fragment_missing} fires. */
    private static FileRead<RawGraph> emptyFragment() {
        return new FileRead.Read<>(
                "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));
    }

    @Test
    void groupsComeInTheOrderPackYamlShardsFragmentPackWideThenOtherFiles() {
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.BUILT_IN)),
                List.of(
                        broken("nested/z.graph.yaml", fallback("nested/z.graph.yaml")),
                        broken("b.graph.yaml", fallback("b.graph.yaml")),
                        broken("a.graph.yaml", fallback("a.graph.yaml"))),
                broken("drg/fragment.yaml", fallback("drg/fragment.yaml")));
        assertThat(PackPolicy.judge(c).findings())
                .extracting(PackFinding::file, PackFinding::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a.graph.yaml", PackFindingCode.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("b.graph.yaml", PackFindingCode.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("drg/fragment.yaml", PackFindingCode.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("pack.yaml", PackFindingCode.PACK_LEVEL_MISMATCH),
                        org.assertj.core.groups.Tuple.tuple("nested/z.graph.yaml", PackFindingCode.DRG_SCHEMA_ERROR));
    }

    @Test
    void anUnreadablePackYamlComesBeforeEverything() {
        FileRead<PackDescriptor> bad =
                new FileRead.Unreadable<>("pack.yaml", new PackContents.ParseFailure("pack.yaml", "oops"), List.of());
        PackContents c = contents(bad, List.of(broken("a.graph.yaml", fallback("a.graph.yaml"))), emptyFragment());
        assertThat(PackPolicy.judge(c).findings())
                .extracting(PackFinding::file)
                .containsExactly("pack.yaml", "a.graph.yaml");
    }

    @Test
    void arrayIndicesCompareNumericallySoTwoComesBeforeTen() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "nodes", "10", "tags"),
                        fallback("a.graph.yaml", "nodes", "2", "tags"),
                        fallback("a.graph.yaml", "nodes", "9", "tags"))))
                .containsExactly("/nodes/2/tags", "/nodes/9/tags", "/nodes/10/tags");
    }

    @Test
    void nodesComeBeforeEdges() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "edges", "0", "when"),
                        fallback("a.graph.yaml", "nodes", "5", "label"))))
                .containsExactly("/nodes/5/label", "/edges/0/when");
    }

    @Test
    void rootKeysFollowTheShardSchemaOrder() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "edges"),
                        fallback("a.graph.yaml", "generated_by"),
                        fallback("a.graph.yaml", "nodes"),
                        fallback("a.graph.yaml", "generated_at"),
                        fallback("a.graph.yaml", "schema_version"))))
                .containsExactly("/schema_version", "/generated_at", "/generated_by", "/nodes", "/edges");
    }

    @Test
    void fragmentRootKeysHaveNodesBeforeEdges() {
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(),
                broken(
                        "drg/fragment.yaml",
                        fallback("drg/fragment.yaml", "edges"),
                        fallback("drg/fragment.yaml", "nodes")));
        assertThat(PackPolicy.judge(c).findings())
                .extracting(f -> f.place().get(0))
                .containsExactly("nodes", "edges");
    }

    @Test
    void nodeAndEdgeKeysFollowTheSchemaOrder() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "nodes", "0", "tags"),
                        fallback("a.graph.yaml", "nodes", "0", "label"),
                        fallback("a.graph.yaml", "nodes", "0", "kind"),
                        fallback("a.graph.yaml", "nodes", "0", "urn"),
                        fallback("a.graph.yaml", "edges", "0", "impacts"),
                        fallback("a.graph.yaml", "edges", "0", "when"),
                        fallback("a.graph.yaml", "edges", "0", "relation"),
                        fallback("a.graph.yaml", "edges", "0", "source"))))
                .containsExactly(
                        "/nodes/0/urn",
                        "/nodes/0/kind",
                        "/nodes/0/label",
                        "/nodes/0/tags",
                        "/edges/0/source",
                        "/edges/0/relation",
                        "/edges/0/when",
                        "/edges/0/impacts");
    }

    @Test
    void fragmentNodeKeysFollowTheFragmentSchemaOrder() {
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(),
                broken(
                        "drg/fragment.yaml",
                        fallback("drg/fragment.yaml", "nodes", "0", "body_path"),
                        fallback("drg/fragment.yaml", "nodes", "0", "title"),
                        fallback("drg/fragment.yaml", "nodes", "0", "kind"),
                        fallback("drg/fragment.yaml", "nodes", "0", "id")));
        assertThat(PackPolicy.judge(c).findings())
                .extracting(f -> f.place().get(2))
                .containsExactly("id", "kind", "title", "body_path");
    }

    @Test
    void unknownKeysSortAfterTheKnownOnesLexically() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "zzz"),
                        fallback("a.graph.yaml", "aaa"),
                        fallback("a.graph.yaml", "edges"),
                        fallback("a.graph.yaml", "nodes", "0", "zebra"),
                        fallback("a.graph.yaml", "nodes", "0", "urn"))))
                .containsExactly("/nodes/0/urn", "/nodes/0/zebra", "/edges", "/aaa", "/zzz");
    }

    @Test
    void theDescriptorFollowsItsSchemaOrder() {
        FileRead<PackDescriptor> bad = new FileRead.Unreadable<>(
                "pack.yaml",
                null,
                List.of(
                        fallback("pack.yaml", "name"),
                        fallback("pack.yaml", "pack_version"),
                        fallback("pack.yaml", "pack_id")));
        PackContents c = contents(bad, List.of(), emptyFragment());
        assertThat(PackPolicy.judge(c).findings())
                .extracting(f -> f.place().get(0))
                .containsExactly("pack_id", "pack_version", "name");
    }

    @Test
    void aMultiDefectShardIsOrderedAsTodaysReaderReportedIt() {
        FileRead<RawGraph> shard = broken(
                "a.graph.yaml",
                fallback("a.graph.yaml", "edges", "0", "impacts"),
                fallback("a.graph.yaml", "nodes", "10", "label"),
                unknownKey("a.graph.yaml", "surprise"));
        PackContents c = contents(descriptor(Optional.of(PackLevel.ORG)), List.of(shard), emptyFragment());
        assertThat(PackPolicy.judge(c).findings())
                .extracting(PackFinding::detail)
                .containsExactly(
                        "qa/a.graph.yaml: unknown key 'surprise'",
                        "qa/a.graph.yaml: '/nodes/10/label' breaks schema keyword 'uniqueItems'",
                        "qa/a.graph.yaml: '/edges/0/impacts' breaks schema keyword 'uniqueItems'");
    }

    @Test
    void aTwoFaultPackRefusesWithTheShardsFindingNotTheLevelMismatch() {
        FileRead<RawGraph> shard = broken("a.graph.yaml", unknownKey("a.graph.yaml", "surprise"));
        PackContents c = contents(descriptor(Optional.of(PackLevel.BUILT_IN)), List.of(shard), emptyFragment());
        List<PackFinding> findings = PackPolicy.judge(c).findings();
        assertThat(findings)
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.DRG_SCHEMA_ERROR, PackFindingCode.PACK_LEVEL_MISMATCH);
        assertThatThrownBy(() -> PackPolicy.load(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(r.getMessage()).isEqualTo("qa/a.graph.yaml: unknown key 'surprise'");
        });
    }

    @Test
    void graphRuleFindingsOrderNodesBeforeEdgesAndByIndexAcrossTheDocument() {
        RawGraph raw = new RawGraph(
                "a.graph.yaml",
                RawGraph.Shape.SHARD,
                List.of(
                        new PackContents.RawNode("tactic:a", "tactic", Optional.empty()),
                        new PackContents.RawNode("bad", "tactic", Optional.empty())),
                List.of(new PackContents.RawEdge(
                        "tactic:a", "nope", "requires", Optional.empty(), Optional.empty(), Optional.empty())));
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(new FileRead.Read<>("a.graph.yaml", raw)),
                emptyFragment());
        assertThat(PackPolicy.judge(c).findings())
                .extracting(PackFinding::place)
                .containsExactly(List.of("nodes", "1"), List.of("edges", "0"));
    }

    // ---- the comparators, directly --------------------------------------------------------------------

    @Test
    void aShorterPointerComesBeforeALongerOneThatExtendsIt() {
        assertThat(PackPolicy.schemaFindings(
                                "qa",
                                List.of(
                                        fallback("a.graph.yaml", "nodes", "0", "tags"),
                                        fallback("a.graph.yaml", "nodes", "0"),
                                        fallback("a.graph.yaml", "nodes")))
                        .stream()
                        .map(f -> "/" + String.join("/", f.place())))
                .containsExactly("/nodes", "/nodes/0", "/nodes/0/tags");
    }

    @Test
    void schemaFindingsPutTheEarlierPlaceFirstWhateverTheInputOrder() {
        assertThat(PackPolicy.schemaFindings(
                                "qa",
                                List.of(
                                        fallback("a.graph.yaml", "nodes", "1", "tags"),
                                        fallback("a.graph.yaml", "nodes", "0", "tags")))
                        .stream()
                        .map(f -> "/" + String.join("/", f.place())))
                .containsExactly("/nodes/0/tags", "/nodes/1/tags");
    }

    @Test
    void thePrecedenceOrdersTheFindingsOfOneFileByPlaceAndPutsAShorterPlaceFirst() {
        PackFinding deep =
                new PackFinding(PackFindingCode.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes", "1", "tags"), "d");
        PackFinding early =
                new PackFinding(PackFindingCode.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes", "0", "tags"), "d");
        PackFinding shallow = new PackFinding(PackFindingCode.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes"), "d");

        assertThat(java.util.stream.Stream.of(deep, early, shallow).sorted(PackPolicy.PRECEDENCE))
                .containsExactly(shallow, early, deep);
    }

    @Test
    void aFileWithALeadingSlashIsNotARootShard() {
        FileRead<RawGraph> rooted = broken("/x.graph.yaml", fallback("/x.graph.yaml"));
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(broken("b.graph.yaml", fallback("b.graph.yaml")), rooted),
                broken("drg/fragment.yaml", fallback("drg/fragment.yaml")));

        assertThat(PackPolicy.judge(c).findings())
                .extracting(PackFinding::file)
                .containsExactly("b.graph.yaml", "drg/fragment.yaml", "/x.graph.yaml");
    }

    @Test
    void pathsCompareSegmentBySegmentAndByCodePoint() {
        assertThat(PackPolicy.comparePaths("a", "a")).isZero();
        assertThat(PackPolicy.comparePaths("a", "a/b")).isNegative();
        assertThat(PackPolicy.comparePaths("a/b", "a")).isPositive();
        assertThat(PackPolicy.comparePaths("a", "ab")).isNegative();
        assertThat(PackPolicy.comparePaths("ab", "a")).isPositive();
        assertThat(PackPolicy.comparePaths("a.yaml", "a/b.yaml")).isPositive();
        assertThat(PackPolicy.comparePaths("python-legacy/y", "python/x")).isPositive();
        // U+FF5E (one UTF-16 unit) sorts below U+1F600 (a surrogate pair) by code point, above it by UTF-16 unit.
        assertThat(PackPolicy.comparePaths("\uff5e", "\ud83d\ude00")).isNegative();
        assertThat(PackPolicy.comparePaths("\ud83d\ude00", "\uff5e")).isPositive();
        assertThat(PackPolicy.comparePaths("", "a")).isNegative();
    }

    @Test
    void anIndexOfEighteenDigitsSortsByValueLikeAnyOther() {
        assertThat(places(broken(
                        "a.graph.yaml",
                        fallback("a.graph.yaml", "nodes", "100000000000000000"),
                        fallback("a.graph.yaml", "nodes", "99999999999999999"))))
                .containsExactly("/nodes/99999999999999999", "/nodes/100000000000000000");
    }

    // ---- the order of the places within a file is total (a TimSort contract violation was a 500) --------

    private static PackFinding at(String key) {
        return new PackFinding(PackFindingCode.PACK_UNKNOWN_KEY, "pack.yaml", List.of(key), "d");
    }

    /** Indexes of every length, with leading zeros, empty, keys that start with digits, and declared keys. */
    private static final List<String> SAMPLE = List.of(
            "",
            "0",
            "00",
            "7",
            "07",
            "007",
            "9",
            "10",
            "010",
            "7x",
            "10x",
            "1a",
            "N",
            "Nx",
            "name",
            "pack_id",
            "pack_version",
            "99999999999999999",
            "999999999999999999",
            "9999999999999999999",
            "10000000000000000000",
            "010000000000000000000",
            "12345678901234567890",
            "12345678901234567891",
            "9223372036854775807",
            "9223372036854775808",
            "18446744073709551616x");

    @Test
    void theOrderOfPlacesIsAntisymmetricAndTransitiveOverAFixedSample() {
        List<PackFinding> all = SAMPLE.stream().map(PackPolicyOrderTest::at).toList();
        for (PackFinding a : all) {
            for (PackFinding b : all) {
                assertThat(Integer.signum(PackPolicy.PRECEDENCE.compare(a, b)))
                        .as("antisymmetry of %s and %s", a.place(), b.place())
                        .isEqualTo(-Integer.signum(PackPolicy.PRECEDENCE.compare(b, a)));
                for (PackFinding c : all) {
                    if (PackPolicy.PRECEDENCE.compare(a, b) <= 0 && PackPolicy.PRECEDENCE.compare(b, c) <= 0) {
                        assertThat(PackPolicy.PRECEDENCE.compare(a, c))
                                .as("transitivity of %s, %s, %s", a.place(), b.place(), c.place())
                                .isLessThanOrEqualTo(0);
                    }
                }
            }
        }
    }

    @Test
    void indexesSortByValueBeforeEveryOtherKeyAndLeadingZerosDoNotChangeTheValue() {
        List<PackFinding> sorted = new ArrayList<>(Stream.of(
                        "pack_id", "10", "Nx", "9", "07", "1a", "7", "", "12345678901234567890", "9223372036854775808")
                .map(PackPolicyOrderTest::at)
                .toList());
        sorted.sort(PackPolicy.PRECEDENCE);
        assertThat(sorted.stream().map(f -> f.place().get(0)))
                .containsExactly(
                        "07", "7", "9", "10", "9223372036854775808", "12345678901234567890", "pack_id", "", "1a", "Nx");
    }

    @Test
    void manyShuffledMixesSortWithoutAnExceptionAndToOneOrder() {
        Random random = new Random(20260620);
        List<PackFinding> reference = SAMPLE.stream()
                .map(PackPolicyOrderTest::at)
                .sorted(PackPolicy.PRECEDENCE)
                .toList();
        for (int round = 0; round < 500; round++) {
            List<PackFinding> shuffled = new ArrayList<>(reference);
            Collections.shuffle(shuffled, random);
            shuffled.sort(PackPolicy.PRECEDENCE);
            assertThat(shuffled).isEqualTo(reference);
        }
        // Many keys at once, the shape that made TimSort check its contract: 217 keys of "N" and "Nx" mixed.
        for (int round = 0; round < 200; round++) {
            List<PackFinding> keys = new ArrayList<>();
            for (int i = 0; i < 32 + random.nextInt(200); i++) {
                int n = random.nextInt(2000);
                keys.add(at(random.nextBoolean() ? Integer.toString(n) : n + "x"));
            }
            keys.sort(PackPolicy.PRECEDENCE);
            assertThat(keys).isSortedAccordingTo(PackPolicy.PRECEDENCE);
        }
    }
}
