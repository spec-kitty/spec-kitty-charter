package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.artifacts.application.ContextiveProjection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/**
 * The YAML text of a Contextive v1 definitions document (ADR-0076 decision 6): the tree {@link ContextiveProjection}
 * builds, written in block style with the keys in the decision's order. A context writes {@code name},
 * {@code domainVisionStatement}, {@code paths} and {@code terms}; a term writes {@code name}, {@code definition},
 * {@code examples}, {@code aliases} and {@code meta} ({@code urn}, then {@code status}). An optional member that is
 * empty is omitted, never written as {@code null} or as an empty list.
 *
 * <p>The rendering is stable: one document always gives the same text, with {@code \n} line breaks, two-space indents
 * and no line folding, so a long definition stays on one line. Quoting is the emitter's: a value that would not read
 * back as the same string is quoted. The caller encodes the text as UTF-8.
 */
final class ContextiveYaml {

    private static final DumpSettings SETTINGS = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setSplitLines(false)
            .setBestLineBreak("\n")
            .build();

    private ContextiveYaml() {}

    /**
     * The document as YAML text.
     *
     * @param document the Contextive document
     * @return its YAML text, ending with a line break
     */
    static String render(ContextiveProjection.Document document) {
        return new Dump(SETTINGS)
                .dumpToString(Map.of(
                        "contexts",
                        document.contexts().stream()
                                .map(ContextiveYaml::context)
                                .toList()));
    }

    private static Map<String, Object> context(ContextiveProjection.Context context) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", context.name());
        node.put("domainVisionStatement", context.domainVisionStatement());
        putPresent(node, "paths", context.paths());
        node.put("terms", context.terms().stream().map(ContextiveYaml::term).toList());
        return node;
    }

    private static Map<String, Object> term(ContextiveProjection.Term term) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", term.name());
        node.put("definition", term.definition());
        putPresent(node, "examples", term.examples());
        putPresent(node, "aliases", term.aliases());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("urn", term.meta().urn());
        meta.put("status", term.meta().status());
        node.put("meta", meta);
        return node;
    }

    private static void putPresent(Map<String, Object> node, String key, Optional<List<String>> values) {
        values.ifPresent(present -> node.put(key, present));
    }
}
