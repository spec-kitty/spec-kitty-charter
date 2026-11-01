package ai.speckitty.charter.contracts.closure;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one map the checker accepts (rule 5 of {@link ClosureChecker}, ADR-0019 D3): "a property named {@code _links}
 * whose values reference the link object". The exemption is structural, because the checker reads the bundle with
 * every {@code $ref} inlined: the schema is reached as the property {@code _links}, its {@code type} is
 * {@code object} alone, it has no {@code patternProperties}, and its {@code additionalProperties} is a schema titled
 * {@code Link} that the checker itself classified as closed. Any other map, a lookalike included, stays open.
 */
final class LinksMap {

    private static final List<String> REACHED_AS = List.of("properties", "_links");
    private static final String VALUE_TITLE = "Link";

    private LinksMap() {}

    /**
     * The closure of {@code schema}'s own keywords when it is the exempt {@code _links} map.
     *
     * @param schema the schema whose own keywords are classified
     * @param values the classification of its schema-valued {@code additionalProperties}
     * @return a closed object when the exemption applies; empty otherwise
     */
    static Optional<Closure> exempt(Node schema, Closure values) {
        if (!reachedAsLinks(schema.pointer())
                || values.kind() != Closure.Kind.CLOSED
                || schema.has("patternProperties")
                || !Vocabulary.declaredTypes(schema).equals(Optional.of(Set.of("object")))
                || !valueTitledLink(schema)) {
            return Optional.empty();
        }
        return Optional.of(
                Closure.closedObject(schema.pointer(), "a _links map whose values are the closed Link (ADR-0019 D3)"));
    }

    private static boolean reachedAsLinks(JsonPointer pointer) {
        List<String> tokens = pointer.tokens();
        return tokens.size() >= REACHED_AS.size()
                && tokens.subList(tokens.size() - REACHED_AS.size(), tokens.size())
                        .equals(REACHED_AS);
    }

    private static boolean valueTitledLink(Node schema) {
        return schema.child("additionalProperties")
                .flatMap(values -> values.child("title"))
                .map(title -> VALUE_TITLE.equals(title.value()))
                .orElse(false);
    }
}
