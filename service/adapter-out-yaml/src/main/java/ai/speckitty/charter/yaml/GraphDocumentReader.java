package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawEdge;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackContents.RawNode;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads the two DRG document shapes as facts: a built-in graph shard ({@code <kind>.graph.yaml}: nodes by
 * {@code urn}) and an org fragment ({@code drg/fragment.yaml}: nodes by {@code id} with a plural {@code kind}).
 *
 * <p>The reader decides nothing. A document that is not YAML is {@link FileRead.Unreadable} with the parse failure; a
 * document that breaks its bundled schema ({@code graph-shard} or {@code org-fragment}) is {@link FileRead.Unreadable}
 * with the validator's facts; any other document is read as written, with nothing bound or defaulted. Every code and
 * every text is {@code PackPolicy}'s, including the legacy {@code in_tension_with} mapping, which stays the word on
 * the page here. It never throws on content.
 */
public final class GraphDocumentReader {

    /** Which of the two DRG document shapes a file has. */
    public enum Shape {
        /** A built-in graph shard, {@code <kind>.graph.yaml}: nodes by {@code urn}. */
        SHARD,
        /** An org fragment, {@code drg/fragment.yaml}: nodes by {@code id} with a plural {@code kind}. */
        FRAGMENT
    }

    private GraphDocumentReader() {}

    /**
     * Reads one DRG document.
     *
     * @param text the document's YAML text
     * @param shape which of the two shapes the document has
     * @param file the pack-relative path of the document
     * @return the document as written, or why it could not be read
     */
    public static FileRead<RawGraph> read(String text, Shape shape, String file) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations =
                PackSchemas.bundled().validate(shape == Shape.SHARD ? "graph-shard" : "org-fragment", file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, withSiblingFacts(violations, document, shape));
        }
        return new FileRead.Read<>(file, raw(document, shape, file));
    }

    // ---- the document as written -----------------------------------------------------------------

    private static RawGraph raw(@Nullable Object document, Shape shape, String file) {
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException(file + " passed the schema but is no mapping");
        }
        List<RawNode> nodes = new ArrayList<>();
        for (Object item : items(root, "nodes")) {
            Map<?, ?> node = (Map<?, ?>) item;
            nodes.add(
                    shape == Shape.SHARD
                            ? new RawNode(string(node, "urn"), string(node, "kind"), optionalString(node, "label"))
                            : new RawNode(string(node, "id"), string(node, "kind"), optionalString(node, "title")));
        }
        List<RawEdge> edges = new ArrayList<>();
        for (Object item : items(root, "edges")) {
            Map<?, ?> edge = (Map<?, ?>) item;
            edges.add(new RawEdge(
                    string(edge, "source"),
                    string(edge, "target"),
                    string(edge, "relation"),
                    edge.get("impacts") instanceof Number n ? Optional.of(n.doubleValue()) : Optional.empty(),
                    optionalString(edge, "when"),
                    optionalString(edge, "reason")));
        }
        return new RawGraph(file, shape == Shape.SHARD ? RawGraph.Shape.SHARD : RawGraph.Shape.FRAGMENT, nodes, edges);
    }

    /** The list under {@code key}; the schema has checked that it is a list of mappings when it is there. */
    private static List<?> items(Map<?, ?> root, String key) {
        return root.get(key) instanceof List<?> list ? list : List.of();
    }

    /** A string the schema has already required; anything else means the reader and the schema have drifted apart. */
    private static String string(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String s) {
            return s;
        }
        throw new IllegalStateException("'" + key + "' passed the schema but is not a string");
    }

    private static Optional<String> optionalString(Map<?, ?> map, String key) {
        return map.get(key) instanceof String s ? Optional.of(s) : Optional.empty();
    }

    // ---- the facts the validator does not emit ---------------------------------------------------

    /**
     * Adds the named facts of {@code PackPolicy}'s vocabulary contract to the violations that need them:
     * {@code kind}, the sibling plural kind of a fragment node, on a violation of its {@code id}; and {@code id} (a
     * fragment node) or {@code urn} (a shard node), the sibling identity, on an {@code enum} violation of its
     * {@code kind}. The validator reports only the offending place; the node's other fields come from the document.
     */
    private static List<SchemaViolation> withSiblingFacts(
            List<SchemaViolation> violations, @Nullable Object document, Shape shape) {
        List<SchemaViolation> enriched = new ArrayList<>();
        for (SchemaViolation violation : violations) {
            Map<String, String> sibling = siblingFacts(violation, document, shape);
            if (sibling.isEmpty()) {
                enriched.add(violation);
                continue;
            }
            Map<String, String> args = new LinkedHashMap<>(violation.args());
            args.putAll(sibling);
            enriched.add(new SchemaViolation(
                    violation.file(),
                    violation.pointer(),
                    violation.keyword(),
                    violation.property(),
                    violation.value(),
                    args));
        }
        return enriched;
    }

    private static Map<String, String> siblingFacts(SchemaViolation violation, @Nullable Object document, Shape shape) {
        List<String> pointer = violation.pointer();
        if (pointer.size() != 3 || !"nodes".equals(pointer.get(0)) || !(document instanceof Map<?, ?> root)) {
            return Map.of();
        }
        Map<?, ?> node = nodeAt(root, pointer.get(1));
        if (node == null) {
            return Map.of();
        }
        String field = pointer.get(2);
        if (shape == Shape.FRAGMENT && "id".equals(field) && node.get("kind") instanceof String kind) {
            return Map.of("kind", kind);
        }
        if ("kind".equals(field) && "enum".equals(violation.keyword())) {
            String identity = shape == Shape.FRAGMENT ? "id" : "urn";
            if (node.get(identity) instanceof String value) {
                return Map.of(identity, value);
            }
        }
        return Map.of();
    }

    private static @Nullable Map<?, ?> nodeAt(Map<?, ?> root, String index) {
        if (!(root.get("nodes") instanceof List<?> nodes)) {
            return null;
        }
        int position;
        try {
            position = Integer.parseInt(index);
        } catch (NumberFormatException e) {
            return null;
        }
        return position >= 0 && position < nodes.size() && nodes.get(position) instanceof Map<?, ?> map ? map : null;
    }
}
