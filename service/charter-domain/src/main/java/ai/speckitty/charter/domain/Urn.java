package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/** A DRG node identity, {@code <kind>:<id>}. */
public record Urn(NodeKind kind, String id) implements Comparable<Urn> {

    public Urn {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A URN needs a non-empty id");
        }
    }

    /** Parses {@code kind:id}; empty when the token is not a URN of a known node kind. */
    public static Optional<Urn> parse(String token) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) {
            return Optional.empty();
        }
        return NodeKind.fromToken(token.substring(0, colon)).map(k -> new Urn(k, token.substring(colon + 1)));
    }

    public static Urn action(String missionType, String action) {
        return new Urn(NodeKind.ACTION, missionType + "/" + action);
    }

    public static Urn missionType(String missionType) {
        return new Urn(NodeKind.MISSION_TYPE, missionType);
    }

    @Override
    public int compareTo(Urn other) { return toString().compareTo(other.toString()); }

    @Override
    public String toString() { return kind.token() + ":" + id; }
}
