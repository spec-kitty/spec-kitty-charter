package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a guard's could-not-evaluate result counts as (ADR-0054 decisions 7 and 8). */
public enum OnIndeterminate {
    /** It blocks under the entry's {@code blocking} rule; the default. */
    BLOCK,
    /** It counts as a pass, as the guard's author declared. */
    PASS;

    /**
     * The token the guard file writes.
     *
     * @return {@code block} or {@code pass}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a value up by its token.
     *
     * @param token {@code block} or {@code pass}
     * @return the value; empty for any other token
     */
    public static Optional<OnIndeterminate> fromToken(String token) {
        return Arrays.stream(values()).filter(v -> v.token().equals(token)).findFirst();
    }
}
