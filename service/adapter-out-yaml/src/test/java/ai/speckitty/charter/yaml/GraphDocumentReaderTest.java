package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import ai.speckitty.charter.yaml.GraphDocumentReader.Shape;
import org.junit.jupiter.api.Test;

class GraphDocumentReaderTest {

    @Test
    void readsAFragmentWithBareIdsAndPluralKinds() {
        DoctrineGraph graph = GraphDocumentReader.read("""
                pack_name: qa
                nodes:
                - id: QA_001
                  kind: directives
                  title: Traceability
                edges:
                - source: QA_001
                  target: directive:DIRECTIVE_010
                  relation: suggests
                """, Shape.FRAGMENT, "qa/drg/fragment.yaml");
        Urn qa = new Urn(NodeKind.DIRECTIVE, "QA_001");
        assertThat(graph.node(qa).flatMap(n -> n.label())).contains("Traceability");
        assertThat(graph.outgoing(qa, Relation.SUGGESTS))
                .singleElement()
                .satisfies(e -> assertThat(e.target()).hasToString("directive:DIRECTIVE_010"));
    }

    @Test
    void writingAGraphAndReadingItBackGivesTheSameGraph() {
        DoctrineGraph graph = GraphDocumentReader.read("""
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - urn: tactic:t
                  kind: tactic
                  label: 'A: label'
                edges:
                - source: tactic:t
                  target: directive:d
                  relation: requires
                  when: always
                  reason: because
                """, Shape.SHARD, "s");
        assertThat(GraphDocumentReader.read(GraphShardWriter.write(graph), Shape.SHARD, "w"))
                .isEqualTo(graph);
    }

    @Test
    void anImpactsEdgeCarriesItsSignedStrengthAndSurvivesAWriteAndReRead() {
        DoctrineGraph graph = GraphDocumentReader.read("""
                edges:
                - source: tactic:t
                  target: tactic:u
                  relation: impacts
                  impacts: 0.75
                - {source: tactic:u, target: tactic:t, relation: impacts, impacts: -1}
                """, Shape.SHARD, "s");
        Urn t = new Urn(NodeKind.TACTIC, "t");
        Urn u = new Urn(NodeKind.TACTIC, "u");
        assertThat(graph.outgoing(t, Relation.IMPACTS))
                .singleElement()
                .satisfies(e -> assertThat(e.impacts()).contains(0.75));
        assertThat(graph.outgoing(u, Relation.IMPACTS))
                .singleElement()
                .satisfies(e -> assertThat(e.impacts()).contains(-1.0));
        assertThat(GraphDocumentReader.read(GraphShardWriter.write(graph), Shape.SHARD, "w"))
                .isEqualTo(graph);
    }

    @Test
    void aLegacyInTensionWithEdgeIsReadAsImpactsWithStrengthMinusOne() {
        DoctrineGraph graph = GraphDocumentReader.read("""
                edges:
                - source: directive:a
                  target: directive:b
                  relation: in_tension_with
                  reason: both decide the review depth
                """, Shape.SHARD, "s");
        assertThat(graph.edges()).singleElement().satisfies(e -> {
            assertThat(e.relation()).isEqualTo(Relation.IMPACTS);
            assertThat(e.impacts()).contains(-1.0);
            assertThat(e.reason()).contains("both decide the review depth");
        });
    }

    @Test
    void anImpactsStrengthIsRequiredOnImpactsEdgesInRangeAndRefusedOnOtherEdges() {
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: impacts}\n",
                "s: an impacts edge is missing 'impacts'");
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: 1.5}\n",
                "s: 'impacts' 1.5 is outside [-1, 1]");
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: .nan}\n",
                "s: 'impacts' NaN is outside [-1, 1]");
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: strong}\n",
                "s: 'impacts' must be a number");
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: scope, impacts: 0.5}\n",
                "s: a scope edge has no 'impacts'");
        assertRefusedWith(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: in_tension_with, impacts: -0.5}\n",
                "s: an in_tension_with edge has no 'impacts'");
    }

    @Test
    void refusalsAreNamed() {
        assertRefused("nodes: []\nedges: []\nsurprise: 1\n", Shape.SHARD, RefusalCode.DRG_SCHEMA_ERROR);
        assertRefused("nodes:\n- urn: tactic:t\n  kind: directive\n", Shape.SHARD, RefusalCode.DRG_SCHEMA_ERROR);
        assertRefused(
                "edges:\n- {source: a:b, target: tactic:t, relation: requires}\n",
                Shape.SHARD,
                RefusalCode.UNRESOLVED_EDGE_ENDPOINT);
        assertRefused(
                "edges:\n- {source: tactic:t, target: tactic:u, relation: inspires}\n",
                Shape.SHARD,
                RefusalCode.DRG_SCHEMA_ERROR);
        assertRefused("nodes: [\n", Shape.SHARD, RefusalCode.PACK_UNREADABLE);
        assertRefused("nodes:\n- {id: x, kind: widgets}\n", Shape.FRAGMENT, RefusalCode.DRG_SCHEMA_ERROR);
        assertRefused("nodes:\n- {id: '', kind: directives}\n", Shape.FRAGMENT, RefusalCode.DRG_SCHEMA_ERROR);
    }

    @Test
    void aFragmentNodeWithAnEmptyIdIsRefusedAsASchemaError() {
        assertThatThrownBy(
                        () -> GraphDocumentReader.read("nodes:\n- {id: '', kind: directives}\n", Shape.FRAGMENT, "t"))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("t: a node of kind 'directives' has an empty id");
    }

    @Test
    void aMappingWithANonStringKeyOrAValueThatIsNoMappingIsRefusedAsASchemaError() {
        assertThatThrownBy(() -> GraphDocumentReader.read("nodes:\n- {1: x}\n", Shape.SHARD, "t"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR))
                .hasMessage("t node: expected a mapping");
        assertThatThrownBy(() -> GraphDocumentReader.read("- a\n", Shape.SHARD, "t"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR))
                .hasMessage("t: expected a mapping");
    }

    private static void assertRefusedWith(String text, String message) {
        assertThatThrownBy(() -> GraphDocumentReader.read(text, Shape.SHARD, "s"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR))
                .hasMessage(message);
    }

    private static void assertRefused(String text, Shape shape, RefusalCode code) {
        assertThatThrownBy(() -> GraphDocumentReader.read(text, shape, "t"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
    }
}
