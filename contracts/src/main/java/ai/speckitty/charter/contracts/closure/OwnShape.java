package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies a schema by its own keywords alone ({@code type}, {@code const}/{@code enum} and the object and array
 * keywords), ignoring its composition. Nested positions are judged elsewhere ({@link SchemaClassifier}).
 */
final class OwnShape {

    private static final List<String> CLOSING_KEYWORDS =
            List.of("additionalProperties", "patternProperties", "items", "prefixItems");

    private OwnShape() {}

    static Closure classify(Node schema) {
        if (schema.value() instanceof Boolean admitsAll) {
            return admitsAll
                    ? Closure.open(schema.pointer(), "the true schema admits any value", false)
                    : Closure.notAnObject(schema.pointer(), "the false schema admits nothing");
        }
        Closure type = byType(schema);
        return finiteValues(schema)
                .map(values -> Conjunction.of(schema.pointer(), List.of(values, type)))
                .orElse(type);
    }

    /**
     * A {@code const} or {@code enum} admits only its listed values: closed objects and fixed arrays at most. A
     * malformed {@code enum} (reported by {@link Vocabulary#vet}) lists nothing the checker can rely on, so it is
     * ignored here and the schema is judged by its other keywords.
     */
    private static Optional<Closure> finiteValues(Node schema) {
        Optional<Node> listed = schema.child("enum").filter(Vocabulary::isEnum);
        if (!schema.has("const") && listed.isEmpty()) {
            return Optional.empty();
        }
        List<Object> values = new ArrayList<>();
        schema.child("const").ifPresent(c -> values.add(c.value()));
        listed.ifPresent(e -> values.addAll((List<?>) e.value()));
        String only = "a const or enum admits only the values it lists";
        return Optional.of(new Closure(
                schema.pointer(),
                values.stream().anyMatch(Map.class::isInstance) ? Closure.Facet.closed(only) : Closure.Facet.none(only),
                values.stream().anyMatch(List.class::isInstance)
                        ? Closure.Facet.closed(only)
                        : Closure.Facet.none(only)));
    }

    private static Closure byType(Node schema) {
        Optional<Set<String>> declared = Vocabulary.declaredTypes(schema);
        if (declared.isEmpty() && CLOSING_KEYWORDS.stream().noneMatch(schema::has)) {
            return Closure.open(schema.pointer(), "it has no type and no closing keyword", false);
        }
        Set<String> types = declared.orElse(Vocabulary.TYPES);
        String untyped = declared.isEmpty() ? "it has no type, so it admits any object and any array, and " : "";
        Closure.Facet objects = types.contains("object")
                ? objectFacet(schema, untyped, declared.isPresent())
                : Closure.Facet.none("its type " + types + " admits no object");
        Closure.Facet arrays = types.contains("array")
                ? arrayFacet(schema, untyped)
                : Closure.Facet.none("its type " + types + " admits no array");
        return new Closure(schema.pointer(), objects, arrays);
    }

    private static Closure.Facet objectFacet(Node schema, String untyped, boolean declared) {
        if (schema.has("patternProperties")) {
            return Closure.Facet.open(untyped + "patternProperties admits members by pattern", declared);
        }
        Object additional =
                schema.child("additionalProperties").map(Node::value).orElse(null);
        if (Boolean.FALSE.equals(additional)) {
            return Closure.Facet.closed("additionalProperties: false without patternProperties");
        }
        if (additional == null) {
            return Closure.Facet.open(untyped + "no additionalProperties: false", declared);
        }
        String why;
        if (Boolean.TRUE.equals(additional)) {
            why = "additionalProperties: true admits any member";
        } else if (additional instanceof Map<?, ?> && isLinksMap(schema)) {
            return Closure.Facet.closed(
                    "the _links map of ADR-0019 D3: open relation keys, each value a Link judged as a position");
        } else if (additional instanceof Map<?, ?>) {
            why = "additionalProperties holds a schema, so it is a map";
        } else {
            why = "additionalProperties is malformed, so nothing closes it";
        }
        return Closure.Facet.open(untyped + why, declared);
    }

    /**
     * The one map a response may hold (ADR-0019 D3, {@code docs/development/hypermedia-hateoas.md}, shared with #126):
     * the schema of a property named {@code _links}, directly or as an {@code allOf} part of it, whose
     * {@code additionalProperties} is a schema titled {@code Link}. The relation keys are open, so a new relation is
     * additive; the {@code Link} is a position of its own, so one that is not closed is still reported. The test is
     * structural, because the checker reads the inlined bundle, where no {@code $ref} is left. A map with any other
     * value, or under any other property, is open.
     */
    private static boolean isLinksMap(Node schema) {
        boolean linkValues = schema.child("additionalProperties")
                .flatMap(value -> value.child("title"))
                .map(Node::value)
                .filter("Link"::equals)
                .isPresent();
        if (!linkValues) {
            return false;
        }
        List<String> tokens = schema.pointer().tokens();
        int at = tokens.lastIndexOf("_links");
        if (at < 1 || !"properties".equals(tokens.get(at - 1))) {
            return false;
        }
        List<String> rest = tokens.subList(at + 1, tokens.size());
        for (int i = 0; i < rest.size(); i += 2) {
            if (!"allOf".equals(rest.get(i)) || i + 1 >= rest.size() || !isIndex(rest.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIndex(String token) {
        return !token.isEmpty() && token.chars().allMatch(Character::isDigit);
    }

    private static Closure.Facet arrayFacet(Node schema, String untyped) {
        if (schema.has("items")) {
            return Closure.Facet.closed("its items are a position of their own");
        }
        return Closure.Facet.open(
                untyped
                        + (schema.has("prefixItems")
                                ? "a tuple with no items: false admits any item after its prefixItems"
                                : "an array with no items admits any item"),
                false);
    }
}
