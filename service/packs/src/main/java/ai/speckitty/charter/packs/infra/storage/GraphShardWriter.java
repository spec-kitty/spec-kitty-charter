package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.DrgNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/** Writes a {@link DoctrineGraph} as a graph shard in the canonical key order of the built-in shards. */
public final class GraphShardWriter {

    private GraphShardWriter() {}

    /**
     * Writes {@code graph} as a graph shard.
     *
     * @param graph the graph to write
     * @return the shard's YAML text, with {@code generated_at} fixed to {@code STATIC} so the output is stable
     */
    public static String write(DoctrineGraph graph) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "1.0");
        root.put("generated_at", "STATIC");
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
        for (DrgEdge edge : graph.edges()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", edge.source().toString());
            out.put("target", edge.target().toString());
            out.put("relation", edge.relation().token());
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
