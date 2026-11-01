package ai.speckitty.charter.contracts.closure;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The JSON Schema and OpenAPI keywords the checker understands. Any other keyword is reported, so a construct the
 * checker has no semantics for (for example {@code unevaluatedProperties}, {@code propertyNames}, {@code contains})
 * can never slip past it.
 */
final class Vocabulary {

    /** Keywords that describe a schema and constrain nothing. Every {@code x-} extension is one too. */
    static final Set<String> ANNOTATIONS = Set.of(
            "title",
            "description",
            "default",
            "example",
            "examples",
            "deprecated",
            "readOnly",
            "writeOnly",
            "$comment",
            "format",
            "contentMediaType",
            "contentEncoding",
            "externalDocs",
            "xml",
            "discriminator");

    /** Keywords that narrow a value without admitting or closing anything. */
    static final Set<String> CONSTRAINTS = Set.of(
            "minLength",
            "maxLength",
            "pattern",
            "minimum",
            "maximum",
            "exclusiveMinimum",
            "exclusiveMaximum",
            "multipleOf",
            "minItems",
            "maxItems",
            "uniqueItems",
            "required",
            "dependentRequired",
            "minProperties",
            "maxProperties");

    /** Keywords the classification reads. */
    static final Set<String> STRUCTURE = Set.of(
            "type",
            "const",
            "enum",
            "properties",
            "additionalProperties",
            "patternProperties",
            "items",
            "prefixItems",
            "allOf",
            "anyOf",
            "oneOf",
            "not",
            "if",
            "then",
            "else",
            "dependentSchemas");

    /** The JSON Schema type names. */
    static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean", "null", "object", "array");

    private static final String REF = "$ref";

    private Vocabulary() {}

    static boolean understands(String keyword) {
        return keyword.startsWith("x-")
                || ANNOTATIONS.contains(keyword)
                || CONSTRAINTS.contains(keyword)
                || STRUCTURE.contains(keyword);
    }

    /**
     * Reports every keyword of {@code schema} the checker does not understand, a {@code $ref}, a malformed
     * {@code type}, a malformed {@code enum} and an {@code additionalProperties} that is not a schema.
     */
    static void vet(Node schema, Findings findings) {
        for (String keyword : schema.keys()) {
            if (REF.equals(keyword)) {
                findings.malformed(
                        schema.pointer().child(keyword),
                        "a $ref is left in the bundle: it names no component of it, or a component that holds itself");
            } else if (!understands(keyword)) {
                findings.malformed(
                        schema.pointer().child(keyword),
                        "the keyword '" + keyword + "' is not one the checker understands, so it cannot judge"
                                + " the schema; teach the checker or drop the keyword");
            }
        }
        schema.child("type")
                .flatMap(Vocabulary::typeProblem)
                .ifPresent(problem -> findings.malformed(schema.pointer().child("type"), problem));
        schema.child("enum")
                .filter(e -> !isEnum(e))
                .ifPresent(e -> findings.malformed(e.pointer(), "must be a non-empty list of values"));
        schema.child("additionalProperties")
                .filter(additional -> !additional.isSchema())
                .ifPresent(additional ->
                        findings.malformed(additional.pointer(), "must be a schema: a mapping or a boolean"));
    }

    /** Whether {@code node}, the value of an {@code enum}, is well-formed: a non-empty list. */
    static boolean isEnum(Node node) {
        return node.value() instanceof List<?> list && !list.isEmpty();
    }

    /**
     * The types {@code schema} declares, or empty when it declares none or declares them malformed (then it admits
     * all).
     */
    static Optional<Set<String>> declaredTypes(Node schema) {
        return schema.child("type").filter(type -> typeProblem(type).isEmpty()).map(type -> names(type.value()));
    }

    private static Optional<String> typeProblem(Node type) {
        if (!(type.value() instanceof String) && !(type.value() instanceof List<?> list && !list.isEmpty())) {
            return Optional.of("must be a type name or a non-empty list of type names");
        }
        List<?> names = type.value() instanceof List<?> list ? list : List.of(type.value());
        return names.stream()
                .map(Vocabulary::nameProblem)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> nameProblem(@Nullable Object name) {
        if (name == null) {
            return Optional.of(
                    "a type name must be a string: an unquoted YAML null is not the type name 'null';" + " quote it");
        }
        if (!(name instanceof String text)) {
            return Optional.of("a type name must be a string, not " + name);
        }
        return TYPES.contains(text) ? Optional.empty() : Optional.of("'" + text + "' is not a JSON Schema type");
    }

    /** The names of a well-formed {@code type}: one string, or a list of strings. */
    private static Set<String> names(@Nullable Object type) {
        Set<String> names = new LinkedHashSet<>();
        if (type instanceof List<?> list) {
            list.forEach(name -> names.add((String) name));
        } else if (type instanceof String name) {
            names.add(name);
        }
        return names;
    }
}
