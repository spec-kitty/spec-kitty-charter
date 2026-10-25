package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard.ShardRead;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RelationshipShardRoundTripTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");
    private static final Urn C = new Urn(NodeKind.TACTIC, "c");

    @Test
    void aSetOfRelationshipsWrittenAndReadBackIsTheSameSetWithItsTexts() {
        List<Relationship> relationships = List.of(
                new Relationship(A, B, RelationType.REQUIRES, Optional.of("when it rains"), Optional.of("why")),
                Relationship.of(B, C, RelationType.SUGGESTS));

        ShardRead read = RelationshipShard.read("qa", RelationshipShard.write(relationships));

        assertThat(read.findings()).isEmpty();
        assertThat(read.relationships()).containsExactlyElementsOf(relationships);
        // Non-vacuous: relation, condition and rationale survive, present-vs-absent distinct (FR-005, NFR-003).
        assertThat(read.relationships().get(0).type()).isEqualTo(RelationType.REQUIRES);
        assertThat(read.relationships().get(0).when()).contains("when it rains");
        assertThat(read.relationships().get(0).rationale()).contains("why");
        assertThat(read.relationships().get(1).type()).isEqualTo(RelationType.SUGGESTS);
        assertThat(read.relationships().get(1).when()).isEmpty();
        assertThat(read.relationships().get(1).rationale()).isEmpty();
    }

    @Test
    void everyRegistrableRelationIsWrittenInAFormTheReaderAccepts() {
        // impacts is refused on the registration path (RelationshipMerge); every other relation must read back clean,
        // or the next registration over the shard is refused as unreadable.
        List<Relationship> relationships = EnumSet.complementOf(EnumSet.of(RelationType.IMPACTS)).stream()
                .map(type -> Relationship.of(A, new Urn(NodeKind.DIRECTIVE, "to-" + type.token()), type))
                .toList();

        ShardRead read = RelationshipShard.read("qa", RelationshipShard.write(relationships));

        assertThat(read.findings()).isEmpty();
        assertThat(read.relationships()).containsExactlyElementsOf(relationships);
    }

    @Test
    void aMalformedShardSurfacesAsFindingsNotAThrow() {
        ShardRead read = RelationshipShard.read("qa", "nodes: [\n");
        assertThat(read.relationships()).isEmpty();
        assertThat(read.findings()).isNotEmpty();
    }

    @Test
    void aNonUrnEndpointInAShardIsAFindingNotASilentDrop() {
        String yaml = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: QA_404, target: directive:b, relation: requires}
                """;

        ShardRead read = RelationshipShard.read("qa", yaml);

        assertThat(read.relationships()).isEmpty();
        assertThat(read.findings()).isNotEmpty();
    }
}
