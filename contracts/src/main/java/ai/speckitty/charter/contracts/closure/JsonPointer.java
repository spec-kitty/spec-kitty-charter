package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A JSON Pointer (RFC 6901) to a place in the bundled contract.
 *
 * @param tokens the reference tokens, unescaped
 */
public record JsonPointer(List<String> tokens) {

    /** The pointer to the whole document. */
    public static final JsonPointer ROOT = new JsonPointer(List.of());

    /** Copies the tokens, so a pointer never changes. */
    public JsonPointer {
        tokens = List.copyOf(tokens);
    }

    /** The pointer to member {@code token} of the value this pointer names. */
    public JsonPointer child(String token) {
        List<String> longer = new ArrayList<>(tokens);
        longer.add(token);
        return new JsonPointer(longer);
    }

    /** The pointer to element {@code index} of the list this pointer names. */
    public JsonPointer child(int index) {
        return child(Integer.toString(index));
    }

    /** Whether this pointer names the whole document. */
    public boolean isRoot() {
        return tokens.isEmpty();
    }

    /**
     * This pointer as seen from {@code ancestor}: the escaped tokens below it joined by {@code /} ({@code allOf/0}),
     * empty for the ancestor itself, and the whole pointer when {@code ancestor} is not an ancestor.
     */
    public String relativeTo(JsonPointer ancestor) {
        if (tokens.size() < ancestor.tokens.size()
                || !tokens.subList(0, ancestor.tokens.size()).equals(ancestor.tokens)) {
            return toString();
        }
        return tokens.subList(ancestor.tokens.size(), tokens.size()).stream()
                .map(JsonPointer::escape)
                .collect(Collectors.joining("/"));
    }

    @Override
    public String toString() {
        return tokens.stream().map(t -> "/" + escape(t)).collect(Collectors.joining());
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
