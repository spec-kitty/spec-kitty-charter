package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RelationshipMapperTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "b");

    @Test
    void toDomainOfToDsoIsTheRelationshipItself() {
        for (Relationship relationship : List.of(
                new Relationship(A, B, RelationType.REQUIRES, Optional.of("w"), Optional.of("r")), // directed, texts
                Relationship.of(A, B, RelationType.SUGGESTS), // directed, no texts
                new Relationship(A, B, RelationType.IMPACTS, Optional.of("w"), Optional.empty()))) { // bidirectional
            Relationship back = RelationshipMapper.toDomain(RelationshipMapper.toDso(relationship));
            assertThat(back).isEqualTo(relationship);
            assertThat(back.from()).isEqualTo(relationship.from());
            assertThat(back.to()).isEqualTo(relationship.to());
            assertThat(back.type()).isEqualTo(relationship.type());
            assertThat(back.when()).isEqualTo(relationship.when());
            assertThat(back.rationale()).isEqualTo(relationship.rationale());
        }
    }

    @Test
    void whenAndRationalePresentVsAbsentAreDistinctAcrossTheBoundary() {
        DrgEdge present = RelationshipMapper.toDso(
                new Relationship(A, B, RelationType.REQUIRES, Optional.of("x"), Optional.of("y")));
        DrgEdge absent = RelationshipMapper.toDso(Relationship.of(A, B, RelationType.REQUIRES));
        assertThat(present.when()).contains("x");
        assertThat(present.reason()).contains("y");
        assertThat(absent.when()).isEmpty();
        assertThat(absent.reason()).isEmpty();
        assertThat(present).isNotEqualTo(absent);
        assertThat(RelationshipMapper.toDomain(absent).when()).isEmpty();
        assertThat(RelationshipMapper.toDomain(absent).rationale()).isEmpty();
    }

    @Test
    void aNonUrnEndpointIsAMapperFaultNotASilentDrop() {
        DrgEdge badSource =
                new DrgEdge("QA_404", "directive:b", "requires", Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> RelationshipMapper.toDomain(badSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QA_404");
    }

    @Test
    void anUnknownRelationTokenIsAMapperFault() {
        DrgEdge badRelation = new DrgEdge(
                "tactic:a", "directive:b", "befriends", Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> RelationshipMapper.toDomain(badRelation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("befriends");
    }

    @Test
    void aSelfLoopIsAMapperFault() {
        DrgEdge selfLoop =
                new DrgEdge("tactic:a", "tactic:a", "requires", Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> RelationshipMapper.toDomain(selfLoop)).isInstanceOf(IllegalArgumentException.class);
    }
}
