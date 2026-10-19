package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.ArtifactKind;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.DrgNode;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reads the two DRG document shapes into a {@link DoctrineGraph}: a built-in graph shard
 * ({@code <kind>.graph.yaml}: nodes by {@code urn}) and an org fragment ({@code drg/fragment.yaml}:
 * nodes by {@code id} with a plural {@code kind}). Unknown keys are refused, as core does.
 */
public final class GraphDocumentReader {

    /** Which of the two DRG document shapes a file has. */
    public enum Shape {
        /** A built-in graph shard, {@code <kind>.graph.yaml}: nodes by {@code urn}. */
        SHARD,
        /** An org fragment, {@code drg/fragment.yaml}: nodes by {@code id} with a plural {@code kind}. */
        FRAGMENT
    }

    private static final Set<String> SHARD_KEYS =
            Set.of("schema_version", "generated_at", "generated_by", "nodes", "edges");
    private static final Set<String> SHARD_NODE_KEYS = Set.of("urn", "kind", "label", "tags");
    private static final Set<String> FRAGMENT_KEYS =
            Set.of("pack_name", "source_kind", "source_ref", "layer_index", "provenance_marker", "nodes", "edges");
    private static final Set<String> FRAGMENT_NODE_KEYS = Set.of("id", "kind", "title", "body_path");
    private static final Set<String> EDGE_KEYS = Set.of("source", "target", "relation", "when", "reason");

    private GraphDocumentReader() {}

    /**
     * Reads one DRG document.
     *
     * @param text the document's YAML text
     * @param shape which of the two shapes the document has
     * @param origin the pack-relative name of the document, used in refusal details
     * @return the document's nodes and edges, in declared order
     * @throws ai.speckitty.charter.domain.CharterRefusal with {@code PACK_UNREADABLE} when the text is not YAML,
     *     {@code DRG_SCHEMA_ERROR} when the document breaks its shape's schema, or {@code UNRESOLVED_EDGE_ENDPOINT}
     *     when an edge endpoint is neither a URN nor a bare id the document declares
     */
    public static DoctrineGraph read(String text, Shape shape, String origin) {
        Object document;
        try {
            document = Yaml.load(text);
        } catch (RuntimeException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, origin + " is not valid YAML: " + firstLine(e), e);
        }
        Map<String, Object> root = mapping(document, origin);
        checkKeys(root, shape == Shape.SHARD ? SHARD_KEYS : FRAGMENT_KEYS, origin);
        List<DrgNode> nodes = new ArrayList<>();
        Map<String, Urn> declaredIds = new HashMap<>();
        for (Object item : list(root.get("nodes"), origin + " nodes")) {
            Map<String, Object> node = mapping(item, origin + " node");
            DrgNode parsed = shape == Shape.SHARD ? shardNode(node, origin) : fragmentNode(node, origin);
            nodes.add(parsed);
            declaredIds.putIfAbsent(parsed.urn().id(), parsed.urn());
        }
        List<DrgEdge> edges = new ArrayList<>();
        for (Object item : list(root.get("edges"), origin + " edges")) {
            Map<String, Object> edge = mapping(item, origin + " edge");
            checkKeys(edge, EDGE_KEYS, origin + " edge");
            String relationToken = string(edge, "relation", origin);
            Relation relation = Relation.fromToken(relationToken)
                    .orElseThrow(() -> schema(origin, "unknown relation '" + relationToken + "'"));
            edges.add(new DrgEdge(
                    endpoint(string(edge, "source", origin), declaredIds, origin),
                    endpoint(string(edge, "target", origin), declaredIds, origin),
                    relation,
                    optionalString(edge, "when", origin),
                    optionalString(edge, "reason", origin)));
        }
        return new DoctrineGraph(nodes, edges);
    }

    private static DrgNode shardNode(Map<String, Object> node, String origin) {
        checkKeys(node, SHARD_NODE_KEYS, origin + " node");
        String token = string(node, "urn", origin);
        Urn urn = Urn.parse(token).orElseThrow(() -> schema(origin, "node urn '" + token + "' is not <kind>:<id>"));
        String kind = string(node, "kind", origin);
        if (!kind.equals(urn.kind().token())) {
            throw schema(origin, "node " + urn + " declares kind '" + kind + "'");
        }
        return new DrgNode(urn, optionalString(node, "label", origin));
    }

    private static DrgNode fragmentNode(Map<String, Object> node, String origin) {
        checkKeys(node, FRAGMENT_NODE_KEYS, origin + " node");
        String id = string(node, "id", origin);
        String plural = string(node, "kind", origin);
        if (id.isEmpty()) {
            throw schema(origin, "a node of kind '" + plural + "' has an empty id");
        }
        NodeKind kind = pluralKind(plural)
                .orElseThrow(() -> schema(origin, "node " + id + " has unknown kind '" + plural + "'"));
        return new DrgNode(new Urn(kind, id), optionalString(node, "title", origin));
    }

    private static Optional<NodeKind> pluralKind(String plural) {
        return switch (plural) {
            case "mission_types" -> Optional.of(NodeKind.MISSION_TYPE);
            case "actions" -> Optional.of(NodeKind.ACTION);
            default -> ArtifactKind.fromPlural(plural).map(NodeKind::of);
        };
    }

    /** An endpoint is a URN of a known node kind, or a bare id the same document declares. */
    private static Urn endpoint(String token, Map<String, Urn> declaredIds, String origin) {
        return Urn.parse(token)
                .or(() -> Optional.ofNullable(declaredIds.get(token)))
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.UNRESOLVED_EDGE_ENDPOINT,
                        origin + ": edge endpoint '" + token + "' is neither a URN nor an id this document declares"));
    }

    /** A mapping whose keys are all strings, copied in document order; anything else is refused. */
    private static Map<String, Object> mapping(@Nullable Object value, String what) {
        if (!(value instanceof Map<?, ?> map)) {
            throw schema(what, "expected a mapping");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw schema(what, "expected a mapping");
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    private static List<?> list(@Nullable Object value, String what) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> items) {
            return items;
        }
        throw schema(what, "expected a list");
    }

    private static void checkKeys(Map<String, Object> map, Set<String> allowed, String what) {
        map.keySet().stream()
                .filter(k -> !allowed.contains(k))
                .sorted()
                .findFirst()
                .ifPresent(k -> {
                    throw schema(what, "unknown key '" + k + "'");
                });
    }

    private static String string(Map<String, Object> map, String key, String origin) {
        return optionalString(map, key, origin).orElseThrow(() -> schema(origin, "missing '" + key + "'"));
    }

    private static Optional<String> optionalString(Map<String, Object> map, String key, String origin) {
        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String s) {
            return Optional.of(s);
        }
        throw schema(origin, "'" + key + "' must be a string");
    }

    private static CharterRefusal schema(String origin, String problem) {
        return new CharterRefusal(RefusalCode.DRG_SCHEMA_ERROR, origin + ": " + problem);
    }

    /**
     * The first non-blank line of the parser's message: what it was doing or found, as {@code while parsing a block
     * mapping}. Its later lines hold the position and a snippet of the document, so nothing of the host reaches
     * the detail; the parser is given no file name, only the label {@code charter}.
     */
    private static String firstLine(RuntimeException e) {
        return String.valueOf(e.getMessage())
                .lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElse(e.getClass().getSimpleName());
    }
}
