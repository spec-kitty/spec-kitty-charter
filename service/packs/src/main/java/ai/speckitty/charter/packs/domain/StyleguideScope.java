package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Optional;

/** The scope category of a styleguide. Each value carries the exact token the schema stores. */
public enum StyleguideScope {
    /** Source code conventions. */
    CODE("code"),
    /** Documentation conventions. */
    DOCS("docs"),
    /** Architecture conventions. */
    ARCHITECTURE("architecture"),
    /** Testing conventions. */
    TESTING("testing"),
    /** Operations conventions. */
    OPERATIONS("operations"),
    /** Glossary conventions. */
    GLOSSARY("glossary");

    private final String token;

    StyleguideScope(String token) {
        this.token = token;
    }

    /**
     * The spelling the schema stores.
     *
     * @return the token, for example {@code docs}
     */
    public String token() {
        return token;
    }

    /**
     * Looks a value up by its token.
     *
     * @param token a token, for example {@code testing}
     * @return the value; empty when no value has that token
     */
    public static Optional<StyleguideScope> fromToken(String token) {
        return Arrays.stream(values()).filter(s -> s.token.equals(token)).findFirst();
    }
}
