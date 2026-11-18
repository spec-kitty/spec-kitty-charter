package ai.speckitty.charter.routing.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The tiers in play (data-model rule 3): the cap and the effective tier combine, neither replacing the other. */
@DisplayName("The tiers in play combine")
class TiersInPlayTest {

    @Test
    @DisplayName("with no tier in play every tier is inside, and the effective tier is unflagged")
    void noTier() {
        TiersInPlay none = new TiersInPlay(Optional.empty(), Optional.empty());

        for (CostTier tier : CostTier.values()) {
            assertThat(none.inside(tier)).isTrue();
            assertThat(none.withinTierConstraint(tier)).isTrue();
            assertThat(none.withinModelTier(tier)).isEmpty();
        }
    }

    @Test
    @DisplayName("a premium effective tier never lifts a medium cap, and a low one narrows it")
    void theTwoCombine() {
        TiersInPlay premium = new TiersInPlay(Optional.of(CostTier.MEDIUM), Optional.of(CostTier.PREMIUM));
        TiersInPlay low = new TiersInPlay(Optional.of(CostTier.MEDIUM), Optional.of(CostTier.LOW));

        assertThat(premium.inside(CostTier.MEDIUM)).isTrue();
        assertThat(premium.inside(CostTier.HIGH)).isFalse();
        assertThat(premium.withinModelTier(CostTier.HIGH)).contains(true);
        assertThat(premium.withinTierConstraint(CostTier.HIGH)).isFalse();
        assertThat(low.inside(CostTier.MEDIUM)).isFalse();
        assertThat(low.withinTierConstraint(CostTier.MEDIUM)).isTrue();
        assertThat(low.withinModelTier(CostTier.MEDIUM)).contains(false);
        assertThat(low.inside(CostTier.LOW)).isTrue();
    }
}
