package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
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
    void aShardBindsItsNodesAndItsRelationshipsATensionAmongThem() {
        GraphBuild build = GraphBinding.graph(
                "qa",
                shard(
                        SHARD,
                        List.of(new RawNode("tactic:a", "tactic", Optional.of("A"))),
                        List.of(
                                edge("tactic:a", "directive:b", "requires"),
                                edge("tactic:a", "directive:b", "tension"))));

        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().nodes()).containsExactly(new DrgNode(A, Optional.of("A")));
        assertThat(build.graph().relationships())
                .extracting(Relationship::type, Relationship::from, Relationship::to)
                .containsExactly(tuple(RelationType.REQUIRES, A, B), tuple(RelationType.TENSION, A, B));
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
    void theRegisteredShardHoldsATensionLikeAnyRelationship() {
        GraphBuild build = GraphBinding.registeredGraph(
                "qa", shard(REGISTERED, List.of(), List.of(edge("tactic:a", "directive:c", "tension"))));

        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().relationships())
                .singleElement()
                .satisfies(relationship -> assertThat(relationship.type()).isEqualTo(RelationType.TENSION));
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
        assertThat(GraphBinding.NOT_AUTHORED)
                .isEqualTo("a guard or a lifecycle is not an authored edge endpoint until its contract set lands: ");
        assertThat(GraphBinding.TERM_ENDPOINT)
                .isEqualTo("a term is an edge endpoint only as the target of a vocabulary edge: ");
    }

    @Test
    void aRoutingCatalogIsRefusedInItsOwnWordsAndAGuardOrALifecycleInTheirs() {
        // ADR-0080 decision 5, squad A1: a catalog layers by its id; the guard's and lifecycle's wording waits on #156.
        assertThat(GraphBinding.NOT_A_GRAPH_KIND)
                .isEqualTo("a routing catalog is no graph node and no edge endpoint, it layers by its id: ");
        assertThat(GraphBinding.notAuthored(NodeKind.ROUTING_CATALOG)).isEqualTo(GraphBinding.NOT_A_GRAPH_KIND);
        assertThat(GraphBinding.notAuthored(NodeKind.GUARD)).isEqualTo(GraphBinding.NOT_AUTHORED);
        assertThat(GraphBinding.notAuthored(NodeKind.LIFECYCLE)).isEqualTo(GraphBinding.NOT_AUTHORED);
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

        /**
         * A qualifier the schema accepts but no pack id can be (#582): over 63 characters, the pack schemas' pattern
         * cannot cap it, so the binding refuses it and says why. One of 63 binds, on the same edge.
         */
        @Test
        void aQualifierOverTheLongestPackIdIsAFindingThatNamesThePackIdGrammar() {
            String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
            GraphBuild refused = build(List.of(edge("tactic:a", overCap + "::directive:b", "requires")));
            assertThat(refused.graph().relationships()).isEmpty();
            assertThat(refused.findings()).singleElement().satisfies(finding -> {
                assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
                assertThat(finding.detail())
                        .contains("edge endpoint is not a reference")
                        .contains(PackIdentifier.GRAMMAR_DETAIL);
            });

            String atCap = "p".repeat(PackIdentifier.MAX_LENGTH);
            GraphBuild bound = build(List.of(edge("tactic:a", atCap + "::directive:b", "requires")));
            assertThat(bound.findings()).isEmpty();
            assertThat(bound.graph().relationships())
                    .singleElement()
                    .satisfies(relationship -> assertThat(relationship.toPack()).contains(atCap));
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
                    edge("tactic:a", "term:g/t", "tension")));
            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build))
                    .containsExactly(
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT + "'term:g/t' requires 'directive:b'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT
                                    + "'term:g/t' vocabulary 'glossary_context:g'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT + "'directive:b' overrides 'term:g/t'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT
                                    + "'tactic:a' enhances 'rival::term:g/t'",
                            "tactic.graph.yaml: " + GraphBinding.TERM_ENDPOINT + "'tactic:a' tension 'term:g/t'");
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
        void aTensionEdgeBindsAsATensionRelationshipWithItsWords() {
            GraphBuild build = build(List.of(new RawEdge(
                    "tactic:a", "directive:b", "tension", Optional.of("always"), Optional.of("both stay valid"))));

            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().relationships()).singleElement().satisfies(relationship -> {
                assertThat(relationship.type()).isEqualTo(RelationType.TENSION);
                assertThat(relationship.from()).isEqualTo(A);
                assertThat(relationship.to()).isEqualTo(B);
                assertThat(relationship.when()).contains("always");
                assertThat(relationship.rationale()).contains("both stay valid");
            });
        }

        @Test
        void theOldSpellingInTensionWithIsAnUnknownRelation() {
            GraphBuild build = build(List.of(edge("tactic:a", "directive:b", "in_tension_with")));

            assertThat(build.graph().relationships()).isEmpty();
            assertThat(details(build)).containsExactly("tactic.graph.yaml: unknown relation 'in_tension_with'");
        }

        @Test
        void aTensionAndAStructuralRelationshipOnTheSamePairBothBind() {
            GraphBuild build = build(
                    List.of(edge("tactic:a", "directive:b", "tension"), edge("directive:b", "tactic:a", "requires")));

            assertThat(build.findings()).isEmpty();
            assertThat(build.graph().relationships())
                    .extracting(Relationship::type, Relationship::from)
                    .containsExactly(tuple(RelationType.TENSION, A), tuple(RelationType.REQUIRES, B));
        }

        @Test
        void aTensionWhoseEndpointIsNotAUrnOrThatJoinsARuleToItselfOrToAnotherVersionOfItIsAFinding() {
            GraphBuild build = build(List.of(
                    edge("nonsense", "directive:b", "tension"),
                    edge("tactic:a", "tactic:a", "tension"),
                    edge("tactic:a", "rival::tactic:a", "tension")));

            assertThat(build.graph().relationships()).isEmpty();
            assertThat(build.findings())
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    PackCodes.DRG_SCHEMA_ERROR,
                                    "tactic.graph.yaml: edge endpoint 'nonsense' is not a URN"),
                            tuple(
                                    PackCodes.TENSION_SELF,
                                    "tactic.graph.yaml: a tension cannot join 'tactic:a' to itself or to a version of"
                                            + " itself"),
                            tuple(
                                    PackCodes.TENSION_SELF,
                                    "tactic.graph.yaml: a tension cannot join 'tactic:a' to itself or to a version of"
                                            + " itself"));
            // A structural self-loop keeps its own wording and code.
            assertThat(build(List.of(edge("tactic:a", "tactic:a", "refines"))).findings())
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(tuple(
                            PackCodes.DRG_SCHEMA_ERROR,
                            "tactic.graph.yaml: a relationship cannot join 'tactic:a' to itself"));
            // Positive control: a structural edge to another pack's version of the same id binds (ADR-0053 d7).
            assertThat(build(List.of(edge("tactic:a", "rival::tactic:a", "refines")))
                            .graph()
                            .relationships())
                    .hasSize(1);
        }

        @Test
        void aTensionBetweenAGuidanceKindAndAnotherKindIsRefusedByTheTable() {
            GraphBuild refused = build(List.of(edge("tactic:a", "agent_profile:p", "tension")));
            GraphBuild bound = build(List.of(edge("tactic:a", "directive:b", "tension")));

            assertThat(refused.graph().relationships()).isEmpty();
            assertThat(bound.graph().relationships()).hasSize(1);
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
