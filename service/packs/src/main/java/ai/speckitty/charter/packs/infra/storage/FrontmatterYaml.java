package ai.speckitty.charter.packs.infra.storage;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The YAML settings of this adapter, opened to the Markdown codec ({@code infra.storage.markdown}) for a document's
 * frontmatter, so the frontmatter is loaded and written exactly as every other YAML file of a pack is: the same
 * loader settings ({@link Yaml#settings()}) behind the same guards ({@link ParsedYaml}), and the same writer settings
 * ({@link YamlDumpEmitter}). It holds no settings of its own.
 */
public final class FrontmatterYaml {

    private FrontmatterYaml() {}

    /**
     * Frontmatter text loaded through the guards every pack YAML file passes ({@code ParsedYaml}): the depth cap, the
     * node cap over aliases, string mapping keys, and plain scalars only (so {@code !!binary} is refused, never a
     * {@code byte[]} in the model).
     *
     * @param document the loaded value; {@code null} for an empty document or a YAML null, and when there is a problem
     * @param problem why the text is refused, a short phrase or the parser's first line; {@code null} when it is not
     * @param line the 1-based line of the text the parser names when the parser itself refused it; 0 when a guard
     *     refused it (a guard names no line) or there is no problem
     */
    public record Loaded(
            @Nullable Object document, @Nullable String problem, int line) {}

    /**
     * Loads one YAML document with the adapter's loader settings and the guards of every pack YAML file.
     *
     * @param text the YAML text
     * @return the loaded value or the problem; never throws on content
     */
    public static Loaded load(String text) {
        ParsedYaml.Checked checked = ParsedYaml.check(text);
        return new Loaded(checked.document(), checked.problem(), checked.line());
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
