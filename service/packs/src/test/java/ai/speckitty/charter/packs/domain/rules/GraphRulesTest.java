package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
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

    private static FileRead<RawGraph> unreadable(String file) {
        return new FileRead.Unreadable<>(file, new ParseFailure(file, "not YAML"), List.of());
    }

    /** Facts of the pack {@code qa}, whose descriptor names it {@code declared}, with the graph documents given. */
    private static PackContents graphs(List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment) {
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
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
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
}
