package ai.speckitty.charter.routing.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a routing catalog asks its ranking to favour (ADR-0080 decision 10). The three objectives are three meanings: a
 * catalog author states which one they intend. Today {@link #BALANCED} and {@link #COST_FIRST} rank alike, by the
 * weighted sum of the catalog's weights, and the weights say how much cost counts. They are kept as distinct objectives
 * so a later rule may set them apart without renaming any catalog.
 */
public enum RoutingObjective {
    /** The model that fits the task best wins; the weighted sum only separates equal fits. */
    QUALITY_FIRST("quality_first"),
    /** Quality, cost, risk and latency weighed together, as the catalog's weights state. */
    BALANCED("balanced"),
    /** Cost leads; today ranked by the weighted sum, as {@link #BALANCED} is. */
    COST_FIRST("cost_first");

    private final String token;

    RoutingObjective(String token) {
        this.token = token;
    }

    /**
     * The objective's token, as a catalog writes it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The objective a token names.
     *
     * @param token the token a catalog writes
     * @return the objective, or empty when no objective has that token
     */
    public static Optional<RoutingObjective> ofToken(String token) {
        return Arrays.stream(values()).filter(o -> o.token.equals(token)).findFirst();
    }

    /**
     * Whether the fit score ranks first and the weighted sum only breaks ties.
     *
     * @return true for {@link #QUALITY_FIRST} only
     */
    public boolean ranksByFitFirst() {
        return this == QUALITY_FIRST;
    }
}
