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
    }

    private static void assertRefused(String text, Shape shape, RefusalCode code) {
        assertThatThrownBy(() -> GraphDocumentReader.read(text, shape, "t"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
    }
}
