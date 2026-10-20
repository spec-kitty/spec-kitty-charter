package ai.speckitty.charter.yaml;

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

    static LoadSettings settings() {
        return LoadSettings.builder()
                .setSchema(new NullFormsSchema())
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
