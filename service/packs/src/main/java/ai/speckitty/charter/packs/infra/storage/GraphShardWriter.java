package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/**
 * Writes a {@link DoctrineGraph} as a graph shard in the canonical key order of the built-in shards. Each
 * {@link Relationship} is mapped to its {@link DrgEdge} DSO via {@link RelationshipMapper} and emitted in the
 * shard's established key order ({@code source, target, relation, impacts?, when?, reason?}); nodes are
 * emitted unchanged.
 */
public final class GraphShardWriter {

    private GraphShardWriter() {}

    /**
     * Writes {@code graph} as a graph shard.
     *
     * @param graph the graph to write
     * @return the shard's YAML text, with {@code generated_at} fixed to {@code STATIC} so the output is stable
     */
    public static String write(DoctrineGraph graph) {
        return write(graph, null);
    }

    /**
     * Writes {@code graph} as a graph shard that names its writer in {@code generated_by}.
     *
     * @param graph the graph to write
     * @param generatedBy the value of the shard's {@code generated_by}, or null to leave the key out
     * @return the shard's YAML text, with {@code generated_at} fixed to {@code STATIC} so the output is stable
     */
    public static String write(DoctrineGraph graph, @Nullable String generatedBy) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "1.0");
        root.put("generated_at", "STATIC");
        if (generatedBy != null) {
            root.put("generated_by", generatedBy);
        }
        List<Object> nodes = new ArrayList<>();
        for (DrgNode node : graph.nodes()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("urn", node.urn().toString());
            out.put("kind", node.urn().kind().token());
            node.label().ifPresent(label -> out.put("label", label));
            nodes.add(out);
        }
        root.put("nodes", nodes);
        List<Object> edges = new ArrayList<>();
        for (Relationship relationship : graph.relationships()) {
            DrgEdge edge = RelationshipMapper.toDso(relationship);
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
                .setSchema(Yaml.schema())
                .build();
        return new Dump(settings).dumpToString(root);
    }
}
