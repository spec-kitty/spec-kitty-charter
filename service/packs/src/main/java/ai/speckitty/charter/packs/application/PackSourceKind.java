package ai.speckitty.charter.packs.application;

import java.util.Arrays;
import java.util.Optional;

/** The kinds of source {@code POST /packs} names (ADR-0065 D10); the service-stored kind is {@code PUT}. */
public enum PackSourceKind {
    /** An archive fetched over https from an allowlisted host. */
    HTTPS_ARCHIVE("https_archive"),
    /** A git repository and ref. */
    GIT("git"),
    /** A directory under the configured root. */
    FILESYSTEM("filesystem");

    private final String wire;

    PackSourceKind(String wire) {
        this.wire = wire;
    }

    /**
     * The kind on the wire and in the audit record.
     *
     * @return the lower-case, underscore-separated token
     */
    public String wire() {
        return wire;
    }

    /**
     * The kind of a wire token.
     *
     * @param token the token
     * @return the kind; empty for a token that names none
     */
    public static Optional<PackSourceKind> fromWire(String token) {
        return Arrays.stream(values()).filter(kind -> kind.wire.equals(token)).findFirst();
    }
}
