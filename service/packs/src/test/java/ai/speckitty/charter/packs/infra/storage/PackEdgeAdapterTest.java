package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.domain.PackEdge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file-system adapter of the edge port (ADR-0063): the edges of a pack's root graph shards in file-name order, the
 * registered shard among them, each in document order; none for a pack with no graph document; and a refusal, not a
 * partial answer, when a graph document cannot be read or names a relation the service does not know.
 */
@DisplayName("The edge port's file-system adapter (ADR-0063)")
class PackEdgeAdapterTest {

    private static final String PACK = "pack-descriptor-only";

    @Test
    @DisplayName("a pack with no graph document has no edge")
    void aPackWithNoGraphDocumentHasNoEdge(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);

        assertThat(over(pack).edges(new PackIdentifier(PACK))).isEmpty();
    }

    @Test
    @DisplayName("the root shards are read in file-name order, the registered shard among them")
    void theRootShardsAreReadInFileNameOrder(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve("tactic.graph.yaml"), shard("tactic:t", "directive:D1", "suggests"));
        Files.writeString(pack.resolve("registered.graph.yaml"), shard("directive:D2", "directive:D1", "requires"));
        Files.writeString(pack.resolve("directive.graph.yaml"), shard("directive:D1", "tactic:t", "requires"));

        assertThat(over(pack).edges(new PackIdentifier(PACK)))
                .containsExactly(
                        new PackEdge("directive:D1", "tactic:t", RelationType.REQUIRES),
                        new PackEdge("directive:D2", "directive:D1", RelationType.REQUIRES),
                        new PackEdge("tactic:t", "directive:D1", RelationType.SUGGESTS));
    }

    @Test
    @DisplayName("a graph document that cannot be read refuses the read, while a readable one is answered")
    void anUnreadableGraphDocumentRefusesTheRead(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve("directive.graph.yaml"), shard("directive:D1", "tactic:t", "requires"));
        assertThat(over(pack).edges(new PackIdentifier(PACK))).hasSize(1);

        Files.writeString(pack.resolve("tactic.graph.yaml"), "edges: [unclosed\n");

        assertRefused(over(pack));
    }

    @Test
    @DisplayName("an edge naming a relation the service does not know refuses the read")
    void anUnknownRelationRefusesTheRead(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve("directive.graph.yaml"), shard("directive:D1", "tactic:t", "befriends"));

        assertRefused(over(pack));
    }

    private static void assertRefused(DomainRepositoryService repository) {
        assertThatThrownBy(() -> repository.edges(new PackIdentifier(PACK)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    private static String shard(String source, String target, String relation) {
        return """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: %s, target: %s, relation: %s}
                """.formatted(source, target, relation);
    }

    private static DomainRepositoryService over(Path directory) {
        PackLocation location = new PackLocation(PACK, PackLevel.ORG, directory);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }
}
