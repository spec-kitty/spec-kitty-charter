package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A DRG node identity, {@code <kind>:<id>}. URNs order by their text. A URN is bare: it never names a pack, because a
 * pack is provenance, not identity (ADR-0048 decision 10). A reference that relies on another pack
 * ({@code <pack-name>::<kind>:<id>}, ADR-0053 decision 7) is a {@link PackQualifiedReference}, the one parser, and the
 * relationship or edge that holds it carries the qualifier beside its bare URN ({@link Relationship#toPack()}).
 *
 * @param kind the node's kind
 * @param id the node's id within its kind, for example {@code software-dev/plan}; never empty, never holding a
 *     {@code ::}
 */
public record Urn(NodeKind kind, String id) implements Comparable<Urn> {

    /**
     * Checks the URN.
     *
     * @param kind the node's kind
     * @param id the node's id
     * @throws IllegalArgumentException when {@code id} is empty or holds the {@code ::} pack qualifier
     */
    public Urn {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A URN needs a non-empty id");
        }
        if (id.contains("::")) {
            throw new IllegalArgumentException("A URN is bare: a pack qualifier belongs to the reference, not the id");
        }
    }

    /**
     * Parses {@code kind:id}. A pack-qualified reference is not a URN: split it with {@link PackQualifiedReference}
     * first and parse its URN part.
     *
     * @param token the text, for example {@code directive:DIRECTIVE_001}
     * @return the URN; empty when the token is not a URN of a known node kind, or holds a {@code ::} qualifier
     */
    public static Optional<Urn> parse(String token) {
        return token.contains("::") ? Optional.empty() : parseBare(token);
    }

    private static Optional<Urn> parseBare(String token) {
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
