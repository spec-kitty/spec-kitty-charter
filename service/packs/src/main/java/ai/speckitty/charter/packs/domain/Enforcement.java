package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Optional;

/** How strictly a directive binds. Each value carries the exact token the schema stores. */
public enum Enforcement {
    /** The directive must be followed. */
    REQUIRED("required"),
    /** The directive is followed unless an explicit allowance applies. */
    LENIENT_ADHERENCE("lenient-adherence"),
    /** The directive is guidance only. */
    ADVISORY("advisory");

    private final String token;

    Enforcement(String token) {
        this.token = token;
    }

    /**
     * The spelling the schema stores; stored rather than derived because it is hyphenated.
     *
     * @return the token, for example {@code lenient-adherence}
     */
    public String token() {
        return token;
    }

    /**
     * Looks a value up by its token.
     *
     * @param token a token, for example {@code advisory}
     * @return the value; empty when no value has that token
     */
    public static Optional<Enforcement> fromToken(String token) {
        return Arrays.stream(values()).filter(e -> e.token.equals(token)).findFirst();
    }
}
