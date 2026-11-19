package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RelationTypeTest {

    @Test
    void theRetiredRelationsAreGone() {
        // Operator ruling DM-01M7B1YXJHQCBSYJ145NJC77V5 (slice F): impacts, replaces and delegates_to have no endpoint
        // row, so the catalogue does not name them and a graph document that spells one meets an unknown relation.
        assertThat(RelationType.fromToken("impacts")).isEmpty();
        assertThat(RelationType.fromToken("replaces")).isEmpty();
        assertThat(RelationType.fromToken("delegates_to")).isEmpty();
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
        assertThat(RelationType.fromToken("overrides")).contains(RelationType.OVERRIDES);
    }

    @Test
    void tensionIsABidirectionalAuthoredRelationSpelledTensionAndTheOldSpellingIsUnknown() {
        assertThat(RelationType.fromToken("tension")).contains(RelationType.TENSION);
        assertThat(RelationType.TENSION.directionality()).isEqualTo(Directionality.BIDIRECTIONAL);
        assertThat(RelationType.TENSION.derived()).isFalse();
        assertThat(RelationType.TENSION.allows(NodeKind.DIRECTIVE, NodeKind.TACTIC))
                .isTrue();
        assertThat(RelationType.TENSION.allows(NodeKind.DIRECTIVE, NodeKind.AGENT_PROFILE))
                .isFalse();
        assertThat(RelationType.fromToken("in_tension_with")).isEmpty();
    }
}
