package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RelationTypeTest {

    @Test
    void everyRelationTokenHasAMatchingRelationType() {
        // Pins the two catalogues together: RelationType supersedes Relation, so every Relation
        // token must resolve to a RelationType. Iterating Relation.values() keeps this non-vacuous
        // — a missing or renamed token fails here.
        for (Relation relation : Relation.values()) {
            assertThat(RelationType.fromToken(relation.token()))
                    .as("RelationType for token '%s'", relation.token())
                    .isPresent();
        }
    }

    @Test
    void everyRelationTypeTokenHasAMatchingRelation() {
        // The reverse direction: no RelationType may carry a token the Relation catalogue lacks, but a derived one
        // (implemented_by, ADR-0054 decision 3), which no graph document spells and so Relation does not list.
        for (RelationType type : RelationType.values()) {
            if (type.derived()) {
                assertThat(Relation.fromToken(type.token()))
                        .as("derived '%s'", type.token())
                        .isEmpty();
                continue;
            }
            assertThat(Relation.fromToken(type.token()))
                    .as("Relation for token '%s'", type.token())
                    .isPresent();
        }
    }

    @Test
    void implementedByIsTheOneDerivedRelationType() {
        assertThat(java.util.Arrays.stream(RelationType.values()).filter(RelationType::derived))
                .containsExactly(RelationType.IMPLEMENTED_BY);
        assertThat(RelationType.IMPLEMENTED_BY.directionality()).isEqualTo(Directionality.DIRECTED);
    }

    @Test
    void impactsIsBidirectional() {
        assertThat(RelationType.IMPACTS.directionality()).isEqualTo(Directionality.BIDIRECTIONAL);
    }

    @Test
    void aDirectedExampleIsDirected() {
        assertThat(RelationType.REQUIRES.directionality()).isEqualTo(Directionality.DIRECTED);
        assertThat(RelationType.SUGGESTS.directionality()).isEqualTo(Directionality.DIRECTED);
    }

    @Test
    void tokenIsTheLowerCaseConstantName() {
        assertThat(RelationType.SPECIALIZES_FROM.token()).isEqualTo("specializes_from");
        assertThat(RelationType.REQUIRES.token()).isEqualTo("requires");
    }

    @Test
    void fromTokenReturnsEmptyForAnUnknownToken() {
        assertThat(RelationType.fromToken("not_a_relation")).isEmpty();
        assertThat(RelationType.fromToken("")).isEmpty();
    }

    @Test
    void fromTokenResolvesAKnownToken() {
        assertThat(RelationType.fromToken("impacts")).contains(RelationType.IMPACTS);
    }
}
