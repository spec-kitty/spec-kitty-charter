package ai.speckitty.charter.routing.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A three-step grade a catalog writes for a task type's sensitivities, a fit's confidence and a model's latency tier
 * (ADR-0080 decision 5): low, medium or high.
 */
public enum Grade {
    /** Low. */
    LOW("low"),
    /** Medium. */
    MEDIUM("medium"),
    /** High. */
    HIGH("high");

    private final String token;

    Grade(String token) {
        this.token = token;
    }

    /**
     * The grade's token, as a catalog writes it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The grade a token names.
     *
     * @param token the token a catalog writes
     * @return the grade, or empty when no grade has that token
     */
    public static Optional<Grade> ofToken(String token) {
        return Arrays.stream(values()).filter(g -> g.token.equals(token)).findFirst();
    }
}
