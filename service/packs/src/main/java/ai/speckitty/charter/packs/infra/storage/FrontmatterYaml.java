package ai.speckitty.charter.packs.infra.storage;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The YAML settings of this adapter, opened to the Markdown codec ({@code infra.storage.markdown}) for a document's
 * frontmatter, so the frontmatter is loaded and written exactly as every other YAML file of a pack is: the same
 * loader settings ({@link Yaml#settings()}) and the same writer settings ({@link YamlDumpEmitter}). It holds no
 * settings of its own.
 */
public final class FrontmatterYaml {

    private FrontmatterYaml() {}

    /**
     * Loads one YAML document with the adapter's loader settings.
     *
     * @param text the YAML text
     * @return the loaded value; {@code null} for an empty document or a YAML null
     * @throws org.snakeyaml.engine.v2.exceptions.YamlEngineException when the text is not valid YAML (for example a
     *     duplicate key)
     */
    public static @Nullable Object load(String text) {
        return Yaml.load(text);
    }

    /**
     * Writes a mapping as block-style YAML with the adapter's writer settings, keys in the mapping's order.
     *
     * @param mapping the mapping; values are loaded YAML values (scalars, possibly null, lists and mappings)
     * @return the YAML text, ending in a newline; {@code {}} plus a newline for an empty mapping
     */
    public static String dump(Map<String, ?> mapping) {
        YamlDumpEmitter emitter = new YamlDumpEmitter();
        mapping.forEach(emitter::present);
        return emitter.dump();
    }
}
