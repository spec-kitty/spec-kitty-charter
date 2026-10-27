package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A DRG document of a pack as authored, every field its schema allows kept: a root graph shard
 * ({@code <name>.graph.yaml}, nodes by URN) or the org fragment ({@code drg/fragment.yaml}, nodes by id and plural
 * kind). {@link WholePackStore} carries these beside the aggregate so a whole pack can be written back (ADR-0036).
 * They stay out of the {@code Pack} aggregate: the artifacts also mint relationships, and which of the two copies of
 * an edge is authoritative is not settled, so nothing here pretends to keep them in step. Resolving them into a
 * doctrine graph is not this value's business.
 *
 * <p>Edges compare in canonical form: {@link #canonicalEdges()} ignores their order and duplicates. Nodes keep the
 * order they were written in. The values hold what the documents mean, not their bytes: an {@code impacts} weight is a
 * number ({@code 1} and {@code 1.0} are equal), and an absent list reads like an empty one.
 */
public sealed interface GraphDocument permits GraphDocument.Shard, GraphDocument.Fragment {

    /**
     * The document's pack-relative, {@code /}-separated path; it is unique in a pack.
     *
     * @return the path
     */
    String file();

    /**
     * The edges as written, in file order.
     *
     * @return an immutable list
     */
    List<Edge> edges();

    /**
     * The edges in canonical form: order and duplicates do not count.
     *
     * @return the distinct edges
     */
    default Set<Edge> canonicalEdges() {
        return Set.copyOf(edges());
    }

    /**
     * One edge, as either shape writes it. A fragment edge never has a {@code when}.
     *
     * @param source the source URN
     * @param target the target URN
     * @param relation the relation as written
     * @param when the {@code when} text, when written
     * @param reason the {@code reason} text, when written
     * @param impacts the {@code impacts} weight, when written
     */
    record Edge(
            String source,
            String target,
            String relation,
            Optional<String> when,
            Optional<String> reason,
            Optional<Double> impacts) {
        /**
         * Checks the fields.
         *
         * @param source the source URN
         * @param target the target URN
         * @param relation the relation
         * @param when the {@code when} text, possibly empty
         * @param reason the {@code reason} text, possibly empty
         * @param impacts the weight, possibly empty
         */
        public Edge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(impacts, "impacts");
        }
    }

    /**
     * A root graph shard.
     *
     * @param file the pack-relative path
     * @param schemaVersion the {@code schema_version}
     * @param generatedAt the {@code generated_at} text
     * @param generatedBy the {@code generated_by} text, when written
     * @param nodes the nodes, in file order
     * @param edges the edges, in file order
     */
    record Shard(
            String file,
            String schemaVersion,
            String generatedAt,
            Optional<String> generatedBy,
            List<ShardNode> nodes,
            List<Edge> edges)
            implements GraphDocument {
        /**
         * Checks the fields and copies the lists.
         *
         * @param file the pack-relative path
         * @param schemaVersion the schema version
         * @param generatedAt the generation time text
         * @param generatedBy the generator, possibly empty
         * @param nodes the nodes
         * @param edges the edges
         */
        public Shard {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(generatedAt, "generatedAt");
            Objects.requireNonNull(generatedBy, "generatedBy");
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    /**
     * A shard node.
     *
     * @param urn the node's URN
     * @param kind the singular kind as written
     * @param label the label, when written
     * @param provenance the provenance text, when written
     * @param tags the tags, in file order; empty when none are written
     */
    record ShardNode(String urn, String kind, Optional<String> label, Optional<String> provenance, List<String> tags) {
        /**
         * Checks the fields and copies the tags.
         *
         * @param urn the URN
         * @param kind the kind
         * @param label the label, possibly empty
         * @param provenance the provenance, possibly empty
         * @param tags the tags
         */
        public ShardNode {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(provenance, "provenance");
            tags = List.copyOf(tags);
        }
    }

    /**
     * The org fragment.
     *
     * @param file the pack-relative path
     * @param nodes the nodes, in file order
     * @param edges the edges, in file order
     */
    record Fragment(String file, List<FragmentNode> nodes, List<Edge> edges) implements GraphDocument {
        /**
         * Checks the path and copies the lists.
         *
         * @param file the pack-relative path
         * @param nodes the nodes
         * @param edges the edges
         */
        public Fragment {
            Objects.requireNonNull(file, "file");
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    /**
     * A fragment node.
     *
     * @param id the node's id
     * @param kind the plural kind as written
     * @param title the title, when written
     * @param bodyPath the {@code body_path}, when written
     */
    record FragmentNode(String id, String kind, Optional<String> title, Optional<String> bodyPath) {
        /**
         * Checks the fields.
         *
         * @param id the id
         * @param kind the plural kind
         * @param title the title, possibly empty
         * @param bodyPath the body path, possibly empty
         */
        public FragmentNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(bodyPath, "bodyPath");
        }
    }
}
