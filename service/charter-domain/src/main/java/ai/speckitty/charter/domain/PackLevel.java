package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/** The level a Charter Pack declares; layers merge in declaration order of this enum. */
public enum PackLevel {
    /** Ships with Spec Kitty; merged first. */
    BUILT_IN("built-in"),
    /** An organization's pack, registered under {@code charter_packs.org.packs}; merged second. */
    ORG("org"),
    /** The project's own layer; merged last. */
    PROJECT("project");

    private final String token;

    PackLevel(String token) {
        this.token = token;
    }

    /**
     * The spelling {@code pack.yaml} and the contract use.
     *
     * @return the token, for example {@code built-in}
     */
    public String token() {
        return token;
    }

    /**
     * Looks a level up by its token.
     *
     * @param token a token, for example {@code org}
     * @return the level; empty when no level has that token
     */
    public static Optional<PackLevel> fromToken(String token) {
        return Arrays.stream(values()).filter(l -> l.token.equals(token)).findFirst();
    }
}
