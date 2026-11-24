package ai.speckitty.charter.packs.infra.storage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.json.ConstructYamlJsonFloat;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.JsonScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import org.snakeyaml.engine.v2.schema.Schema;

/** Loading YAML text into plain values, with the settings every reader in this adapter shares. */
final class Yaml {

    private Yaml() {}

    /**
     * The most code points a YAML text may hold: 1 Mi (1 048 576). The scanner's window grows once per chunk it reads,
     * so one long scalar costs the square of its length (8 Mi code points in one scalar take over a minute to scan),
     * and the bound keeps a read to seconds. Real pack files are far smaller: the largest open-packs file, a glossary,
     * holds about 125 000 characters, so the bound leaves eight times that. {@link ParsedYaml} refuses a longer text
     * as {@code the document is too large} before the scanner sees it, as it refuses a document past its node budget;
     * the loader's own limit is the same bound, for the readers that load without it. {@link ParsedYaml} also bounds the
     * strings a loaded document holds at the same count, a shared (aliased) string counted at each reach, so no
     * accepted document expands past it (Tier R B3).
     */
    static final int MAX_CODE_POINTS = 1024 * 1024;

    /**
     * The JSON schema, plus the plain null forms {@code ~}, {@code Null} and {@code NULL} that core's parser reads as
     * null (R18), and a finite float read as the exact {@link BigDecimal} its text writes (ADR-0080 decision 10, R3):
     * no binary double step, so {@code 0.1} is 0.1 and a value of any precision keeps every digit. A non-finite float
     * ({@code .inf}, {@code .nan}) has no decimal and stays a {@code Double}. Booleans and integers stay as the JSON
     * schema has them.
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
            Map<Tag, ConstructNode> constructors = new HashMap<>(json.getSchemaTagConstructors());
            constructors.put(Tag.FLOAT, new ExactFloat());
            return constructors;
        }
    }

    /**
     * A float scalar as the exact decimal its text writes. One that is non-finite as a double ({@code .inf},
     * {@code .nan}, or {@code 1e400}, which core's reader reads as infinity) stays the JSON schema's {@code Double}.
     */
    private static final class ExactFloat extends ConstructYamlJsonFloat {
        @Override
        protected Object constructFromString(String value) {
            Object read = super.constructFromString(value);
            return read instanceof Double d && Double.isFinite(d) ? new BigDecimal(value) : read;
        }
    }

    /**
     * The dump-only schema: {@link NullFormsSchema} plus every implicit form a YAML 1.1 resolver gives a non-string
     * type (booleans such as {@code yes}/{@code no}/{@code on}/{@code off}/{@code y}/{@code n}, timestamps and dates
     * such as {@code 2026-07-29}, octal, binary, sexagesimal and underscored numbers, {@code <<} and {@code =}).
     * The emitter writes a string plain only when the schema resolves the plain form back to a string, so with these
     * resolvers a string that core's YAML 1.1 loader (ruamel) would read as another type is quoted. Nothing is ever
     * <em>loaded</em> with it, so the extra tags need no constructors. The sexagesimal branches are possessive, so a
     * scalar of any length is judged without recursing once per {@code :NN} segment (#441).
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
                            + "|[-+]?[1-9][0-9_]*+(?::[0-5]?[0-9])++)$"),
                    "-+0123456789");
            resolver.addImplicitResolver(
                    Tag.FLOAT,
                    Pattern.compile(
                            "^(?:[-+]?(?:[0-9][0-9_]*)?\\.[0-9_]*(?:[eE][-+][0-9]+)?"
                                    + "|[-+]?[0-9][0-9_]*+(?::[0-5]?[0-9])++\\.[0-9_]*|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN))$"),
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
                .setCodePointLimit(MAX_CODE_POINTS)
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
