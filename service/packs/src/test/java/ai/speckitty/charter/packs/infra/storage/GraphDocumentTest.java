package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DRG documents as authored")
class GraphDocumentTest {

    private static final GraphDocument.Edge A = new GraphDocument.Edge(
            "tactic:a", "directive:b", "requires", Optional.empty(), Optional.empty(), Optional.empty());
    private static final GraphDocument.Edge B = new GraphDocument.Edge(
            "tactic:c", "directive:d", "suggests", Optional.of("always"), Optional.of("why"), Optional.of(0.5));

    @Test
    @DisplayName("edges compare in canonical form: order and duplicates do not count")
    void edgesCompareCanonically() {
        var left = new GraphDocument.Fragment("drg/fragment.yaml", List.of(), List.of(A, B, A));
        var right = new GraphDocument.Fragment("drg/fragment.yaml", List.of(), List.of(B, A));

        assertThat(left.canonicalEdges()).isEqualTo(right.canonicalEdges());
        assertThat(left).isNotEqualTo(right);
    }

    @Test
    @DisplayName("a shard with every field reads back equal after a write")
    void aShardRoundTrips() {
        var shard = new GraphDocument.Shard(
                "x.graph.yaml",
                "1.0",
                "STATIC",
                Optional.of("drg-migration-v1"),
                List.of(
                        new GraphDocument.ShardNode(
                                "tactic:c", "tactic", Optional.of("C"), Optional.of("hand"), List.of("t", "t")),
                        new GraphDocument.ShardNode(
                                "directive:d", "directive", Optional.empty(), Optional.empty(), List.of())),
                List.of(B, A));

        FileRead<GraphDocument> read =
                GraphDocumentYaml.read(GraphDocumentYaml.write(shard), Shape.SHARD, "x.graph.yaml");

        assertThat(read).isEqualTo(new FileRead.Read<GraphDocument>("x.graph.yaml", shard));
    }

    @Test
    @DisplayName("a fragment with every field reads back equal after a write")
    void aFragmentRoundTrips() {
        var fragment = new GraphDocument.Fragment(
                "drg/fragment.yaml",
                List.of(new GraphDocument.FragmentNode(
                        "D", "directives", Optional.of("Title"), Optional.of("directives/d.directive.yaml"))),
                List.of(new GraphDocument.Edge(
                        "directive:D", "tactic:c", "suggests", Optional.empty(), Optional.of("r"), Optional.of(1.0))));

        FileRead<GraphDocument> read =
                GraphDocumentYaml.read(GraphDocumentYaml.write(fragment), Shape.FRAGMENT, "drg/fragment.yaml");

        assertThat(read).isEqualTo(new FileRead.Read<GraphDocument>("drg/fragment.yaml", fragment));
    }

    @Test
    @DisplayName("an unknown field is refused, as the schema says")
    void anUnknownFieldIsRefused() {
        String text = "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\nsurprise: 1\n";

        assertThat(GraphDocumentYaml.read(text, Shape.SHARD, "x.graph.yaml")).isInstanceOf(FileRead.Unreadable.class);
    }
}
