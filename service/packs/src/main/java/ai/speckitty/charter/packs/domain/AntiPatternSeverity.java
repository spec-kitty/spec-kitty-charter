package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How much an {@link AntiPattern} weighs in a review (ADR-0052 decision 10). Every anti-pattern file writes one: there
 * is no default (ADR-0053 decision 32). Named so, not {@code Severity}: the kernel's validation severity is another
 * concept.
 */
public enum AntiPatternSeverity {
    /** Worth a remark. */
    SMELL,
    /** Should block review. */
    SEVERE,
    /** Must block. */
    BREAKING;

    /**
     * The token the anti-pattern file writes.
     *
     * @return {@code smell}, {@code severe} or {@code breaking}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a value up by its token.
     *
     * @param token {@code smell}, {@code severe} or {@code breaking}
     * @return the value; empty for any other token
     */
    public static Optional<AntiPatternSeverity> fromToken(String token) {
        return Arrays.stream(values()).filter(v -> v.token().equals(token)).findFirst();
    }
}
