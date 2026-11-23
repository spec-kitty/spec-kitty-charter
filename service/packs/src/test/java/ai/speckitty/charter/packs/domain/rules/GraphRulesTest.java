package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("GraphRules (#554, T036)")
class GraphRulesTest {

    private static final String SHARD = "tactic.graph.yaml";

    private static final String FRAGMENT = "drg/fragment.yaml";

    private static RawEdge edge(String source, String target, String relation) {
        return new RawEdge(source, target, relation, Optional.empty(), Optional.empty());
    }

    private static FileRead<RawGraph> shard(List<RawNode> nodes, RawEdge... edges) {
        return new FileRead.Read<>(SHARD, new RawGraph(SHARD, RawGraph.Shape.SHARD, nodes, List.of(edges)));
    }

    private static FileRead<RawGraph> fragment(RawEdge... edges) {
        return new FileRead.Read<>(
                FRAGMENT, new RawGraph(FRAGMENT, RawGraph.Shape.FRAGMENT, List.of(), List.of(edges)));
    }

    private static FileRead<RawGraph> fragment(List<RawNode> nodes, RawEdge... edges) {
        return new FileRead.Read<>(FRAGMENT, new RawGraph(FRAGMENT, RawGraph.Shape.FRAGMENT, nodes, List.of(edges)));
    }

    private static FileRead<RawGraph> unreadable(String file) {
        return new FileRead.Unreadable<>(file, new ParseFailure(file, "not YAML"), List.of());
    }

    /** Facts of the pack {@code qa}, whose descriptor names it {@code declared}, with the graph documents given. */
    private static PackContents graphs(List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment) {
        return holding(List.of(), shards, fragment);
    }

    /** As {@link #graphs}, with the artifacts the pack holds, each {@code kind:id}. */
    private static PackContents holding(
            List<String> held, List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Read<>(
                        "pack.yaml",
                        new PackDescriptor(
                                "01ARWG13C00000000000001ABC",
                                "1.0.0",
                                Optional.empty(),
                                "declared",
                                Optional.of(PackLevel.ORG),
                                Optional.empty())),
                shards,
                fragment,
                held.stream()
                        .map(urn -> new ArtifactFacts(
                                urn.substring(0, urn.indexOf(':')) + "s/" + urn + ".yaml",
                                urn.substring(0, urn.indexOf(':')),
                                urn.substring(urn.indexOf(':') + 1)))
                        .toList(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("a node that does not bind is one drg_schema_error at its place, in GraphBinding's words")
    void aNodeThatDoesNotBindIsOneFindingAtItsPlace() {
        PackContents contents = graphs(
                List.of(shard(List.of(new RawNode("not-a-urn", "tactic", Optional.empty())))),
                new FileRead.Absent<>(FRAGMENT));

        assertThat(GraphRules.UNBOUND.apply(contents)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.isFailing()).isTrue();
            assertThat(finding.location()).isEqualTo(Location.in(SHARD).at("/nodes/0"));
            assertThat(finding.detail()).isEqualTo(SHARD + ": node urn 'not-a-urn' is not <kind>:<id>");
        });
    }

    @Test
    @DisplayName("a document the read refused holds no fact, so no graph rule judges it")
    void aRefusedDocumentIsJudgedByNoGraphRule() {
        PackContents contents =
                graphs(List.of(unreadable(SHARD)), fragment(edge("procedure:p", "action:a/b", "requires")));

        assertThat(GraphRules.UNBOUND.apply(contents)).isEmpty();
        assertThat(GraphRules.ENDPOINT_NOT_ALLOWED.apply(contents))
                .extracting(reason -> reason.location().file())
                .containsExactly(FRAGMENT);
        assertThat(GraphRules.graphsRead(contents)).isFalse();
        assertThat(GraphRules.graphsRead(graphs(List.of(), new FileRead.Absent<>(FRAGMENT))))
                .as("an absent document is no unreadable one")
                .isTrue();
    }

