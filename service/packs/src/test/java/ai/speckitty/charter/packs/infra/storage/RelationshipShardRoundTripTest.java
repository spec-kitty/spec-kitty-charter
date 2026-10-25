package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard.ShardRead;
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
    void upsertingTheSamePairTwiceKeepsExactlyOneWithTheSecondRegistration() {
        Relationship first = new Relationship(A, B, RelationType.REQUIRES, Optional.of("first"), Optional.empty());
        Relationship second =
                new Relationship(A, B, RelationType.SUGGESTS, Optional.of("second"), Optional.of("because"));

        List<Relationship> merged = RelationshipShard.upsert(RelationshipShard.upsert(List.of(), first), second);

        assertThat(merged).hasSize(1);
        ShardRead read = RelationshipShard.read("qa", RelationshipShard.write(merged));
        assertThat(read.relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.type()).isEqualTo(RelationType.SUGGESTS);
            assertThat(relationship.when()).contains("second");
            assertThat(relationship.rationale()).contains("because");
        });
    }

    @Test
    void upsertingTheReverseOrientationReplacesRatherThanAdds() {
        Relationship forward = new Relationship(A, B, RelationType.REQUIRES, Optional.empty(), Optional.empty());
        Relationship reverse = new Relationship(B, A, RelationType.SUGGESTS, Optional.of("rev"), Optional.empty());

        List<Relationship> merged = RelationshipShard.upsert(RelationshipShard.upsert(List.of(), forward), reverse);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).from()).isEqualTo(B);
        assertThat(merged.get(0).to()).isEqualTo(A);
        assertThat(merged.get(0).type()).isEqualTo(RelationType.SUGGESTS);
        assertThat(merged.get(0).when()).contains("rev");
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
