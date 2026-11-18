package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Readouts;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.infra.storage.Wording;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PrecedenceTest {

    /** A fixture of the group its caller tests first: validate mode passes the placement codes (#554, N2). */
    private static final ReasonCode PACK_WIDE = new ReasonCode("pack_wide", Severity.WARNING);

    private static final Comparator<Reason> PRECEDENCE = Precedence.comparator(Set.of(PACK_WIDE));

    /** A failing reason at a file and a place, its place as a JSON pointer (RFC 6901), as the rules state it. */
    private static Reason reason(ReasonCode code, String file, List<String> place) {
        Location where = Location.in(file);
        if (!place.isEmpty()) {
            StringBuilder pointer = new StringBuilder();
            place.forEach(segment ->
                    pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1")));
            where = where.at(pointer.toString());
        }
        return new Reason(code.value(), Evaluation.FAIL, code, where, "d");
    }

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
                new PackDescriptor(
                        "01ARWG13C00000000000001ABC", "1.0.0", Optional.empty(), "qa", declared, Optional.empty()));
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
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * The findings of hand-written facts in validate mode's order: the one reader's read faults ({@link Readouts}) and
     * the content rules' findings, with any finding of the caller's first group given beside them, sorted by the
     * precedence {@code PackVerdicts} sorts with.
     */
    private static List<Reason> judged(PackContents contents, Reason... firstGroup) {
        List<Reason> all = new ArrayList<>(Readouts.of(contents).rationale().failures());
        all.addAll(PackRulesets.content().check(contents).failures());
        all.addAll(List.of(firstGroup));
        all.sort(PRECEDENCE);
        return all;
    }

    /** A finding of the first group at {@code pack.yaml}, as a placement rule states one. */
    private static Reason packWide() {
        return reason(PACK_WIDE, "pack.yaml", List.of());
    }

    /** The places of a shard's findings, in validate mode's order, as JSON pointers. */
    private static List<String> places(FileRead<RawGraph> shard) {
        return judged(contents(descriptor(Optional.of(PackLevel.ORG)), List.of(shard), emptyFragment())).stream()
                .map(f -> f.location().pointer())
                .toList();
    }

    /** The first segment of a pointer. */
    private static String segment(Reason finding, int index) {
        return finding.location().pointer().substring(1).split("/", -1)[index];
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
        assertThat(judged(c, packWide()))
                .extracting(f -> f.location().file(), Reason::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a.graph.yaml", PackCodes.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("b.graph.yaml", PackCodes.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("drg/fragment.yaml", PackCodes.DRG_SCHEMA_ERROR),
                        org.assertj.core.groups.Tuple.tuple("pack.yaml", PACK_WIDE),
                        org.assertj.core.groups.Tuple.tuple("nested/z.graph.yaml", PackCodes.DRG_SCHEMA_ERROR));
    }

    @Test
    void anUnreadablePackYamlComesBeforeEverything() {
        FileRead<PackDescriptor> bad =
                new FileRead.Unreadable<>("pack.yaml", new PackContents.ParseFailure("pack.yaml", "oops"), List.of());
        PackContents c = contents(bad, List.of(broken("a.graph.yaml", fallback("a.graph.yaml"))), emptyFragment());
        assertThat(judged(c)).extracting(f -> f.location().file()).containsExactly("pack.yaml", "a.graph.yaml");
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
        assertThat(judged(c)).extracting(f -> segment(f, 0)).containsExactly("nodes", "edges");
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
        assertThat(judged(c)).extracting(f -> segment(f, 2)).containsExactly("id", "kind", "title", "body_path");
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
        assertThat(judged(c)).extracting(f -> segment(f, 0)).containsExactly("pack_id", "pack_version", "name");
    }

    @Test
    void aMultiDefectShardIsOrderedAsTodaysReaderReportedIt() {
        FileRead<RawGraph> shard = broken(
                "a.graph.yaml",
                fallback("a.graph.yaml", "edges", "0", "impacts"),
                fallback("a.graph.yaml", "nodes", "10", "label"),
                unknownKey("a.graph.yaml", "surprise"));
        PackContents c = contents(descriptor(Optional.of(PackLevel.ORG)), List.of(shard), emptyFragment());
        assertThat(judged(c))
                .extracting(Reason::detail)
                .containsExactly(
                        "a.graph.yaml: unknown key 'surprise'",
                        "a.graph.yaml: '/nodes/10/label' breaks schema keyword 'uniqueItems'",
                        "a.graph.yaml: '/edges/0/impacts' breaks schema keyword 'uniqueItems'");
    }

    @Test
    void aShardsFindingComesBeforeThePackWideOne() {
        FileRead<RawGraph> shard = broken("a.graph.yaml", unknownKey("a.graph.yaml", "surprise"));
        PackContents c = contents(descriptor(Optional.of(PackLevel.BUILT_IN)), List.of(shard), emptyFragment());
        List<Reason> findings = judged(c, packWide());
        assertThat(findings).extracting(Reason::code).containsExactly(PackCodes.DRG_SCHEMA_ERROR, PACK_WIDE);
        assertThat(findings.getFirst().detail()).isEqualTo("a.graph.yaml: unknown key 'surprise'");
    }

    @Test
    void graphRuleFindingsOrderNodesBeforeEdgesAndByIndexAcrossTheDocument() {
        RawGraph raw = new RawGraph(
                "a.graph.yaml",
                RawGraph.Shape.SHARD,
                List.of(
                        new PackContents.RawNode("tactic:a", "tactic", Optional.empty()),
                        new PackContents.RawNode("bad", "tactic", Optional.empty())),
                List.of(new PackContents.RawEdge("tactic:a", "nope", "requires", Optional.empty(), Optional.empty())));
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(new FileRead.Read<>("a.graph.yaml", raw)),
                emptyFragment());
        assertThat(judged(c)).extracting(f -> f.location().pointer()).containsExactly("/nodes/1", "/edges/0");
    }

    // ---- the comparators, directly --------------------------------------------------------------------

    @Test
    void aShorterPointerComesBeforeALongerOneThatExtendsIt() {
        assertThat(Wording.schemaFindings(
                                "qa",
                                List.of(
                                        fallback("a.graph.yaml", "nodes", "0", "tags"),
                                        fallback("a.graph.yaml", "nodes", "0"),
                                        fallback("a.graph.yaml", "nodes")))
                        .stream()
                        .map(f -> f.location().pointer()))
                .containsExactly("/nodes", "/nodes/0", "/nodes/0/tags");
    }

    @Test
    void schemaFindingsPutTheEarlierPlaceFirstWhateverTheInputOrder() {
        assertThat(Wording.schemaFindings(
                                "qa",
                                List.of(
                                        fallback("a.graph.yaml", "nodes", "1", "tags"),
                                        fallback("a.graph.yaml", "nodes", "0", "tags")))
                        .stream()
                        .map(f -> f.location().pointer()))
                .containsExactly("/nodes/0/tags", "/nodes/1/tags");
    }

    @Test
    void thePrecedenceOrdersTheFindingsOfOneFileByPlaceAndPutsAShorterPlaceFirst() {
        Reason deep = reason(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes", "1", "tags"));
        Reason early = reason(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes", "0", "tags"));
        Reason shallow = reason(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml", List.of("nodes"));

        assertThat(java.util.stream.Stream.of(deep, early, shallow).sorted(PRECEDENCE))
                .containsExactly(shallow, early, deep);
    }

    @Test
    void theFirstGroupSortsAfterTheGraphDocumentsAndBeforeEveryOtherFile() {
        Reason other = reason(PackCodes.DUPLICATE_ARTIFACT_ID, "a/x.tactic.yaml", List.of());
        Reason wide = reason(PACK_WIDE, "pack.yaml", List.of());
        Reason fragment = reason(PackCodes.DRG_SCHEMA_ERROR, "drg/fragment.yaml", List.of());
        Reason shard = reason(PackCodes.DRG_SCHEMA_ERROR, "z.graph.yaml", List.of());
        Reason descriptor = reason(PackCodes.DRG_SCHEMA_ERROR, "pack.yaml", List.of("name"));

        assertThat(java.util.stream.Stream.of(other, wide, fragment, shard, descriptor)
                        .sorted(PRECEDENCE))
                .containsExactly(descriptor, shard, fragment, wide, other);
        assertThat(Precedence.group(PACK_WIDE, "pack.yaml", Set.of(PACK_WIDE))).isEqualTo(4);
        assertThat(Precedence.group(PACK_WIDE, "pack.yaml", Set.of())).isEqualTo(1);
        assertThat(Precedence.group(PackCodes.DRG_SCHEMA_ERROR, "drg/a.graph.yaml", Set.of()))
                .as("a shard below the root is no root shard")
                .isEqualTo(5);
    }

    @Test
    void withinAGroupTheFilesSortByPathThenByPlace() {
        Comparator<Reason> order = Precedence.comparator(Set.of());
        Reason later = reason(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/b.tactic.yaml", List.of());
        Reason earlier = reason(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/a.tactic.yaml", List.of("id"));
        Reason legacy = reason(PackCodes.DUPLICATE_ARTIFACT_ID, "python-legacy/y.tactic.yaml", List.of());

        assertThat(Stream.of(later, legacy, earlier).sorted(order))
                .as("the path decides before the place: a whole later file sorts after a place in an earlier one")
                .containsExactly(legacy, earlier, later);
    }

    @Test
    void aPointerSegmentIsDecodedBeforeItIsCompared() {
        Reason slash = reason(PackCodes.DRG_SCHEMA_ERROR, "pack.yaml", List.of("a/b"));
        Reason tilde = reason(PackCodes.DRG_SCHEMA_ERROR, "pack.yaml", List.of("a~"));

        assertThat(slash.location().pointer()).isEqualTo("/a~1b");
        assertThat(Findings.segments(slash.location().pointer())).containsExactly("a/b");
        assertThat(Findings.segments(tilde.location().pointer())).containsExactly("a~");
        assertThat(Findings.segments("")).isEmpty();
        assertThat(PRECEDENCE.compare(slash, tilde))
                .as("'a/b' sorts before 'a~' by code unit; '~1' would not")
                .isNegative();
    }

    @Test
    void aFileWithALeadingSlashIsNotARootShard() {
        FileRead<RawGraph> rooted = broken("/x.graph.yaml", fallback("/x.graph.yaml"));
        PackContents c = contents(
                descriptor(Optional.of(PackLevel.ORG)),
                List.of(broken("b.graph.yaml", fallback("b.graph.yaml")), rooted),
                broken("drg/fragment.yaml", fallback("drg/fragment.yaml")));

        assertThat(judged(c))
                .extracting(f -> f.location().file())
                .containsExactly("b.graph.yaml", "drg/fragment.yaml", "/x.graph.yaml");
    }

    @Test
    void thePathOrderIsPythonsOnEveryPathOfTheCorpus() throws java.io.IOException {
        List<String> expected = ArtifactRulesTest.pythonPathOrder();
        List<String> shuffled = new ArrayList<>(expected);
        Collections.shuffle(shuffled, new Random(3));

        assertThat(shuffled.stream().sorted(Precedence::comparePaths)).containsExactlyElementsOf(expected);
    }

    @Test
    void pathsCompareSegmentBySegmentAndByCodePoint() {
        assertThat(Precedence.comparePaths("a", "a")).isZero();
        assertThat(Precedence.comparePaths("a", "a/b")).isNegative();
        assertThat(Precedence.comparePaths("a/b", "a")).isPositive();
        assertThat(Precedence.comparePaths("a", "ab")).isNegative();
        assertThat(Precedence.comparePaths("ab", "a")).isPositive();
        assertThat(Precedence.comparePaths("a.yaml", "a/b.yaml")).isPositive();
        assertThat(Precedence.comparePaths("python-legacy/y", "python/x")).isPositive();
        // U+FF5E (one UTF-16 unit) sorts below U+1F600 (a surrogate pair) by code point, above it by UTF-16 unit.
        assertThat(Precedence.comparePaths("\uff5e", "\ud83d\ude00")).isNegative();
        assertThat(Precedence.comparePaths("\ud83d\ude00", "\uff5e")).isPositive();
        assertThat(Precedence.comparePaths("", "a")).isNegative();
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

    private static Reason at(String key) {
        return reason(PackCodes.DRG_SCHEMA_ERROR, "pack.yaml", List.of(key));
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
        List<Reason> all = SAMPLE.stream().map(PrecedenceTest::at).toList();
        for (Reason a : all) {
            for (Reason b : all) {
                assertThat(Integer.signum(PRECEDENCE.compare(a, b)))
                        .as(
                                "antisymmetry of %s and %s",
                                a.location().pointer(), b.location().pointer())
                        .isEqualTo(-Integer.signum(PRECEDENCE.compare(b, a)));
                for (Reason c : all) {
                    if (PRECEDENCE.compare(a, b) <= 0 && PRECEDENCE.compare(b, c) <= 0) {
                        assertThat(PRECEDENCE.compare(a, c))
                                .as(
                                        "transitivity of %s, %s, %s",
                                        a.location().pointer(),
                                        b.location().pointer(),
                                        c.location().pointer())
                                .isLessThanOrEqualTo(0);
                    }
                }
            }
        }
    }

    @Test
    void indexesSortByValueBeforeEveryOtherKeyAndLeadingZerosDoNotChangeTheValue() {
        List<Reason> sorted = new ArrayList<>(Stream.of(
                        "pack_id", "10", "Nx", "9", "07", "1a", "7", "", "12345678901234567890", "9223372036854775808")
                .map(PrecedenceTest::at)
                .toList());
        sorted.sort(PRECEDENCE);
        assertThat(sorted.stream()
                        .map(f -> Findings.segments(f.location().pointer()).get(0)))
                .containsExactly(
                        "07", "7", "9", "10", "9223372036854775808", "12345678901234567890", "pack_id", "", "1a", "Nx");
    }

    @Test
    void manyShuffledMixesSortWithoutAnExceptionAndToOneOrder() {
        Random random = new Random(20260620);
        List<Reason> reference =
                SAMPLE.stream().map(PrecedenceTest::at).sorted(PRECEDENCE).toList();
        for (int round = 0; round < 500; round++) {
            List<Reason> shuffled = new ArrayList<>(reference);
            Collections.shuffle(shuffled, random);
            shuffled.sort(PRECEDENCE);
            assertThat(shuffled).isEqualTo(reference);
        }
        // Many keys at once, the shape that made TimSort check its contract: 217 keys of "N" and "Nx" mixed.
        for (int round = 0; round < 200; round++) {
            List<Reason> keys = new ArrayList<>();
            for (int i = 0; i < 32 + random.nextInt(200); i++) {
                int n = random.nextInt(2000);
                keys.add(at(random.nextBoolean() ? Integer.toString(n) : n + "x"));
            }
            keys.sort(PRECEDENCE);
            assertThat(keys).isSortedAccordingTo(PRECEDENCE);
        }
    }
}
