package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/** The level a Charter Pack declares; layers merge in declaration order of this enum. */
public enum PackLevel {
    BUILT_IN("built-in"),
    ORG("org"),
    PROJECT("project");

    private final String token;

    PackLevel(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static Optional<PackLevel> fromToken(String token) {
        return Arrays.stream(values()).filter(l -> l.token.equals(token)).findFirst();
    }
}
