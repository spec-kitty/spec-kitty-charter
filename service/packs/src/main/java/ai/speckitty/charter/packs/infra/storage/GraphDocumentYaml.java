package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/**
 * The YAML form of the two DRG document shapes as the whole-pack load and write hold them: every field the
 * {@code graph-shard} and {@code org-fragment} schemas allow, read after schema validation and written back in the
 * key order the packs use. Unlike {@link GraphDocumentReader}, which reduces a document to the facts the pack policy
 * judges, nothing is dropped here.
 */
final class GraphDocumentYaml {

    private GraphDocumentYaml() {}

    /**
     * Reads one DRG document; never throws on content.
     *
     * @param text the document's text
     * @param shape which shape the document has
     * @param file the pack-relative path, named in a failure and kept on the document
     * @return the document, or why it is unreadable
     */
    static FileRead<GraphDocument> read(String text, Shape shape, String file) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations =
                PackSchemas.bundled().validate(shape == Shape.SHARD ? "graph-shard" : "org-fragment", file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException(file + " passed the schema but is no mapping");
        }
        return new FileRead.Read<>(file, shape == Shape.SHARD ? shard(root, file) : fragment(root, file));
    }

    private static GraphDocument.Shard shard(Map<?, ?> root, String file) {
        List<GraphDocument.ShardNode> nodes = new ArrayList<>();
        for (Object item : list(root, "nodes")) {
            if (item instanceof Map<?, ?> node) {
                nodes.add(new GraphDocument.ShardNode(
                        required(node, "urn"),
                        required(node, "kind"),
                        optional(node, "label"),
                        optional(node, "provenance"),
                        strings(node, "tags")));
            }
        }
        return new GraphDocument.Shard(
                file,
                required(root, "schema_version"),
                required(root, "generated_at"),
                optional(root, "generated_by"),
                nodes,
                edges(root));
    }

    private static GraphDocument.Fragment fragment(Map<?, ?> root, String file) {
        List<GraphDocument.FragmentNode> nodes = new ArrayList<>();
        for (Object item : list(root, "nodes")) {
            if (item instanceof Map<?, ?> node) {
                nodes.add(new GraphDocument.FragmentNode(
                        required(node, "id"),
                        required(node, "kind"),
                        optional(node, "title"),
                        optional(node, "body_path")));
            }
        }
        return new GraphDocument.Fragment(file, nodes, edges(root));
    }

    private static List<GraphDocument.Edge> edges(Map<?, ?> root) {
        List<GraphDocument.Edge> edges = new ArrayList<>();
        for (Object item : list(root, "edges")) {
            if (item instanceof Map<?, ?> edge) {
                edges.add(new GraphDocument.Edge(
                        required(edge, "source"),
                        required(edge, "target"),
                        required(edge, "relation"),
                        optional(edge, "when"),
                        optional(edge, "reason"),
                        edge.get("impacts") instanceof Number n ? Optional.of(n.doubleValue()) : Optional.empty()));
            }
        }
        return edges;
    }

    /**
     * Writes one DRG document from scratch.
     *
     * @param document the document
     * @return the file text
     */
    static String write(GraphDocument document) {
        Map<String, Object> root = new LinkedHashMap<>();
        switch (document) {
            case GraphDocument.Shard shard -> {
                root.put("schema_version", shard.schemaVersion());
                root.put("generated_at", shard.generatedAt());
                shard.generatedBy().ifPresent(by -> root.put("generated_by", by));
                List<Object> nodes = new ArrayList<>();
                for (GraphDocument.ShardNode node : shard.nodes()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("urn", node.urn());
                    out.put("kind", node.kind());
                    node.label().ifPresent(label -> out.put("label", label));
                    node.provenance().ifPresent(provenance -> out.put("provenance", provenance));
                    if (!node.tags().isEmpty()) {
                        out.put("tags", node.tags());
                    }
                    nodes.add(out);
                }
                root.put("nodes", nodes);
            }
            case GraphDocument.Fragment fragment -> {
                List<Object> nodes = new ArrayList<>();
                for (GraphDocument.FragmentNode node : fragment.nodes()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", node.id());
                    out.put("kind", node.kind());
                    node.title().ifPresent(title -> out.put("title", title));
                    node.bodyPath().ifPresent(path -> out.put("body_path", path));
                    nodes.add(out);
                }
                root.put("nodes", nodes);
            }
        }
        List<Object> edges = new ArrayList<>();
        for (GraphDocument.Edge edge : document.edges()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", edge.source());
            out.put("target", edge.target());
            out.put("relation", edge.relation());
            edge.impacts().ifPresent(strength -> out.put("impacts", strength));
            edge.when().ifPresent(when -> out.put("when", when));
            edge.reason().ifPresent(reason -> out.put("reason", reason));
            edges.add(out);
        }
        root.put("edges", edges);
        DumpSettings settings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setWidth(4096)
                .setSchema(Yaml.dumpSchema())
                .build();
        return new Dump(settings).dumpToString(root);
    }
}
