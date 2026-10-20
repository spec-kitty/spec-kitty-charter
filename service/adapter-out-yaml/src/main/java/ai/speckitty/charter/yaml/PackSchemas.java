package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackContents.SchemaViolation;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/**
 * The bundled pack schemas, loaded strictly, offline and once.
 *
 * <p>One {@link SchemaRegistry} is built on the Draft 2020-12 dialect with {@link DisallowUnknownKeywordFactory}, so a
 * keyword the dialect does not know is refused when the schema loads instead of being silently ignored. The schema
 * {@code $id} prefix is mapped onto the classpath, and the loader allows that prefix and the bundled 2020-12
 * metaschema IRIs only, so no schema is ever fetched over the network. Every schema is checked against the
 * metaschema, compiled and cached when the set is built; a defect there is a build or configuration fault and throws
 * {@link IllegalStateException} naming the file, never a pack finding.
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
            } catch (SchemaException e) {
                throw new IllegalStateException(
                        file + " cannot be compiled: " + e.getClass().getSimpleName(), e);
            }
        }
        this.schemas = Map.copyOf(compiled);
    }

    /** The 14 schemas bundled with this adapter, built on first use and shared afterwards. */
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
        List<SchemaViolation> violations = new ArrayList<>();
        for (Error error : schema.validate(toTree(yamlTree))) {
            violations.add(violation(file, error));
        }
        return violations;
    }

    /** Converts a loaded YAML value to a JSON tree, keeping integers, numbers and strings apart. */
    static JsonNode toTree(@Nullable Object yamlTree) {
        if (yamlTree == null) {
            return NullNode.getInstance();
        }
        return MAPPER.valueToTree(yamlTree);
    }

    private static SchemaViolation violation(String file, Error error) {
        NodePath location = error.getInstanceLocation();
        List<String> pointer = new ArrayList<>();
        for (int i = 0; i < location.getNameCount(); i++) {
            pointer.add(String.valueOf(location.getElement(i)));
        }
        Map<String, String> args = new LinkedHashMap<>();
        Object[] arguments = error.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            args.put(Integer.toString(i), String.valueOf(arguments[i]));
        }
        JsonNode instance = error.getInstanceNode();
        Optional<String> value = instance != null && instance.isValueNode() && !instance.isNull()
                ? Optional.of(instance.asString())
                : Optional.empty();
        return new SchemaViolation(
                file, pointer, error.getKeyword(), Optional.ofNullable(error.getProperty()), value, args);
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
        }
    }

    private static Dialect strictDialect() {
        return Dialect.builder(Dialects.getDraft202012())
                .unknownKeywordFactory(DisallowUnknownKeywordFactory.getInstance())
                .build();
    }
}
