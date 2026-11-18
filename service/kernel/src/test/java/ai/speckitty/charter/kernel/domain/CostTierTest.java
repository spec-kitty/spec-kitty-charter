package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CostTier")
class CostTierTest {

    @Test
    @DisplayName("the tiers run from cheapest to dearest, with their tokens")
    void theOrderAndTheTokens() {
        assertThat(CostTier.values()).extracting(CostTier::token).containsExactly("low", "medium", "high", "premium");
    }

    @Test
    @DisplayName("atMost holds for the same tier and every dearer one, and for no cheaper one")
    void atMostAtEachBoundary() {
        assertThat(CostTier.MEDIUM.atMost(CostTier.MEDIUM)).isTrue();
        assertThat(CostTier.MEDIUM.atMost(CostTier.HIGH)).isTrue();
        assertThat(CostTier.MEDIUM.atMost(CostTier.PREMIUM)).isTrue();
        assertThat(CostTier.MEDIUM.atMost(CostTier.LOW)).isFalse();
        assertThat(CostTier.PREMIUM.atMost(CostTier.HIGH)).isFalse();
        assertThat(CostTier.LOW.atMost(CostTier.LOW)).isTrue();
    }

    @Test
    @DisplayName("a token finds its tier back, and an unknown or differently cased token finds none")
    void tokens() {
        for (CostTier tier : CostTier.values()) {
            assertThat(CostTier.ofToken(tier.token())).contains(tier);
        }
        assertThat(CostTier.ofToken("Low")).isEmpty();
        assertThat(CostTier.ofToken("cheap")).isEmpty();
        assertThat(CostTier.ofToken("")).isEmpty();
    }
}
