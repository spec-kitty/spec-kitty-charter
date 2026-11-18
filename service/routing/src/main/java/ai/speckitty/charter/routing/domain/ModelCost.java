package ai.speckitty.charter.routing.domain;

import ai.speckitty.charter.kernel.domain.CostTier;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What a model costs (ADR-0080 decision 5): its tier, which the ranking reads, and the prices, which it does not.
 *
 * @param tier the cost tier
 * @param inputPer1mUsd the price of a million input tokens in US dollars, when written
 * @param outputPer1mUsd the price of a million output tokens in US dollars, when written
 * @param currency the currency of the prices, when written
 * @param pricingSourceUrl where the prices come from, when written
 */
public record ModelCost(
        CostTier tier,
        Optional<BigDecimal> inputPer1mUsd,
        Optional<BigDecimal> outputPer1mUsd,
        Optional<String> currency,
        Optional<String> pricingSourceUrl) {

    /**
     * Checks that the prices are not negative and the texts not blank.
     *
     * @param tier the tier
     * @param inputPer1mUsd the input price, possibly empty
     * @param outputPer1mUsd the output price, possibly empty
     * @param currency the currency, possibly empty
     * @param pricingSourceUrl the pricing source, possibly empty
     */
    public ModelCost {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(inputPer1mUsd, "inputPer1mUsd").ifPresent(Checks::notNegative);
        Objects.requireNonNull(outputPer1mUsd, "outputPer1mUsd").ifPresent(Checks::notNegative);
        Objects.requireNonNull(currency, "currency").ifPresent(Checks::text);
        Objects.requireNonNull(pricingSourceUrl, "pricingSourceUrl").ifPresent(Checks::text);
    }
}
