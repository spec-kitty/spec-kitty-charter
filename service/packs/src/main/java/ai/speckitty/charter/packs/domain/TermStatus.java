package ai.speckitty.charter.packs.domain;

import java.util.Arrays;

/** The status of a {@link GlossaryTerm} (ADR-0053 decision 14): the three values the shipped terms use. */
public enum TermStatus {
    /** Proposed, not yet settled. */
    DRAFT("draft"),
    /** In use: the canonical term. */
    ACTIVE("active"),
    /** Kept for reference, no longer to be used. */
    DEPRECATED("deprecated");

    private final String token;

    TermStatus(String token) {
        this.token = token;
    }

    /**
     * The status as the {@code status} field spells it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The status a token names.
     *
     * @param token the {@code status} field's text
     * @return the status
     * @throws IllegalArgumentException when the token names no status
     */
    public static TermStatus fromToken(String token) {
        return Arrays.stream(values())
                .filter(status -> status.token.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown term status '" + token + "'"));
    }
}
