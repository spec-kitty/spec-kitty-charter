package ai.speckitty.charter.packs.infra.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.JsonScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import org.snakeyaml.engine.v2.schema.Schema;

/** Loading YAML text into plain values, with the settings every reader in this adapter shares. */
final class Yaml {

    private Yaml() {}

    /**
     * The JSON schema, plus the plain null forms {@code ~}, {@code Null} and {@code NULL} that core's parser reads as
     * null (R18). Booleans, integers and floats stay as the JSON schema has them: only null changes.
     */
    private static final class NullFormsSchema implements Schema {
        private final Schema json = new JsonSchema();

        @Override
        public ScalarResolver getScalarResolver() {
            JsonScalarResolver resolver = new JsonScalarResolver();
            resolver.addImplicitResolver(Tag.NULL, Pattern.compile("^(?:~|Null|NULL)$"), "~NN");
            return resolver;
        }

        @Override
        public Map<Tag, ConstructNode> getSchemaTagConstructors() {
            return json.getSchemaTagConstructors();
        }
    }

    /**
     * The dump-only schema: {@link NullFormsSchema} plus every implicit form a YAML 1.1 resolver gives a non-string
     * type (booleans such as {@code yes}/{@code no}/{@code on}/{@code off}/{@code y}/{@code n}, timestamps and dates
     * such as {@code 2026-07-29}, octal, binary, sexagesimal and underscored numbers, {@code <<} and {@code =}).
     * The emitter writes a string plain only when the schema resolves the plain form back to a string, so with these
     * resolvers a string that core's YAML 1.1 loader (ruamel) would read as another type is quoted. Nothing is ever
     * <em>loaded</em> with it, so the extra tags need no constructors.
     */
    private static final class Yaml11SafeDumpSchema implements Schema {
        private static final Tag TIMESTAMP = new Tag(Tag.PREFIX + "timestamp");
        private static final Tag MERGE = new Tag(Tag.PREFIX + "merge");
        private static final Tag VALUE = new Tag(Tag.PREFIX + "value");
        private final Schema loading = new NullFormsSchema();

        @Override
        public ScalarResolver getScalarResolver() {
            JsonScalarResolver resolver = (JsonScalarResolver) loading.getScalarResolver();
            resolver.addImplicitResolver(
                    Tag.BOOL,
                    Pattern.compile("^(?:y|Y|yes|Yes|YES|n|N|no|No|NO|true|True|TRUE|false|False|FALSE"
                            + "|on|On|ON|off|Off|OFF)$"),
                    "yYnNtTfFoO");
            resolver.addImplicitResolver(
                    Tag.INT,
                    Pattern.compile("^(?:[-+]?0b[0-1_]+|[-+]?0[0-7_]+|[-+]?(?:0|[1-9][0-9_]*)|[-+]?0x[0-9a-fA-F_]+"
                            + "|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+)$"),
                    "-+0123456789");
            resolver.addImplicitResolver(
                    Tag.FLOAT,
                    Pattern.compile(
                            "^(?:[-+]?(?:[0-9][0-9_]*)?\\.[0-9_]*(?:[eE][-+][0-9]+)?"
                                    + "|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN))$"),
                    "-+0123456789.");
            resolver.addImplicitResolver(
                    TIMESTAMP,
                    Pattern.compile(
                            "^(?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}(?:[Tt]|[ \\t]+)"
                                    + "[0-9]{1,2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]*)?(?:[ \\t]*(?:Z|[-+][0-9]{1,2}(?::[0-9]{2})?))?)$"),
                    "0123456789");
            resolver.addImplicitResolver(MERGE, Pattern.compile("^(?:<<)$"), "<");
            resolver.addImplicitResolver(VALUE, Pattern.compile("^(?:=)$"), "=");
            return resolver;
        }

        @Override
        public Map<Tag, ConstructNode> getSchemaTagConstructors() {
            return loading.getSchemaTagConstructors();
        }
    }

    /** The schema loading uses, so a string that would read back as null is read as null consistently. */
    static Schema schema() {
        return new NullFormsSchema();
    }

    /**
     * The schema dumping uses: a string is written plain only when neither this service's reader nor a YAML 1.1
     * reader would resolve the plain form as another type; otherwise it is quoted.
     */
    static Schema dumpSchema() {
        return new Yaml11SafeDumpSchema();
    }

    static LoadSettings settings() {
        return LoadSettings.builder()
                .setSchema(schema())
                .setAllowDuplicateKeys(false)
                .setCodePointLimit(Integer.MAX_VALUE)
                .setLabel("charter")
                .build();
    }

    /** Every document of a YAML stream, in order. */
    static List<Object> loadAll(String text) {
        List<Object> documents = new ArrayList<>();
        new Load(settings()).loadAllFromString(text).forEach(documents::add);
        return documents;
    }

    /** The single document of {@code text}; {@code null} for an empty stream or a document that is a YAML null. */
    static @Nullable Object load(String text) {
        return new Load(settings()).loadFromString(text);
    }
}