    @Test
    @DisplayName("an edge the bridge refuses is one violation at /edges/<n>: the source rule's first, then the table's")
    void theSourceRefusalsComeBeforeTheTables() {
        PackContents contents = graphs(
                List.of(shard(
                        List.of(),
                        edge("procedure:p", "action:a/b", "requires"),
                        edge("template:t", "directive:d", "overrides"))),
                new FileRead.Absent<>(FRAGMENT));

        List<Reason> findings = GraphRules.ENDPOINT_NOT_ALLOWED.apply(contents);

        assertThat(findings)
                .extracting(Reason::code, Reason::location)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE,
                                Location.in(SHARD).at("/edges/1")),
                        org.assertj.core.groups.Tuple.tuple(
                                PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED,
                                Location.in(SHARD).at("/edges/0")));
        assertThat(findings.get(1).detail()).isEqualTo(SHARD + ": a requires edge may not connect procedure to action");
        assertThat(findings).allSatisfy(finding -> assertThat(finding.detail()).startsWith(SHARD + ": "));
    }

    @Test
    @DisplayName(
            "an enhancement edge of a step enhancement the pack holds, by its declared name, is the fragment rules'")
    void anEnhancementEdgeOfAHeldEnhancementIsNotTheTables() {
        RawEdge own = edge("declared::step_enhancement:e", "directive:d", "enhances");
        PackContents bare = graphs(List.of(shard(List.of(), own)), new FileRead.Absent<>(FRAGMENT));
        PackContents holding = new PackContents(
                bare.root(),
                bare.descriptor(),
                bare.shards(),
                bare.fragment(),
                List.of(),
                List.of(),
                new StepEnhancementFacts(
                        List.of(new StepEnhancementFacts.Enhancement(
                                "missions/t/enhancements/e.step-enhancement.yaml", "t", "e", List.of())),
                        List.of(),
                        Set.of(),
                        Set.of(),
                        List.of()),
                List.of(),
                List.of(),
                List.of());

        assertThat(GraphRules.ENDPOINT_NOT_ALLOWED.apply(bare)).hasSize(1);
        assertThat(GraphRules.ENDPOINT_NOT_ALLOWED.apply(holding)).isEmpty();
    }

    @Test
    @DisplayName(
            "the documents are judged in merge order, the shards first, then the fragment; the ruleset is both rules")
    void theDocumentsAreJudgedInMergeOrder() {
        RawEdge refused = edge("procedure:p", "action:a/b", "requires");
        PackContents contents = graphs(List.of(shard(List.of(), refused)), fragment(refused));

        assertThat(GraphRules.ENDPOINT_NOT_ALLOWED.apply(contents))
                .extracting(reason -> reason.location().file())
                .containsExactly(SHARD, FRAGMENT);
        List<Reason> both = new ArrayList<>(GraphRules.UNBOUND.apply(contents));
        both.addAll(GraphRules.ENDPOINT_NOT_ALLOWED.apply(contents));
        assertThat(GraphRules.RULES.apply(contents)).isEqualTo(both);
        assertThat(GraphRules.documents(contents))
                .containsExactly(contents.shards().getFirst(), contents.fragment());
    }

    private static final Set<String> GRAPH_TOKENS = Set.of(
            PackCodes.DRG_SCHEMA_ERROR.value(),
            PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED.value(),
            PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE.value());

    static List<Planted> expecting() {
        return PlantedFixtures.expecting(GRAPH_TOKENS);
    }

    @ParameterizedTest
    @MethodSource("expecting")
    @DisplayName("a plant's expected.json is the oracle of the rules' findings, less the read faults of the same codes")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        List<Expected> expected = new ArrayList<>(planted.expected().orElseThrow().stream()
                .filter(finding -> GRAPH_TOKENS.contains(finding.code()))
                .toList());
        planted.tree().readFaults().stream().map(Expected::of).forEach(expected::remove);

        assertThat(GraphRules.RULES.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    // ---- tension consistency (ADR-0045 decisions 1 and 6) --------------------------------------------------------

    private static final List<String> OWNS_AB = List.of("directive:A", "directive:B");

    private static List<Reason> tensions(List<String> held, RawEdge... edges) {
        return GraphRules.TENSION_CONSISTENCY.apply(
                holding(held, List.of(shard(List.of(), edges)), new FileRead.Absent<>(FRAGMENT)));
    }

    @Test
    @DisplayName("a canonical tension beside a requires on the same pair is consistent")
    void aCanonicalTensionBesideARequiresIsConsistent() {
        assertThat(tensions(
                        OWNS_AB,
                        edge("directive:A", "directive:B", "tension"),
                        edge("directive:B", "directive:A", "requires")))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "a second tension on the same unordered pair, in another document, is tension_duplicated at the second")
    void aSecondTensionOnThePairIsDuplicated() {
        PackContents contents = holding(
                OWNS_AB,
                List.of(shard(List.of(), edge("directive:A", "directive:B", "tension"))),
                fragment(edge("directive:A", "directive:B", "tension")));

        assertThat(GraphRules.TENSION_CONSISTENCY.apply(contents))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.TENSION_DUPLICATED);
                    assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
                    assertThat(finding.location())
                            .isEqualTo(Location.in(FRAGMENT).at("/edges/0"));
                    assertThat(finding.detail())
                            .contains("directive:A and directive:B")
                            .contains(SHARD + " /edges/0");
                });
        // Positive control: the same two documents with tensions on two different pairs.
        assertThat(GraphRules.TENSION_CONSISTENCY.apply(holding(
                        List.of("directive:A", "directive:B", "directive:C"),
                        List.of(shard(List.of(), edge("directive:A", "directive:B", "tension"))),
                        fragment(edge("directive:A", "directive:C", "tension")))))
                .isEmpty();
    }

    @Test
    @DisplayName("two owned sides written larger first is tension_orientation, naming the expected source")
    void twoOwnedSidesLargerFirstIsReversed() {
        assertThat(tensions(OWNS_AB, edge("directive:B", "directive:A", "tension")))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.TENSION_ORIENTATION);
                    assertThat(finding.location()).isEqualTo(Location.in(SHARD).at("/edges/0"));
                    assertThat(finding.detail()).contains("its source is directive:A, the smaller of the two");
                });
    }

    @Test
    @DisplayName("an owned side that is the target is tension_orientation, whatever the order of the URNs")
    void theOwnedSideIsTheSource() {
        // The pack owns B only; A is another pack's, so B is the source although A is the smaller.
        assertThat(tensions(List.of("directive:B"), edge("base::directive:A", "directive:B", "tension")))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.TENSION_ORIENTATION);
                    assertThat(finding.detail()).contains("its source is directive:B, the side the pack holds");
                });
        assertThat(tensions(List.of("directive:B"), edge("directive:B", "base::directive:A", "tension")))
                .isEmpty();
    }

    @Test
    @DisplayName("a reference qualified with the pack's own name is owned; a bare one to an artifact it lacks is not")
    void ownershipIsTheHeldArtifactUnderABareOrOwnName() {
        assertThat(tensions(List.of("directive:B"), edge("declared::directive:B", "base::directive:A", "tension")))
                .isEmpty();
        // directive:A is bare but not held: two foreign sides, so the smaller first is canonical, and the other way is
        // not.
        assertThat(tensions(List.of(), edge("directive:A", "base::directive:B", "tension")))
                .isEmpty();
        assertThat(tensions(List.of(), edge("base::directive:B", "directive:A", "tension")))
                .extracting(Reason::code)
                .containsExactly(PackCodes.TENSION_ORIENTATION);
    }

    @Test
    @DisplayName("a tension between two artifacts the pack owns neither of is allowed, smaller first")
    void aFullyForeignTensionIsAllowedSmallerFirst() {
        assertThat(tensions(List.of(), edge("base::directive:X", "base::directive:Y", "tension")))
                .isEmpty();
        assertThat(tensions(List.of(), edge("base::directive:Y", "base::directive:X", "tension")))
                .extracting(Reason::code)
                .containsExactly(PackCodes.TENSION_ORIENTATION);
    }

    @Test
    @DisplayName("a tension of an artifact with itself is left to the binding, which reports tension_self")
    void aSelfTensionIsTheBindingsFinding() {
        PackContents contents = holding(
                OWNS_AB,
                List.of(shard(List.of(), edge("directive:A", "rival::directive:A", "tension"))),
                new FileRead.Absent<>(FRAGMENT));

        assertThat(GraphRules.TENSION_CONSISTENCY.apply(contents)).isEmpty();
        assertThat(GraphRules.UNBOUND.apply(contents)).extracting(Reason::code).containsExactly(PackCodes.TENSION_SELF);
    }

    // ---- a file-less anti_pattern node (ADR-0052 decision 14, ADR-0053 decision 31; FR-014) -----------------------

    private static String withoutFile(String file, String id) {
        return file + ": anti_pattern:" + id + " is declared with no file; an anti-pattern is held by anti_patterns/"
                + id + ".anti-pattern.yaml in the same pack";
    }

    @Test
    @DisplayName(
            "a shard anti_pattern node with no file is one drg_schema_error at /nodes/<n>, naming the file it lacks")
    void aShardAntiPatternNodeWithNoFileIsRefused() {
        PackContents contents = graphs(
                List.of(shard(List.of(
                        new RawNode("tactic:t", "tactic", Optional.empty()),
                        new RawNode("anti_pattern:x", "anti_pattern", Optional.empty())))),
                new FileRead.Absent<>(FRAGMENT));

        assertThat(GraphRules.ANTI_PATTERN_WITHOUT_FILE.apply(contents))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
                    assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
                    assertThat(finding.location()).isEqualTo(Location.in(SHARD).at("/nodes/1"));
                    assertThat(finding.detail()).isEqualTo(withoutFile(SHARD, "x"));
                });
    }

    @Test
    @DisplayName("a fragment anti_patterns node with no file is the same finding, at the fragment")
    void aFragmentAntiPatternNodeWithNoFileIsRefused() {
        PackContents contents =
                graphs(List.of(), fragment(List.of(new RawNode("x", "anti_patterns", Optional.empty()))));

        assertThat(GraphRules.ANTI_PATTERN_WITHOUT_FILE.apply(contents))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
                    assertThat(finding.location())
                            .isEqualTo(Location.in(FRAGMENT).at("/nodes/0"));
                    assertThat(finding.detail()).isEqualTo(withoutFile(FRAGMENT, "x"));
                });
    }

    @Test
    @DisplayName("an anti_pattern node beside its file, an action node and a file-less tactic node give nothing")
    void aNodeWithItsFileAndTheOtherKindsGiveNothing() {
        PackContents contents = holding(
                List.of("anti_pattern:x"),
                List.of(shard(List.of(
                        new RawNode("anti_pattern:x", "anti_pattern", Optional.empty()),
                        new RawNode("action:software-dev/implement", "action", Optional.empty()),
                        new RawNode("tactic:declared-only", "tactic", Optional.empty())))),
                fragment(List.of(
                        new RawNode("x", "anti_patterns", Optional.empty()),
                        new RawNode("declared-only", "tactics", Optional.empty()))));

        assertThat(GraphRules.ANTI_PATTERN_WITHOUT_FILE.apply(contents)).isEmpty();
    }

    @Test
    @DisplayName("a rejects edge to another pack's anti-pattern is no node of this pack, so this rule says nothing")
    void aQualifiedRejectsTargetIsNoNode() {
        PackContents contents = graphs(
                List.of(shard(List.of(), edge("styleguide:s", "other::anti_pattern:x", "rejects"))),
                fragment(edge("styleguide:s", "anti_pattern:y", "rejects")));

        assertThat(GraphRules.ANTI_PATTERN_WITHOUT_FILE.apply(contents)).isEmpty();
    }

    @Test
    @DisplayName("two file-less nodes in two documents are two findings, the shard's first, after the tension findings")
    void twoFileLessNodesAreTwoFindingsInMergeOrder() {
        PackContents contents = holding(
                List.of("directive:A", "directive:B"),
                List.of(shard(
                        List.of(new RawNode("anti_pattern:x", "anti_pattern", Optional.empty())),
                        edge("directive:B", "directive:A", "tension"))),
                fragment(List.of(new RawNode("y", "anti_patterns", Optional.empty()))));

        assertThat(GraphRules.ANTI_PATTERN_WITHOUT_FILE.apply(contents))
                .extracting(Reason::location, Reason::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Location.in(SHARD).at("/nodes/0"), withoutFile(SHARD, "x")),
                        org.assertj.core.groups.Tuple.tuple(
                                Location.in(FRAGMENT).at("/nodes/0"), withoutFile(FRAGMENT, "y")));
        assertThat(GraphRules.RULES.apply(contents))
                .extracting(Reason::code)
                .containsExactly(PackCodes.TENSION_ORIENTATION, PackCodes.DRG_SCHEMA_ERROR, PackCodes.DRG_SCHEMA_ERROR);
    }
}
