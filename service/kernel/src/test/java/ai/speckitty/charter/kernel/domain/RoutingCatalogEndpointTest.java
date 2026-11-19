package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0080 decision 5: nothing relates to a routing catalog, so no relation admits it at either end. */
@DisplayName("the routing catalog in the endpoint table")
class RoutingCatalogEndpointTest {

    @Test
    @DisplayName("the kind is activatable, but neither declarable in graphs nor an edge target")
    void theKindsFacts() {
        assertThat(ArtifactKind.ROUTING_CATALOG.activatable()).isTrue();
        assertThat(NodeKind.ROUTING_CATALOG.declarableInGraphs()).isFalse();
        assertThat(NodeKind.ROUTING_CATALOG.edgeTarget()).isFalse();
        assertThat(NodeKind.of(ArtifactKind.ROUTING_CATALOG)).isEqualTo(NodeKind.ROUTING_CATALOG);
    }

    @Test
    @DisplayName("no relation, enhances, overrides and tension included, names the catalog as a source or a target")
    void noRelationAdmitsTheCatalog() {
        for (RelationType type : RelationType.values()) {
            assertThat(EndpointTable.of(type))
                    .as(type.name())
                    .noneMatch(pair ->
                            pair.source() == NodeKind.ROUTING_CATALOG || pair.target() == NodeKind.ROUTING_CATALOG);
        }
    }

    @Test
    @DisplayName("the other activatable kinds keep their enhances and overrides diagonals")
    void theOtherDiagonalsStay() {
        assertThat(EndpointTable.of(RelationType.OVERRIDES))
                .contains(new EndpointPair(NodeKind.DIRECTIVE, NodeKind.DIRECTIVE))
                .contains(new EndpointPair(NodeKind.MISSION_TYPE, NodeKind.MISSION_TYPE));
        assertThat(EndpointTable.of(RelationType.ENHANCES))
                .contains(new EndpointPair(NodeKind.AGENT_PROFILE, NodeKind.AGENT_PROFILE));
    }
}
