package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.DisallowUnknownKeywordFactory;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.resource.SchemaLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * The bundled pack schemas, loaded strictly, offline and once.
 *
 * <p>One {@link SchemaRegistry} is built on the Draft 2020-12 dialect with {@link DisallowUnknownKeywordFactory}, so a
 * keyword the dialect does not know is refused when the schema loads instead of being silently ignored. The schema
 * {@code $id} prefix is mapped onto the classpath, and the loader allows that prefix and the bundled 2020-12
 * metaschema IRIs only, so no schema is ever fetched over the network. Every schema is checked against the
 * metaschema, compiled and cached when the set is built; a defect there, a document that is not JSON included, is a
 * build or configuration fault and throws {@link IllegalStateException} naming the file, never a pack finding.
 *
 * <p>Validation returns facts ({@link SchemaViolation}); the validator's localized message text is never read.
 */
final class PackSchemas {

    /** The {@code $id} base of every bundled schema (plan call P5). */
    static final String ID_PREFIX = "https://schemas.spec-kitty.ai/charter/packs/";

    private static final String BUNDLED_DIR = "packs/schemas/";
    private static final String SUFFIX = ".schema.json";
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String METASCHEMA_BASE = "https://json-schema.org/draft/2020-12/";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static @Nullable PackSchemas bundled;

    private final Map<String, Schema> schemas;
    private final Map<String, List<String>> declaredProperties;
    private final SchemaLoader loader;

