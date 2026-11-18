package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The cost tier of a model, from cheapest to dearest (ADR-0080 decisions 5 and 12). It is the one tier vocabulary of
 * model routing: a catalog model's {@code cost.tier}, a task type's {@code max_tier}, a mission step's
 * {@code model_tier} and the {@code modelTier} a client asks for all use it. It lives in the kernel because both the
 * missions slice (the step) and the routing slice (the catalog and the ranker) read it, and neither may name the other.
 */
public enum CostTier {
    /** The cheapest tier. */
    LOW("low"),
    /** A middle tier. */
    MEDIUM("medium"),
    /** A dear tier. */
    HIGH("high"),
    /** The dearest tier. */
    PREMIUM("premium");

    private final String token;

    CostTier(String token) {
        this.token = token;
    }

    /**
     * The tier's token, as a pack and the contract write it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The tier a token names.
     *
     * @param token the token a pack or a request writes
     * @return the tier, or empty when no tier has that token
     */
    public static Optional<CostTier> ofToken(String token) {
        return Arrays.stream(values()).filter(t -> t.token.equals(token)).findFirst();
    }

    /**
     * Whether this tier is no dearer than another, so a model of this tier is inside a cap of the other.
     *
     * @param other the cap
     * @return true when this tier is the same as or cheaper than {@code other}
     */
    public boolean atMost(CostTier other) {
        return compareTo(other) <= 0;
    }
}
