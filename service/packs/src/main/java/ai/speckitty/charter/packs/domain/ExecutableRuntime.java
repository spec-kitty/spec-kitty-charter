package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The closed allowlist of runtimes an executable asset may name (ADR-0054 decision 5, carrying core ADR
 * {@code 3.x/2026-08-13-2}'s hardening). Each is a vetted token the client maps to an executable itself; a pack can
 * never name a shell or an executable path. Widening the list is a reviewed change to this type and to
 * {@code asset.schema.json}, not a pack edit.
 */
public enum ExecutableRuntime {
    /** CPython, run as {@code python <entry> <args...>}. */
    PYTHON;

    /**
     * The token the sidecar writes.
     *
     * @return the lower-case constant name, for example {@code python}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a runtime up by its token.
     *
     * @param token a token, for example {@code python}
     * @return the runtime; empty when the token is not on the allowlist
     */
    public static Optional<ExecutableRuntime> fromToken(String token) {
        return Arrays.stream(values()).filter(r -> r.token().equals(token)).findFirst();
    }
}
