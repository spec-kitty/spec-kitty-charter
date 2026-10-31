package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.packs.domain.PackPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/**
 * Writes a {@link DoctrineGraph} as a graph shard, or as an org fragment, in the canonical key order of the packs. Each
 * {@link Relationship} is mapped to its {@link DrgEdge} DSO via {@link RelationshipMapper} and emitted in the edge key
 * order ({@code source, target, relation, impacts?, when?, reason?}); each {@link Tension} is written back as the
 * {@code in_tension_with} edge it was read from, with no strength (ADR-0028, ADR-0036 Decision 4, ADR-0044).
 *
 * <p>Every field the document shape holds is written, and a field it cannot hold is refused rather than dropped
 * (ADR-0036: a node's value is every declared field). A shard node is written {@code urn, kind, label?, provenance?,
 * tags?}, and a shard has no {@code body_path}. A fragment node is written {@code id, kind, title?, body_path?}, and a
 * fragment has no node {@code provenance} or {@code tags} and no edge {@code when}. A refusal is an
 * {@link IllegalArgumentException}: a caller handed this writer a graph its shape cannot hold.
 */
public final class GraphShardWriter {

    private GraphShardWriter() {}

    /**
     * Writes {@code graph} as a graph shard.
     *
     * @param graph the graph to write
     * @return the shard's YAML text, with {@code generated_at} fixed to {@code STATIC} so the output is stable
     * @throws IllegalArgumentException when a node has a body path, which a shard cannot hold
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
     * @throws IllegalArgumentException when a node has a body path, which a shard cannot hold
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
            refuse(node.bodyPath().isPresent(), node, "body_path", "a shard");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("urn", node.urn().toString());
            out.put("kind", node.urn().kind().token());
            node.label().ifPresent(label -> out.put("label", label));
            node.provenance().ifPresent(provenance -> out.put("provenance", provenance));
            if (!node.tags().isEmpty()) {
                out.put("tags", node.tags());
            }
            nodes.add(out);
        }
        root.put("nodes", nodes);
        root.put("edges", edges(graph, true));
        return dump(root);
    }

    /**
     * Writes {@code graph} as an org fragment ({@code drg/fragment.yaml}): nodes by bare id and plural kind, and no
     * header. No production path writes a fragment from the domain yet; this writer proves that a fragment, and a
     * node's {@code body_path} with it, survives the domain round trip (#236).
     *
     * @param graph the graph to write
     * @return the fragment's YAML text
     * @throws IllegalArgumentException when a node has provenance or tags, or an edge has a condition, none of which a
     *     fragment can hold
     */
    public static String writeFragment(DoctrineGraph graph) {
        List<Object> nodes = new ArrayList<>();
        for (DrgNode node : graph.nodes()) {
            refuse(node.provenance().isPresent(), node, "provenance", "a fragment");
            refuse(!node.tags().isEmpty(), node, "tags", "a fragment");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", node.urn().id());
            out.put("kind", node.urn().kind().plural());
            node.label().ifPresent(title -> out.put("title", title));
            node.bodyPath().ifPresent(path -> out.put("body_path", path));
            nodes.add(out);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nodes", nodes);
        root.put("edges", edges(graph, false));
        return dump(root);
    }

    /** The relationships, then the tensions; edges are a set, so the order carries no meaning (ADR-0036). */
    private static List<Object> edges(DoctrineGraph graph, boolean conditionAllowed) {
        List<Object> edges = new ArrayList<>();
        for (Relationship relationship : graph.relationships()) {
            DrgEdge edge = RelationshipMapper.toDso(relationship);
            edges.add(edge(
                    edge.source(),
                    edge.target(),
                    edge.relation(),
                    edge.impacts(),
                    edge.when(),
                    edge.reason(),
                    conditionAllowed));
        }
        for (Tension tension : graph.tensions()) {
            edges.add(edge(
                    tension.from().toString(),
                    tension.to().toString(),
                    PackPolicy.TENSION,
                    Optional.empty(),
                    tension.when(),
                    tension.rationale(),
                    conditionAllowed));
        }
        return edges;
    }

    private static Map<String, Object> edge(
            String source,
            String target,
            String relation,
            Optional<Double> impacts,
            Optional<String> when,
            Optional<String> reason,
            boolean conditionAllowed) {
        if (!conditionAllowed && when.isPresent()) {
            throw new IllegalArgumentException(
                    "a fragment cannot hold the 'when' of the " + relation + " edge " + source + " -> " + target);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("target", target);
        out.put("relation", relation);
        impacts.ifPresent(strength -> out.put("impacts", strength));
        when.ifPresent(condition -> out.put("when", condition));
        reason.ifPresent(text -> out.put("reason", text));
        return out;
    }

    private static void refuse(boolean present, DrgNode node, String field, String shape) {
        if (present) {
            throw new IllegalArgumentException(shape + " cannot hold the '" + field + "' of node " + node.urn());
        }
    }

    private static String dump(Map<String, Object> root) {
        DumpSettings settings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setWidth(4096)
                .setSchema(Yaml.dumpSchema())
                .build();
        return new Dump(settings).dumpToString(root);
    }
}
