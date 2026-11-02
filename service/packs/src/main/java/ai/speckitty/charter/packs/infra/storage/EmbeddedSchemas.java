package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.DisallowUnknownKeywordFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The JSON Schemas a pack file embeds, which the bundled set cannot know in advance: a guard's {@code config_schema},
 * and the transition entries' {@code config} it checks (ADR-0054 decision 4, ADR-0042 S4 as amended).
 *
 * <p>An embedded schema is checked against the Draft 2020-12 metaschema, then compiled with the same strict dialect as
 * the bundled schemas (an unknown keyword is refused) in a registry of its own whose loader allows exactly the Draft
 * 2020-12 metaschema and vocabulary IRIs: a {@code $ref} outside the document is never fetched, and refuses the schema instead. Problems are short
 * facts ({@code <keyword> at <location>}); the validator's localized message text is never read.
 */
final class EmbeddedSchemas {

    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String METASCHEMA_BASE = "https://json-schema.org/draft/2020-12/";

    /** The Draft 2020-12 metaschema and its vocabularies: the only IRIs an embedded schema may reach. */
    private static final java.util.Set<String> METASCHEMA_IRIS = java.util.Set.of(
            DRAFT_2020_12,
            METASCHEMA_BASE + "meta/core",
            METASCHEMA_BASE + "meta/applicator",
            METASCHEMA_BASE + "meta/unevaluated",
            METASCHEMA_BASE + "meta/validation",
            METASCHEMA_BASE + "meta/meta-data",
            METASCHEMA_BASE + "meta/format-annotation",
            METASCHEMA_BASE + "meta/content");

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            Dialect.builder(Dialects.getDraft202012())
                    .unknownKeywordFactory(DisallowUnknownKeywordFactory.getInstance())
                    .build(),
            builder -> builder.schemaLoader(loader -> loader.allow(iri -> METASCHEMA_IRIS.contains(iri.toString()))));

    private static final Schema METASCHEMA = REGISTRY.getSchema(SchemaLocation.of(DRAFT_2020_12));

    private EmbeddedSchemas() {}

    /**
     * Why an embedded schema is not a usable Draft 2020-12 schema.
     *
     * @param schema the embedded schema, as authored
     * @return the problems; empty when it is one
     */
    static List<String> schemaProblems(ConfigValue schema) {
        JsonNode node = PackSchemas.toTree(schema.toParsed());
        JsonNode declared = node.get("$schema");
        if (declared != null && !(declared.isString() && declared.asString().equals(DRAFT_2020_12))) {
            return List.of("$schema is not " + DRAFT_2020_12);
        }
        List<String> problems = describe(METASCHEMA.validate(node));
        if (!problems.isEmpty()) {
            return problems;
        }
        try {
            Schema compiled = REGISTRY.getSchema(node);
            compiled.initializeValidators();
            // A schema that compiles may still fail when it is evaluated (a $dynamicRef, a $ref resolved lazily):
            // evaluating it once against an empty mapping surfaces that here, not on the first entry that uses it.
            compiled.validate(PackSchemas.toTree(java.util.Map.of()));
            return List.of();
        } catch (RuntimeException refused) {
            return List.of("it does not compile offline (an unknown keyword or a $ref outside the document): "
                    + refused.getClass().getSimpleName() + ": " + refused.getMessage());
        }
    }

    /**
     * Why a value does not fit an embedded schema.
     *
     * @param schema the embedded schema; it has no {@linkplain #schemaProblems problems}
     * @param value the value, as authored; an absent value is checked as an empty mapping
     * @return the problems; empty when the value fits
     */
    static List<String> valueProblems(ConfigValue schema, Optional<ConfigValue> value) {
        Optional<Schema> compiled = compile(PackSchemas.toTree(schema.toParsed()));
        if (compiled.isEmpty()) {
            return List.of("its schema does not compile offline");
        }
        Object instance = value.map(ConfigValue::toParsed).orElse(java.util.Map.of());
        try {
            return describe(compiled.get().validate(PackSchemas.toTree(instance)));
        } catch (RuntimeException unresolvable) {
            return List.of("its schema cannot be evaluated offline");
        }
    }

    private static Optional<Schema> compile(JsonNode node) {
        try {
            Schema schema = REGISTRY.getSchema(node);
            schema.initializeValidators();
            return Optional.of(schema);
        } catch (RuntimeException refused) {
            return Optional.empty();
        }
    }

    private static List<String> describe(List<Error> errors) {
        List<String> out = new ArrayList<>(errors.size());
        for (Error error : errors) {
            String at = error.getInstanceLocation().toString();
            out.add(error.getKeyword() + " at " + (at.isEmpty() ? "/" : at));
        }
        return out;
    }
}