    /**
     * Builds, checks and caches a set of schemas.
     *
     * @param resourceDir the classpath directory (with a trailing slash) that holds {@code <kind>.schema.json}
     * @param kinds the schema kinds to load
     * @throws IllegalStateException when a schema is missing, fails the metaschema, or does not compile
     */
    PackSchemas(String resourceDir, List<String> kinds) {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                strictDialect(),
                builder -> builder.schemaLoader(loaderBuilder -> loaderBuilder
                        .schemaIdResolvers(resolvers -> resolvers.mapPrefix(ID_PREFIX, "classpath:" + resourceDir))
                        .allow(iri -> {
                            String text = iri.toString();
                            return text.startsWith(ID_PREFIX) || text.startsWith(METASCHEMA_BASE);
                        })));
        this.loader = registry.getSchemaLoader();
        Schema metaschema = registry.getSchema(SchemaLocation.of(DRAFT_2020_12));
        Map<String, Schema> compiled = new LinkedHashMap<>();
        Map<String, List<String>> declared = new LinkedHashMap<>();
        for (String kind : kinds) {
            String file = kind + SUFFIX;
            JsonNode document = read(resourceDir + file, file);
            requireText(document, "$schema", DRAFT_2020_12, file);
            requireText(document, "$id", ID_PREFIX + file, file);
            List<Error> metaErrors = metaschema.validate(document);
            if (!metaErrors.isEmpty()) {
                throw new IllegalStateException(file + " does not satisfy the Draft 2020-12 metaschema: "
                        + metaErrors.stream().map(PackSchemas::describe).collect(Collectors.joining("; ")));
            }
            try {
                Schema schema = registry.getSchema(SchemaLocation.of(ID_PREFIX + file));
                schema.initializeValidators();
                compiled.put(kind, schema);
                declared.put(kind, propertyNames(document));
            } catch (SchemaException e) {
                throw new IllegalStateException(
                        file + " cannot be compiled: " + e.getClass().getSimpleName(), e);
            }
        }
        this.schemas = Map.copyOf(compiled);
        this.declaredProperties = Map.copyOf(declared);
    }

    /** The 16 schemas bundled with this adapter, built on first use and shared afterwards. */
    static synchronized PackSchemas bundled() {
        PackSchemas current = bundled;
        if (current == null) {
            current = new PackSchemas(
                    BUNDLED_DIR,
                    PackFileKind.bundled().fileRules().stream()
                            .map(PackFileKind.FileRule::schema)
                            .distinct()
                            .toList());
            bundled = current;
        }
        return current;
    }

    /** The kinds this set holds a schema for. */
    List<String> kinds() {
        return List.copyOf(schemas.keySet());
    }

    /**
     * The top-level properties a schema declares, in the order of its {@code properties}.
     *
     * <p>A document key that is not in this list is one the schema does not declare. A schema that allows additional
     * properties does not report such a key as a violation, so this is how a reader finds it.
     *
     * @param kind the schema kind
     * @return the declared property names, in schema order; empty when the schema declares none
     * @throws IllegalArgumentException when no schema of that kind is loaded
     */
    List<String> declaredProperties(String kind) {
        List<String> declared = declaredProperties.get(kind);
        if (declared == null) {
            throw new IllegalArgumentException("No schema of kind '" + kind + "' is loaded");
        }
        return declared;
    }

    /** The loader the registry uses, for the test that proves it cannot reach the network. */
    SchemaLoader schemaLoader() {
        return loader;
    }

    /**
     * Validates a loaded YAML tree against the schema of {@code kind}.
     *
     * @param kind the schema kind, as {@link PackFileKind.Mapped#kind()} names it
     * @param file the pack-relative path, copied onto every violation
     * @param yamlTree the loaded YAML value, or {@code null} for an empty document, which validates as JSON null
     * @return the violations, empty when the tree conforms
     * @throws IllegalArgumentException when no schema of that kind is loaded
     */
    List<SchemaViolation> validate(String kind, String file, @Nullable Object yamlTree) {
        Schema schema = schemas.get(kind);
        if (schema == null) {
            throw new IllegalArgumentException("No schema of kind '" + kind + "' is loaded");
        }
        List<NonFinite> nonFinite = new ArrayList<>();
        collectNonFinite(yamlTree, new ArrayList<>(), nonFinite);
        List<SchemaViolation> violations = new ArrayList<>();
        for (Error error : schema.validate(toTree(yamlTree))) {
            violations.add(violation(file, error));
        }
        if (nonFinite.isEmpty()) {
            return violations;
        }
        // Where does the schema refuse a number at all? Run again with each non-finite value as a plain 0.5.
        Map<List<String>, List<SchemaViolation>> refusingNumbers = new LinkedHashMap<>();
        for (Error error : schema.validate(tokens(MAPPER.valueToTree(yamlTree), true))) {
            SchemaViolation refusal = violation(file, error);
            refusingNumbers
                    .computeIfAbsent(refusal.pointer(), k -> new ArrayList<>())
                    .add(refusal);
        }
        List<SchemaViolation> judged = new ArrayList<>();
        for (NonFinite entry : nonFinite) {
            List<String> pointer = entry.pointer();
            boolean refused = violations.stream().anyMatch(v -> v.pointer().equals(pointer));
            if (isStrengthOfAnEdge(kind, pointer)) {
                violations.removeIf(v -> v.pointer().equals(pointer) && "type".equals(v.keyword()));
            } else if (!refused && refusingNumbers.containsKey(pointer)) {
                // The token string was accepted, but a number is refused here: the schema declares a string. The
                // violation carries the arguments of the probe run's own (the expected type), one per branch, so the
                // domain words it, and collapses a nullable, as it would a number the file really holds.
                String token = Double.toString(entry.value());
                List<SchemaViolation> types = refusingNumbers.get(pointer).stream()
                        .filter(v -> "type".equals(v.keyword()))
                        .toList();
                if (types.isEmpty()) {
                    judged.add(
                            new SchemaViolation(file, pointer, "type", Optional.empty(), Optional.of(token), Map.of()));
                }
                for (SchemaViolation refusal : types) {
                    judged.add(new SchemaViolation(
                            file, pointer, "type", Optional.empty(), Optional.of(token), refusal.args()));
                }
            }
        }
        violations.addAll(judged);
        return violations;
    }

    /**
     * Converts a loaded YAML value to a JSON tree, keeping integers, numbers and strings apart.
     *
     * <p>JSON has no NaN or infinity, and the validator cannot classify one. A YAML {@code .nan}, {@code .inf},
     * {@code -.inf} or a float too large for a double ({@code 1e400}) stands in as the string of its own token, as
     * {@link Double#toString(double)} writes it: {@code "NaN"}, {@code "Infinity"}, {@code "-Infinity"}. That is a
     * value of the one type no numeric rule accepts, so a non-finite value never satisfies {@code type: integer} or
     * {@code type: number}, and no {@code minimum}, {@code maximum} or {@code exclusiveMinimum} ever holds for it by
     * accident. A substituted number such as {@code 0.0} would pass an integer and most ranges, and would be reported
     * as a value the file does not hold. The violation therefore names the file's own token.
     *
     * <p>A string-typed field would accept the token, so {@link #validate} validates a second tree in which each
     * non-finite value is the number 0.5, and adds a {@code type} violation at a pointer only where that run also
     * reports one: the schema refuses numbers there. A free-form location ({@code additionalProperties: true}, an
     * untyped value) accepts a number, so it accepts the non-finite value too. The one place a number is all a schema asks is the {@code impacts} of an edge in a
     * graph-shard or an org-fragment: its range belongs to the domain ({@code 'impacts' NaN is outside [-1, 1]}),
     * which reads the real value from the YAML tree, so the validator's {@code type} violation there is dropped and
     * the domain's is the only finding. Every other schema, WP08's FULL scope included, refuses a non-finite number
     * where it declares a number or an integer.
     */
    static JsonNode toTree(@Nullable Object yamlTree) {
        if (yamlTree == null) {
            return NullNode.getInstance();
        }
        return tokens(MAPPER.valueToTree(yamlTree), false);
    }

    /** Replaces each non-finite number by its token string, or, for the probe run, by the number 0.5. */
    private static JsonNode tokens(JsonNode node, boolean asNumber) {
        if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
            return asNumber
                    ? DecimalNode.valueOf(new BigDecimal("0.5"))
                    : StringNode.valueOf(Double.toString(node.doubleValue()));
        }
        if (node instanceof ObjectNode object) {
            for (String name : List.copyOf(object.propertyNames())) {
                object.set(name, tokens(object.get(name), asNumber));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, tokens(array.get(i), asNumber));
            }
        }
        return node;
    }

    /** A non-finite number of the YAML tree: where it sits, as the validator spells the pointer, and its value. */
    private record NonFinite(List<String> pointer, double value) {}

    private static void collectNonFinite(@Nullable Object value, List<String> path, List<NonFinite> found) {
        if (value instanceof Double d && !Double.isFinite(d)) {
            found.add(new NonFinite(List.copyOf(path), d));
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                path.add(String.valueOf(entry.getKey()));
                collectNonFinite(entry.getValue(), path, found);
                path.remove(path.size() - 1);
            }
        } else if (value instanceof Collection<?> items) {
            int index = 0;
            for (Object item : items) {
                path.add(Integer.toString(index++));
                collectNonFinite(item, path, found);
                path.remove(path.size() - 1);
            }
        }
    }

    /** {@code edges/<i>/impacts} of a graph shard or an org fragment: the one number whose range the domain judges. */
    private static boolean isStrengthOfAnEdge(String kind, List<String> pointer) {
        return ("graph-shard".equals(kind) || "org-fragment".equals(kind))
                && pointer.size() == 3
                && "edges".equals(pointer.get(0))
                && "impacts".equals(pointer.get(2));
    }

    private static SchemaViolation violation(String file, Error error) {
        NodePath location = error.getInstanceLocation();
        List<String> pointer = new ArrayList<>();
        for (int i = 0; i < location.getNameCount(); i++) {
            pointer.add(String.valueOf(location.getElement(i)));
        }
        Map<String, String> args = new LinkedHashMap<>();
        // A keyword may report no arguments at all (uniqueItems does): then the violation has none.
        Object[] arguments = Objects.requireNonNullElse(error.getArguments(), new Object[0]);
        for (int i = 0; i < arguments.length; i++) {
            args.put(Integer.toString(i), String.valueOf(arguments[i]));
        }
        JsonNode instance = error.getInstanceNode();
        Optional<String> value =
                instance != null && (instance.isString() || instance.isNumber() || instance.isBoolean())
                        ? Optional.of(instance.asString())
                        : Optional.empty();
        return new SchemaViolation(
                file, pointer, error.getKeyword(), Optional.ofNullable(error.getProperty()), value, args);
    }

    private static List<String> propertyNames(JsonNode schema) {
        JsonNode properties = schema.get("properties");
        return properties == null ? List.of() : List.copyOf(properties.propertyNames());
    }

    private static String describe(Error error) {
        return error.getKeyword() + " at " + error.getInstanceLocation();
    }

    private static void requireText(JsonNode document, String field, String expected, String file) {
        JsonNode node = document.get(field);
        if (node == null || !node.isString()) {
            throw new IllegalStateException(file + " must declare " + field + " = " + expected);
        }
        if (!node.asString().equals(expected)) {
            throw new IllegalStateException(
                    file + " must declare " + field + " = " + expected + " but declares " + node.asString());
        }
    }

    private static JsonNode read(String resource, String file) {
        try (InputStream in = PackSchemas.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(file + " is missing from the classpath at " + resource);
            }
            return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JacksonException e) {
            throw new IllegalStateException(file + " is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private static Dialect strictDialect() {
        return Dialect.builder(Dialects.getDraft202012())
                .unknownKeywordFactory(DisallowUnknownKeywordFactory.getInstance())
                .build();
    }
}
