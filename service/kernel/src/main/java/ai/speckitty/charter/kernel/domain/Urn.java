package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A DRG node identity, {@code <kind>:<id>}, or, as a reference to an artifact of another pack, the pack-qualified
 * {@code <pack-name>::<kind>:<id>} (ADR-0053 decision 7). URNs order by their text. A qualified URN is a different
 * identity from the bare one: it names the pack it relies on.
 *
 * @param kind the node's kind
 * @param id the node's id within its kind, for example {@code software-dev/plan}; never empty
 * @param pack the pack a qualified reference names; empty for a bare URN
 */
public record Urn(NodeKind kind, String id, Optional<String> pack) implements Comparable<Urn> {

    /**
     * Checks the URN.
     *
     * @param kind the node's kind
     * @param id the node's id
     * @param pack the pack a qualified reference names, in the pack-name grammar
     * @throws IllegalArgumentException when {@code id} is empty or the pack name breaks the grammar
     */
    public Urn {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(pack, "pack");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A URN needs a non-empty id");
        }
        if (pack.isPresent() && !PackQualifiedReference.isPackName(pack.get())) {
            throw new IllegalArgumentException("A URN qualifier follows the pack-name grammar: " + pack.get());
        }
    }

    /**
     * A bare URN.
     *
     * @param kind the node's kind
     * @param id the node's id
     */
    public Urn(NodeKind kind, String id) {
        this(kind, id, Optional.empty());
    }

    /**
     * Parses {@code kind:id}, or the pack-qualified {@code <pack-name>::kind:id}. The qualifier is split by
     * {@link PackQualifiedReference}, the one parser.
     *
     * @param token the text, for example {@code directive:DIRECTIVE_001} or {@code writing::directive:DIRECTIVE_001}
     * @return the URN; empty when the token is not a URN of a known node kind, or its qualifier is malformed
     */
    public static Optional<Urn> parse(String token) {
        if (token.contains("::")) {
            PackQualifiedReference reference;
            try {
                reference = PackQualifiedReference.parse(token);
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
            return parseBare(reference.urn()).map(bare -> new Urn(bare.kind(), bare.id(), reference.packName()));
        }
        return parseBare(token);
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
        return pack.map(name -> name + "::").orElse("") + kind.token() + ":" + id;
    }
}
