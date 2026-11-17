package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.rules.GraphBinding.GraphBuild;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraphBindingTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String REGISTERED = "registered.graph.yaml";
    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

    private static RawEdge edge(String source, String target, String relation) {
        return new RawEdge(source, target, relation, Optional.empty(), Optional.empty());
    }

    private static RawGraph shard(String file, List<RawNode> nodes, List<RawEdge> edges) {
        return new RawGraph(file, RawGraph.Shape.SHARD, nodes, edges);
    }

    private static PackQualifiedReference ref(String text) {
        return PackQualifiedReference.parse(text);
    }

    // ---- the graph of a pack's graph document -------------------------------------------------------

    @Test
    void aShardBindsItsNodesItsRelationshipsAndItsTensions() {
        GraphBuild build = GraphBinding.graph(
                "qa",
                shard(
                        SHARD,
                        List.of(new RawNode("tactic:a", "tactic", Optional.of("A"))),
                        List.of(
                                edge("tactic:a", "directive:b", "requires"),
                                edge("tactic:a", "directive:b", "in_tension_with"))));

        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().nodes()).containsExactly(new DrgNode(A, Optional.of("A")));
        assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
            assertThat(relationship.from()).isEqualTo(A);
            assertThat(relationship.to()).isEqualTo(B);
        });
        assertThat(build.graph().tensions()).containsExactly(new Tension(A, B, Optional.empty(), Optional.empty()));
    }

    @Test
    void anEdgeThatDoesNotBindIsADrgSchemaFindingAtItsPlace() {
        GraphBuild build = GraphBinding.graph(
                "qa",
                shard(
                        SHARD,
                        List.of(),
                        List.of(
                                edge("tactic:a", "directive:b", "requires"),
                                edge("tactic:a", "directive:b", "befriends"))));

        assertThat(build.graph().relationships()).hasSize(1);
        assertThat(build.findings())
                .containsExactly(Findings.at(
                        PackCodes.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of("edges", "1"),
                        "tactic.graph.yaml: unknown relation 'befriends'"));
    }

    @Test
    void anEdgeTheTableRefusesIsLeftOutOfAPacksGraphWithoutAFinding() {
        // A pack's graph documents report the table's refusal under its own code, in a pack-level pass.
        GraphBuild build = GraphBinding.graph(
                "main",
                shard(SHARD, List.of(), List.of(edge("procedure:p", "action:software-dev/implement", "requires"))));

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).isEmpty();
    }

    // ---- the registered-relationship shard ----------------------------------------------------------

    @Test
    void theRegisteredShardHoldsNoTension() {
        GraphBuild build = GraphBinding.registeredGraph(
                "qa", shard(REGISTERED, List.of(), List.of(edge("tactic:a", "directive:c", GraphBinding.TENSION))));

        assertThat(build.graph().tensions()).isEmpty();
        assertThat(build.findings())
                .containsExactly(Findings.at(
                        PackCodes.DRG_SCHEMA_ERROR,
                        REGISTERED,
                        List.of("edges", "0"),
                        "registered.graph.yaml: a registered relationship is never a tension; in_tension_with is"
                                + " authored in a pack's graph shards"));
    }

    @Test
    void theRegisteredShardReportsAnEdgeTheTableRefusesUnderTheEndpointCode() {
        GraphBuild build = GraphBinding.registeredGraph(
                "main",
                shard(
                        REGISTERED,
                        List.of(),
                        List.of(edge("procedure:p", "action:software-dev/implement", "requires"))));

        assertThat(build.graph().relationships()).isEmpty();
        assertThat(build.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.location().file()).isEqualTo(REGISTERED);
            assertThat(finding.location().pointer()).isEqualTo("/edges/0");
            assertThat(finding.detail()).startsWith("registered.graph.yaml: ");
        });
    }

    // ---- the vocabulary of an endpoint --------------------------------------------------------------

    @Test
    void theEndpointTextsKeepTheirWording() {
        assertThat(GraphBinding.TENSION).isEqualTo("in_tension_with");
        assertThat(GraphBinding.NOT_AUTHORED)
                .isEqualTo("a guard or a lifecycle is not an authored edge endpoint until its contract set lands: ");
        assertThat(GraphBinding.TERM_ENDPOINT)
                .isEqualTo("a term is an edge endpoint only as the target of a vocabulary edge: ");
    }

    // ---- a self-loop --------------------------------------------------------------------------------

    @Test
    void twoReferencesToTheSameArtifactOfTheWritingPackJoinItself() {
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("tactic:a")))
                .isTrue();
        assertThat(GraphBinding.joinsItself("qa", ref("qa::tactic:a"), ref("tactic:a")))
                .isTrue();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("qa::tactic:a")))
                .isTrue();
    }

    @Test
    void aReferenceQualifiedWithAnotherPackOrAnotherIdDoesNotJoinItself() {
        assertThat(GraphBinding.joinsItself("qa", ref("rival::tactic:a"), ref("tactic:a")))
                .isFalse();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("rival::tactic:a")))
                .isFalse();
        assertThat(GraphBinding.joinsItself("qa", ref("tactic:a"), ref("tactic:b")))
                .isFalse();
    }

    /** How each node and edge of a document binds, and the finding of one that does not (moved from the policy's test). */
    @Nested
    class Binding {
        private static final String SHARD = "tactic.graph.yaml";
        private static final String FRAGMENT = "drg/fragment.yaml";
        private static final Urn A = new Urn(NodeKind.TACTIC, "a");
        private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

        private static RawGraph shard(List<RawNode> nodes, List<RawEdge> edges) {
            return new RawGraph(SHARD, RawGraph.Shape.SHARD, nodes, edges);
        }

        private static RawGraph fragment(List<RawNode> nodes, List<RawEdge> edges) {
            return new RawGraph(FRAGMENT, RawGraph.Shape.FRAGMENT, nodes, edges);
        }

        private static RawEdge edge(String source, String target, String relation) {
            return new RawEdge(source, target, relation, Optional.empty(), Optional.empty());
        }

        private static List<String> details(GraphBuild build) {
            return build.findings().stream().map(Reason::detail).toList();
        }

        private static GraphBuild build(List<RawEdge> edges) {
            return GraphBinding.graph("qa", shard(List.of(), edges));
        }

        // ---- nodes ---------------------------------------------------------------------------------

        @Test
        void shardNodesBindToTheirUrn() {
            GraphBuild build = GraphBinding.graph(
                    "qa", shard(List.of(new RawNode("tactic:a", "tactic", Optional.of("A"))), List.of()));
            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().nodes()).containsExactly(new DrgNode(A, Optional.of("A")));
        }

        @Test
        void aShardNodeBindsWithItsProvenanceAndItsTagsInOrder() {
            GraphBuild build = GraphBinding.graph(
                    "qa",
                    shard(
                            List.of(new RawNode(
                                    "tactic:a",
                                    "tactic",
                                    Optional.empty(),
                                    Optional.of("imported"),
                                    List.of("y", "x"),
                                    Optional.empty())),
                            List.of()));

            assertThat(build.graph().nodes())
                    .containsExactly(new DrgNode(
                            A, Optional.empty(), Optional.of("imported"), List.of("y", "x"), Optional.empty()));
        }

        @Test
        void aFragmentNodeBindsWithItsBodyPath() {
            GraphBuild build = GraphBinding.graph(
                    "qa",
                    fragment(
                            List.of(new RawNode(
                                    "b",
                                    "directives",
                                    Optional.of("B"),
                                    Optional.empty(),
                                    List.of(),
                                    Optional.of("b.md"))),
                            List.of()));

            assertThat(build.graph().nodes())
                    .containsExactly(
                            new DrgNode(B, Optional.of("B"), Optional.empty(), List.of(), Optional.of("b.md")));
        }

        @Test
        void aShardNodeUrnThatDoesNotParseIsAFindingAndTheNodeIsDropped() {
            GraphBuild build = GraphBinding.graph(
                    "qa", shard(List.of(new RawNode("nonsense", "tactic", Optional.empty())), List.of()));
            assertThat(build.graph().nodes()).isEmpty();
            assertThat(build.findings())
                    .containsExactly(Findings.at(
                            PackCodes.DRG_SCHEMA_ERROR,
                            SHARD,
                            List.of("nodes", "0"),
                            "tactic.graph.yaml: node urn 'nonsense' is not <kind>:<id>"));
        }

        @Test
        void fragmentNodesBindThePluralKindAndTheIdToAUrn() {
            GraphBuild build = GraphBinding.graph(
                    "qa",
                    fragment(
                            List.of(
                                    new RawNode("a", "tactics", Optional.of("A")),
                                    new RawNode("q", "mission_types", Optional.empty())),
                            List.of()));
            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().nodes())
                    .containsExactly(
                            new DrgNode(A, Optional.of("A")), new DrgNode(Urn.missionType("q"), Optional.empty()));
        }

        @Test
        void aFragmentNodeOfAnUnknownKindIsAFinding() {
            GraphBuild build = GraphBinding.graph(
                    "qa", fragment(List.of(new RawNode("t1", "mission_steps", Optional.empty())), List.of()));
            assertThat(build.graph().nodes()).isEmpty();
            assertThat(details(build)).containsExactly("drg/fragment.yaml: node t1 has unknown kind 'mission_steps'");
        }

        @Test
        void aFragmentNodeWithAnEmptyIdIsAFindingNotAnException() {
            GraphBuild build = GraphBinding.graph(
                    "qa", fragment(List.of(new RawNode("", "directives", Optional.empty())), List.of()));
            assertThat(details(build))
                    .containsExactly("drg/fragment.yaml: a node of kind 'directives' has an empty id");
        }

        // ---- edges ---------------------------------------------------------------------------------

        @Test
        void edgesBindBothEndpointsWithTheirOptionalTexts() {
            RawEdge raw = new RawEdge("tactic:a", "directive:b", "requires", Optional.of("w"), Optional.of("r"));
            GraphBuild build = build(List.of(raw));
            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
                assertThat(relationship.from()).isEqualTo(A);
                assertThat(relationship.to()).isEqualTo(B);
                assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
                assertThat(relationship.when()).contains("w");
                assertThat(relationship.rationale()).contains("r");
            });
        }

        @Test
        void aNonUrnEndpointIsAFindingAndNotAnException() {
            GraphBuild build = build(List.of(edge("tactic:a", "QA_404", "requires")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(build.findings())
                    .containsExactly(Findings.at(
                            PackCodes.DRG_SCHEMA_ERROR,
                            SHARD,
                            List.of("edges", "0"),
                            "tactic.graph.yaml: edge endpoint 'QA_404' is not a URN"));
            assertThat(details(build(List.of(edge("nope", "tactic:a", "requires")))))
                    .containsExactly("tactic.graph.yaml: edge endpoint 'nope' is not a URN");
        }

        @Test
        void aSelfLoopEdgeIsAFindingAndNotAnException() {
            GraphBuild build = build(List.of(edge("tactic:a", "tactic:a", "requires")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build))
                    .containsExactly("tactic.graph.yaml: a relationship cannot join 'tactic:a' to itself");
        }

        @Test
        void aQualifiedEndpointOntoTheSameIdIsAnotherPacksArtifactAndNotASelfLoop() {
            GraphBuild crossPack = build(List.of(edge("rival::tactic:a", "tactic:a", "requires")));
            assertThat(details(crossPack)).isEmpty();
            assertThat(crossPack.graph().relationships()).singleElement().satisfies(relationship -> {
                assertThat(relationship.fromPack()).contains("rival");
                assertThat(relationship.to()).isEqualTo(A);
            });

            GraphBuild reference = build(List.of(edge("tactic:a", "rival::tactic:b", "requires")));
            assertThat(reference.graph().relationships()).singleElement().satisfies(relationship -> {
                assertThat(relationship.to()).isEqualTo(new Urn(NodeKind.TACTIC, "b"));
                assertThat(relationship.toPack()).contains("rival");
            });
        }

        @Test
        void anUnknownRelationIsAFinding() {
            GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "befriends")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build)).containsExactly("tactic.graph.yaml: unknown relation 'befriends'");
        }

        @Test
        void anAuthoredEdgeToOrFromAGuardOrALifecycleIsAFinding() {
            // Neither is an authored edge endpoint until its contract set lands (#156, work breakdown item 13).
            GraphBuild build = build(
                    List.of(edge("guard:a", "directive:b", "requires"), edge("tactic:a", "lifecycle:wp", "scope")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build))
                    .containsExactly(
                            "tactic.graph.yaml: " + GraphBinding.NOT_AUTHORED + "'guard:a'",
                            "tactic.graph.yaml: " + GraphBinding.NOT_AUTHORED + "'lifecycle:wp'");
            // The node path says the same: a guard or lifecycle node is not authored, in a shard or in a fragment.
            GraphBuild shardNode = GraphBinding.graph(
                    "qa", shard(List.of(new RawNode("guard:x", "guard", Optional.empty())), List.of()));
            GraphBuild fragmentNode = GraphBinding.graph(
                    "qa", fragment(List.of(new RawNode("wp", "lifecycles", Optional.empty())), List.of()));
            assertThat(shardNode.graph().nodes()).isEmpty();
            assertThat(details(shardNode))
                    .containsExactly("tactic.graph.yaml: " + GraphBinding.NOT_AUTHORED + "'guard:x'");
            assertThat(details(fragmentNode))
                    .containsExactly("drg/fragment.yaml: " + GraphBinding.NOT_AUTHORED + "'lifecycle:wp'");
        }

        @Test
        void aTermIsAnEdgeEndpointOnlyAsTheTargetOfAVocabularyEdge() {
            // Positive control: a term as the target of a vocabulary edge binds, bare or pack-qualified.
            GraphBuild valid = build(List.of(
                    edge("agent_profile:p", "term:spec-kitty-core/mission", "vocabulary"),
                    edge("tactic:a", "rival::term:spec-kitty-core/mission", "vocabulary")));
            assertThat(details(valid)).isEmpty();
            assertThat(valid.graph().relationships())
                    .extracting(relationship -> relationship.to().toString())
                    .containsExactly("term:spec-kitty-core/mission", "term:spec-kitty-core/mission");

            // A term as a source, or as the target of any other relation or of a tension, is a finding (ADR-0049 d4).
            GraphBuild build = build(List.of(
                    edge("term:g/t", "directive:b", "requires"),
                    edge("term:g/t", "glossary_context:g", "vocabulary"),
                    edge("directive:b", "term:g/t", "overrides"),
                    edge("tactic:a", "rival::term:g/t", "enhances"),
                    edge("tactic:a", "term:g/t", "in_tension_with")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(build.graph().tensions()).isEmpty();
            assertThat(details(build))
                    .containsExactly(
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT + "'term:g/t' requires 'directive:b'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT
                                    + "'term:g/t' vocabulary 'glossary_context:g'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT + "'directive:b' overrides 'term:g/t'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT
                                    + "'tactic:a' enhances 'rival::term:g/t'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT
                                    + "'tactic:a' in_tension_with 'term:g/t'");
        }

        @Test
        void aDerivedRelationAuthoredAsAnEdgeIsAFinding() {
            // implemented_by is derived from a guard's own field (ADR-0054 decision 3), never authored as an edge.
            GraphBuild build = build(List.of(edge("tactic:a", "asset:b", "implemented_by")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build))
                    .containsExactly("tactic.graph.yaml: relation 'implemented_by' is derived from an artifact's own"
                            + " field, never authored as an edge");
        }

        @Test
        void anInTensionWithEdgeBindsAsATensionAndNeverAsARelationship() {
            GraphBuild build = build(List.of(new RawEdge(
                    "tactic:a",
                    "directive:b",
                    "in_tension_with",
                    Optional.of("always"),
                    Optional.of("both stay valid"))));

            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(build.graph().tensions())
                    .containsExactly(new Tension(A, B, Optional.of("always"), Optional.of("both stay valid")));
        }

        @Test
        void aTensionAndAStructuralRelationshipOnTheSamePairBothBind() {
            GraphBuild build = build(List.of(
                    edge("tactic:a", "directive:b", "in_tension_with"), edge("directive:b", "tactic:a", "requires")));

            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().tensions()).containsExactly(new Tension(A, B, Optional.empty(), Optional.empty()));
            assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
                assertThat(relationship.from()).isEqualTo(B);
                assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
            });
        }

        @Test
        void aTensionWhoseEndpointIsNotAUrnOrThatJoinsARuleToItselfIsAFinding() {
            GraphBuild build = build(List.of(
                    edge("nonsense", "directive:b", "in_tension_with"),
                    edge("tactic:a", "tactic:a", "in_tension_with")));

            assertThat(build.graph().tensions()).isEmpty();
            assertThat(details(build))
                    .containsExactly(
                            "tactic.graph.yaml: edge endpoint 'nonsense' is not a URN",
                            "tactic.graph.yaml: a relationship cannot join 'tactic:a' to itself");
        }

        @Test
        void aRegisteredShardHoldsNoTensionSoATensionThereIsAFindingAndIsNotBound() {
            RawGraph registered = new RawGraph(
                    "registered.graph.yaml",
                    RawGraph.Shape.SHARD,
                    List.of(),
                    List.of(
                            edge("tactic:a", "directive:b", "requires"),
                            edge("tactic:a", "directive:c", "in_tension_with")));

            GraphBuild build = GraphBinding.registeredGraph("qa", registered);

            assertThat(build.graph().tensions()).isEmpty();
            assertThat(build.graph().relationships())
                    .singleElement()
                    .satisfies(relationship -> assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES));
            assertThat(build.findings())
                    .containsExactly(Findings.at(
                            PackCodes.DRG_SCHEMA_ERROR,
                            "registered.graph.yaml",
                            List.of("edges", "1"),
                            "registered.graph.yaml: a registered relationship is never a tension; in_tension_with is"
                                    + " authored in a pack's graph shards"));
            // Positive control: the same shard without the tension binds whole.
            assertThat(GraphBinding.registeredGraph(
                                    "qa",
                                    new RawGraph(
                                            "registered.graph.yaml",
                                            RawGraph.Shape.SHARD,
                                            List.of(),
                                            List.of(edge("tactic:a", "directive:b", "requires"))))
                            .findings())
                    .isEmpty();
        }

        @Test
        void anEdgeWithAFindingIsNotAddedAndItsNeighboursAre() {
            GraphBuild build = build(List.of(
                    edge("tactic:a", "directive:b", "requires"),
                    edge("tactic:a", "QA_404", "requires"),
                    edge("tactic:a", "directive:b", "suggests")));
            assertThat(build.graph().relationships())
                    .extracting(r -> r.type())
                    .containsExactly(RelationType.REQUIRES, RelationType.SUGGESTS);
            assertThat(build.findings())
                    .extracting(reason -> reason.location().pointer())
                    .containsExactly("/edges/1");
        }

        @Test
        void anEdgeReportsItsFirstFaultInTodaysOrderRelationBeforeEndpoint() {
            GraphBuild build = build(List.of(edge("nope", "directive:b", "befriends")));
            assertThat(details(build)).containsExactly("tactic.graph.yaml: unknown relation 'befriends'");
            // impacts is retired (slice F): it is an unknown relation, reported before the endpoint.
            GraphBuild second = build(List.of(edge("nope", "directive:b", "impacts")));
            assertThat(details(second)).containsExactly("tactic.graph.yaml: unknown relation 'impacts'");
        }
    }
}
