package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The shard and fragment writers write every field the document shape holds, in the packs' key order, and refuse a
 * field the shape cannot hold instead of dropping it (#235, #236; ADR-0036, ADR-0045).
 */
class GraphShardWriterTest {

    private static final Urn A = new Urn(NodeKind.DIRECTIVE, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

    private static DrgNode node(Urn urn, Optional<String> provenance, List<String> tags, Optional<String> bodyPath) {
        return new DrgNode(urn, Optional.empty(), provenance, tags, bodyPath);
    }

    // ---- shard ---------------------------------------------------------------------------------

    @Test
    void aShardNodeIsWrittenWithItsProvenanceAndTagsInKeyOrderAndANodeWithoutThemHasNeitherKey() {
        DoctrineGraph graph = new DoctrineGraph(
                List.of(
                        node(A, Optional.of("imported"), List.of("b", "a"), Optional.empty()),
                        new DrgNode(B, Optional.of("B"))),
                List.of());

        String written = GraphShardWriter.write(graph);

        assertThat(written)
                .contains("- urn: directive:a\n  kind: directive\n  provenance: imported\n  tags:\n  - b\n  - a\n")
                .contains("- urn: directive:b\n  kind: directive\n  label: B\nedges: []\n")
                .doesNotContain("provenance: null", "tags: []");
        // The same fixture, two nodes: provenance and tags are written exactly once, for the node that has them.
        assertThat(written.split("provenance:", -1)).hasSize(2);
        assertThat(written.split("tags:", -1)).hasSize(2);
    }

    @Test
    void aTensionIsWrittenAsTheInTensionWithEdgeItWasWithNoStrength() {
        DoctrineGraph graph = new DoctrineGraph(
                List.of(),
                List.of(Relationship.of(B, A, RelationType.REQUIRES)),
                List.of(new Tension(A, B, Optional.of("always"), Optional.of("both stay valid"))));

        String written = GraphShardWriter.write(graph);

        assertThat(written)
                .contains("- source: directive:b\n  target: directive:a\n  relation: requires\n")
                .contains("""
                        - source: directive:a
                          target: directive:b
                          relation: in_tension_with
                          when: always
                          reason: both stay valid
                        """)
                .doesNotContain("impacts");
    }

    @Test
    void aShardCannotHoldABodyPathSoANodeWithOneIsRefusedNotDropped() {
        DoctrineGraph graph =
                new DoctrineGraph(List.of(node(A, Optional.empty(), List.of(), Optional.of("a.md"))), List.of());

        assertThatThrownBy(() -> GraphShardWriter.write(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directive:a")
                .hasMessageContaining("body_path");
    }

    // ---- fragment ------------------------------------------------------------------------------

    @Test
    void aFragmentIsWrittenWithIdsPluralKindsTitlesAndBodyPathsAndNoHeader() {
        DoctrineGraph graph = new DoctrineGraph(
                List.of(
                        new DrgNode(A, Optional.of("A"), Optional.empty(), List.of(), Optional.of("a.md")),
                        new DrgNode(new Urn(NodeKind.AGENT_PROFILE, "p"), Optional.empty())),
                List.of(new Relationship(A, B, RelationType.SUGGESTS, Optional.empty(), Optional.of("why"))),
                List.of(new Tension(A, B, Optional.empty(), Optional.of("both"))));

        String written = GraphShardWriter.writeFragment(graph);

        assertThat(written)
                .startsWith("nodes:\n")
                .contains("- id: a\n  kind: directives\n  title: A\n  body_path: a.md\n")
                .contains("- id: p\n  kind: agent_profiles\n")
                .contains("- source: directive:a\n  target: directive:b\n  relation: suggests\n  reason: why\n")
                .contains("- source: directive:a\n  target: directive:b\n  relation: in_tension_with\n  reason: both\n")
                .doesNotContain("schema_version", "generated_at");
        assertThat(GraphDocumentReader.read(written, Shape.FRAGMENT, "drg/fragment.yaml"))
                .isInstanceOf(FileRead.Read.class);
    }

    @Test
    void aFragmentRoundTripsWithItsBodyPathAndTheGateComparatorSeesADroppedOne() {
        String source = """
                nodes:
                - id: QA_001
                  kind: directives
                  title: Traceability
                  body_path: directives/qa-001.directive.yaml
                edges:
                - source: directive:QA_001
                  target: directive:DIRECTIVE_010
                  relation: suggests
                  reason: because
                """;
        DoctrineGraph graph = PackPolicy.graph(
                        "qa",
                        ((FileRead.Read<RawGraph>)
                                        GraphDocumentReader.read(source, Shape.FRAGMENT, "drg/fragment.yaml"))
                                .value())
                .graph();

        String written = GraphShardWriter.writeFragment(graph);

        MeaningComparator comparator = MeaningComparator.adr0036();
        assertThat(comparator.compareTexts("org-fragment", source, written)).isEmpty();
        // Positive control: the comparator would see the body path go missing.
        assertThat(comparator.compareTexts(
                        "org-fragment", source, written.replace("  body_path: directives/qa-001.directive.yaml\n", "")))
                .isPresent();
    }

    @Test
    void aFragmentCannotHoldProvenanceTagsOrAConditionSoEachIsRefusedNotDropped() {
        assertThatThrownBy(() -> GraphShardWriter.writeFragment(
                        new DoctrineGraph(List.of(node(A, Optional.of("p"), List.of(), Optional.empty())), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provenance");
        assertThatThrownBy(() -> GraphShardWriter.writeFragment(new DoctrineGraph(
                        List.of(node(A, Optional.empty(), List.of("t"), Optional.empty())), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags");
        assertThatThrownBy(() -> GraphShardWriter.writeFragment(new DoctrineGraph(
                        List.of(),
                        List.of(new Relationship(A, B, RelationType.REQUIRES, Optional.of("w"), Optional.empty())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("when");
        assertThatThrownBy(() -> GraphShardWriter.writeFragment(new DoctrineGraph(
                        List.of(), List.of(), List.of(new Tension(A, B, Optional.of("w"), Optional.empty())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("when");
    }
}
