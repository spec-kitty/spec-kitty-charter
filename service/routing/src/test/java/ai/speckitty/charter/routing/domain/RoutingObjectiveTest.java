package ai.speckitty.charter.routing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RoutingObjective")
class RoutingObjectiveTest {

    @Test
    @DisplayName("each objective has its catalog token, and the token finds it back")
    void tokensRoundTrip() {
        assertThat(RoutingObjective.values())
                .extracting(RoutingObjective::token)
                .containsExactly("quality_first", "balanced", "cost_first");
        for (RoutingObjective objective : RoutingObjective.values()) {
            assertThat(RoutingObjective.ofToken(objective.token())).contains(objective);
        }
    }

    @Test
    @DisplayName("an unknown token, or a near spelling, names no objective")
    void unknownTokensAreEmpty() {
        assertThat(RoutingObjective.ofToken("weighted")).isEmpty();
        assertThat(RoutingObjective.ofToken("QUALITY_FIRST")).isEmpty();
        assertThat(RoutingObjective.ofToken("")).isEmpty();
    }

    @Test
    @DisplayName("only quality_first ranks by fit first; balanced and cost_first rank by the weighted sum")
    void onlyQualityFirstRanksByFitFirst() {
        assertThat(RoutingObjective.QUALITY_FIRST.ranksByFitFirst()).isTrue();
        assertThat(RoutingObjective.BALANCED.ranksByFitFirst()).isFalse();
        assertThat(RoutingObjective.COST_FIRST.ranksByFitFirst()).isFalse();
    }
}
