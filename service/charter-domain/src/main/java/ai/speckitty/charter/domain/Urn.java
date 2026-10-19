package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A DRG node identity, {@code <kind>:<id>}. URNs order by their text.
 *
 * @param kind the node's kind
 * @param id the node's id within its kind, for example {@code software-dev/plan}; never empty
 */
public record Urn(NodeKind kind, String id) implements Comparable<Urn> {

    /**
     * Checks the URN.
     *
     * @param kind the node's kind
     * @param id the node's id
     * @throws IllegalArgumentException when {@code id} is empty
     */
    public Urn {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A URN needs a non-empty id");
        }
    }

    /**
     * Parses {@code kind:id}.
     *
     * @param token the text, for example {@code directive:DIRECTIVE_001}
     * @return the URN; empty when the token is not a URN of a known node kind
     */
    public static Optional<Urn> parse(String token) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) {
            return Optional.empty();
        }
        return NodeKind.fromToken(token.substring(0, colon)).map(k -> new Urn(k, token.substring(colon + 1)));
    }

    /**
     * The URN of an action, {@code action:<missionType>/<action>}.
     *
     * @param missionType the mission type, for example {@code software-dev}
     * @param action the action, for example {@code plan}
     * @return the action's URN
     */
    public static Urn action(String missionType, String action) {
        return new Urn(NodeKind.ACTION, missionType + "/" + action);
    }

    /**
     * The URN of a mission type, {@code mission_type:<missionType>}.
     *
     * @param missionType the mission type, for example {@code software-dev}
     * @return the mission type's URN
     */
    public static Urn missionType(String missionType) {
        return new Urn(NodeKind.MISSION_TYPE, missionType);
    }

    @Override
    public int compareTo(Urn other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return kind.token() + ":" + id;
    }
}
