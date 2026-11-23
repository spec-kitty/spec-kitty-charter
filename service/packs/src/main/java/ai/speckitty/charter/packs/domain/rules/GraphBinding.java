package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds a graph document as written to a {@link DoctrineGraph}, for a pack's graph documents and for the store's
 * registered-relationship shard alike, and collects what does not bind as findings.
 */
public final class GraphBinding {

    /** The list of a graph document's nodes. */
    public static final String NODES = "nodes";

    /** The list of a graph document's edges. */
    public static final String EDGES = "edges";

    /** The finding text of an authored edge to or from a guard or a lifecycle (#156), before the endpoint. */
    public static final String NOT_AUTHORED =
            "a guard or a lifecycle is not an authored edge endpoint until its contract set lands: ";

    /**
     * The finding text of a graph node or an authored edge that names a routing catalog (ADR-0080 decision 5, squad
     * A1), before the node or the endpoint: a catalog layers by its id, and nothing relates to it.
     */
    public static final String NOT_A_GRAPH_KIND =
            "a routing catalog is no graph node and no edge endpoint, it layers by its id: ";

    /**
     * The finding text, before the node or the endpoint, of a kind that is no authored graph node or edge endpoint.
     *
     * @param kind the kind a node or an endpoint names
     * @return {@link #NOT_A_GRAPH_KIND} for a routing catalog, {@link #NOT_AUTHORED} for a guard or a lifecycle
     */
    public static String notAuthored(NodeKind kind) {
        return kind == NodeKind.ROUTING_CATALOG ? NOT_A_GRAPH_KIND : NOT_AUTHORED;
    }

    /**
     * The finding text of an authored or registered edge that names a term anywhere but as the target of a
     * {@code vocabulary} edge (ADR-0049 decision 4; operator ruling of 2026-11-14), before the edge.
     */
    public static final String TERM_ENDPOINT = "a term is an edge endpoint only as the target of a vocabulary edge: ";

    private GraphBinding() {}

    /**
     * A graph built from facts, with what could not be bound.
     *
     * @param graph the nodes and edges that bound
     * @param findings one finding for each node or edge that did not bind, in document order
     */
    public record GraphBuild(DoctrineGraph graph, List<Reason> findings) {
        /**
         * Copies the findings.
         *
         * @param graph the graph
         * @param findings the findings
         */
        public GraphBuild {
            findings = List.copyOf(findings);
        }
    }

    /**
     * Builds the graph of one document and collects what does not bind. A {@code tension} edge binds like any other
     * relation (ADR-0045 decision 1). Every declared node field is carried onto its {@link DrgNode} (ADR-0036).
     *
     * <p>A node or edge with a finding is left out of the graph, so the constructor checks of {@link Relationship}
     * stay programming-error guards and never fire. An edge reports its first fault only, checked
     * in the order the adapter's reader used: relation, then the endpoints, then a self-loop. The schema normally
     * refuses these first; the domain check keeps the same text for a document that reaches here anyway.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the document as written
     * @return the graph and the findings
     */
    public static GraphBuild graph(String packName, RawGraph raw) {
        return build(packName, raw, false);
    }

    /**
     * Builds the graph of the registered-relationship shard. It holds every registered relationship, tensions among
     * them (ADR-0045 decision 5); an edge the endpoint table or the source rule refuses is reported here, because the
     * shard is read on its own.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the registered shard as written
     * @return the graph and the findings
     */
    public static GraphBuild registeredGraph(String packName, RawGraph raw) {
        return build(packName, raw, true);
    }

    private static GraphBuild build(String packName, RawGraph raw, boolean registered) {
        String origin = Paths.origin(packName, raw.file());
        List<DrgNode> nodes = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        List<Reason> findings = new ArrayList<>();
        for (int i = 0; i < raw.nodes().size(); i++) {
            RawNode node = raw.nodes().get(i);
            Optional<DrgNode> bound = switch (raw.shape()) {
                case SHARD -> shardNode(node);
                case FRAGMENT -> fragmentNode(node);
            };
            if (bound.isPresent()) {
                nodes.add(bound.get());
            } else {
                findings.add(graphFinding(raw, NODES, i, nodeProblem(raw.shape(), node, origin)));
            }
        }
        for (int i = 0; i < raw.edges().size(); i++) {
            RawEdge edge = raw.edges().get(i);
            Object outcome = edge(edge, origin);
            switch (outcome) {
                case Relationship relationship -> relationships.add(relationship);
                // Refused by the table or by the source rule. In a pack's graph documents each pack-level pass reports
                // it under its own code; the registered shard is read on its own, so its read reports it here.
                case EndpointRule.Fault refused -> {
                    if (registered) {
                        findings.add(Findings.at(
                                EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE.equals(refused.code())
                                        ? PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE
                                        : PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED,
                                raw.file(),
                                List.of(EDGES, Integer.toString(i)),
                                origin + ": " + refused.message()));
                    }
                }
                case SelfTension self ->
                    findings.add(Findings.at(
                            PackCodes.TENSION_SELF, raw.file(), List.of(EDGES, Integer.toString(i)), self.detail()));
                default -> findings.add(graphFinding(raw, EDGES, i, String.valueOf(outcome)));
            }
        }
        return new GraphBuild(new DoctrineGraph(nodes, relationships), findings);
    }

    private static Reason graphFinding(RawGraph raw, String list, int index, String detail) {
        return Findings.at(PackCodes.DRG_SCHEMA_ERROR, raw.file(), List.of(list, Integer.toString(index)), detail);
    }

    private static Optional<DrgNode> shardNode(RawNode node) {
        return Urn.parse(node.ref())
                .filter(urn -> urn.kind().declarableInGraphs())
                .map(urn -> boundNode(urn, node));
    }

    /** The node with every field written for it; a fragment node has a body path, a shard node provenance and tags. */
    private static DrgNode boundNode(Urn urn, RawNode node) {
        return new DrgNode(urn, node.label(), node.provenance(), node.tags(), node.bodyPath());
    }

    private static Optional<DrgNode> fragmentNode(RawNode node) {
        if (node.ref().isEmpty()) {
            return Optional.empty();
        }
        return NodeKind.fromPlural(node.kind())
                .filter(NodeKind::declarableInGraphs)
                .map(kind -> boundNode(new Urn(kind, node.ref()), node));
    }

    private static String nodeProblem(RawGraph.Shape shape, RawNode node, String origin) {
        return switch (shape) {
            case SHARD ->
                Urn.parse(node.ref())
                        .map(urn -> origin + ": " + notAuthored(urn.kind()) + "'" + node.ref() + "'")
                        .orElseGet(() -> origin + ": node urn '" + node.ref() + "' is not <kind>:<id>");
            case FRAGMENT ->
                node.ref().isEmpty()
                        ? origin + ": " + emptyId(node.kind())
                        : NodeKind.fromPlural(node.kind())
                                .map(kind -> origin + ": " + notAuthored(kind) + "'" + new Urn(kind, node.ref()) + "'")
                                .orElseGet(() ->
                                        origin + ": node " + node.ref() + " has unknown kind '" + node.kind() + "'");
        };
    }

    /**
     * The text of a fragment node with an empty id; the detail vocabulary gives a schema violation the same.
     *
     * @param kind the node's plural kind
     * @return the text
     */
    public static String emptyId(String kind) {
        return "a node of kind '" + kind + "' has an empty id";
    }

    /**
     * Binds one edge; the result is the {@link Relationship}, the {@link EndpointRule.Fault} of an edge whose relation
     * does not allow its endpoints, or the finding text for the first other fault.
     */
    private static Object edge(RawEdge raw, String origin) {
        Optional<RelationType> relation = RelationType.fromToken(raw.relation());
        if (relation.isEmpty()) {
            return origin + ": unknown relation '" + raw.relation() + "'";
        }
        if (relation.get().derived()) {
            return origin + ": relation '" + raw.relation() + "' is derived from an artifact's own field, never"
                    + " authored as an edge";
        }
        Object endpoints = endpoints(raw, origin, relation.get());
        if (!(endpoints instanceof Endpoints bound)) {
            return endpoints;
        }
        return switch (EndpointRule.bind(
                relation.get(), bound.from(), bound.fromPack(), bound.to(), bound.toPack(), raw.when(), raw.reason())) {
            case EndpointRule.Bound typed -> typed.relationship();
            case EndpointRule.Fault fault -> fault;
        };
    }

    /** The finding text of a tension whose two sides have the same bare URN ({@link PackCodes#TENSION_SELF}). */
    private record SelfTension(String detail) {}

    /** The two endpoints of an edge: each a bare URN and the pack its reference names, if it is pack-qualified. */
    private record Endpoints(Urn from, Optional<String> fromPack, Urn to, Optional<String> toPack) {}

    /**
     * The endpoints of an edge, or the finding text for the first that does not bind.
     *
     * @param relation the edge's relation type
     */
    private static Object endpoints(RawEdge raw, String origin, RelationType relation) {
        Optional<String> malformed = malformed(raw.source(), origin).or(() -> malformed(raw.target(), origin));
        if (malformed.isPresent()) {
            return malformed.get();
        }
        Optional<PackQualifiedReference> source = reference(raw.source());
        Optional<Urn> sourceUrn = source.flatMap(PackQualifiedReference::asUrn);
        if (sourceUrn.isEmpty()) {
            return origin + ": edge endpoint '" + raw.source() + "' is not a URN";
        }
        Optional<PackQualifiedReference> target = reference(raw.target());
        Optional<Urn> targetUrn = target.flatMap(PackQualifiedReference::asUrn);
        if (targetUrn.isEmpty()) {
            return origin + ": edge endpoint '" + raw.target() + "' is not a URN";
        }
        // A tension joins two rules: an artifact and itself, or two versions of one artifact, qualified or not, are not
        // two rules (ADR-0045 decision 1). It is reported under its own code.
        if (relation == RelationType.TENSION && sourceUrn.get().equals(targetUrn.get())) {
            return new SelfTension(origin + ": " + selfTension(sourceUrn.get()));
        }
        // Only a bare endpoint pair is a self-loop: a qualifier names another pack's artifact of the same id
        // (`a::directive:X -> directive:X` joins two artifacts), which is legal (ADR-0053 decision 7).
        if (sourceUrn.get().equals(targetUrn.get())
                && source.get().packName().isEmpty()
                && target.get().packName().isEmpty()) {
            return origin + ": " + selfLoop(sourceUrn.get());
        }
        for (Urn endpoint : List.of(sourceUrn.get(), targetUrn.get())) {
            if (!endpoint.kind().edgeTarget()) {
                return origin + ": " + notAuthored(endpoint.kind()) + "'" + endpoint + "'";
            }
        }
        if (!sourceUrn.get().kind().edgeSource() || !targetUrn.get().kind().edgeTargetOf(relation)) {
            return origin + ": " + TERM_ENDPOINT + "'" + raw.source() + "' " + raw.relation() + " '" + raw.target()
                    + "'";
        }
        return new Endpoints(
                sourceUrn.get(),
                source.get().packName(),
                targetUrn.get(),
                target.get().packName());
    }

    /**
     * The finding for an endpoint written as a pack-qualified reference that does not parse, a pack name that is no
     * pack id among them (#582), with the parser's reason, so the refusal says what a reference must be.
     */
    private static Optional<String> malformed(String text, String origin) {
        if (!text.contains("::")) {
            return Optional.empty();
        }
        try {
            PackQualifiedReference.parse(text);
            return Optional.empty();
        } catch (IllegalArgumentException refused) {
            return Optional.of(origin + ": edge endpoint is not a reference: " + refused.getMessage());
        }
    }

    /** The endpoint text split by {@link PackQualifiedReference}; empty when its qualifier is malformed. */
    private static Optional<PackQualifiedReference> reference(String text) {
        try {
            return Optional.of(PackQualifiedReference.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * The fault of an edge that joins an artifact to itself, as a registration states it (#617): the endpoints are read
     * with the pack's name ({@link #joinsItself}), so an edge qualified with the pack's own name is the fault its bare
     * form is. An endpoint that is no reference is judged where its document is read.
     *
     * @param packName the descriptor name of the pack that writes the edge
     * @param file the edge's pack-relative graph document
     * @param index the edge's index in the document's {@code edges} list, which the fault points at
     * @param source the source as written
     * @param target the target as written
     * @param relation the relation as written
     * @return the fault, when the edge joins an artifact to itself
     */
    public static Optional<PackElementViolation> selfJoin(
            String packName, String file, int index, String source, String target, String relation) {
        Optional<PackQualifiedReference> from = reference(source);
        Optional<PackQualifiedReference> to = reference(target);
        if (from.isEmpty() || to.isEmpty() || !joinsItself(packName, from.get(), to.get())) {
            return Optional.empty();
        }
        return from.get()
                .asUrn()
                .map(urn -> RelationType.TENSION.token().equals(relation)
                        ? Findings.atEdge(file, index, PackCodes.TENSION_SELF, selfTension(urn))
                        : Findings.atEdge(file, index, PackCodes.DRG_SCHEMA_ERROR, selfLoop(urn)));
    }

    private static String selfTension(Urn urn) {
        return "a tension cannot join '" + urn + "' to itself or to a version of itself";
    }

    private static String selfLoop(Urn urn) {
        return "a relationship cannot join '" + urn + "' to itself";
    }

    /**
     * The one rule for a self-loop in a pack named {@code packName}: two references join an artifact to itself when
     * they name the same URN and both mean the writing pack, a bare one by default and a qualified one by naming it
     * ({@code pack-a::directive:X} inside {@code pack-a}). A reference qualified with another pack names another
     * pack's artifact of the same id, which is legal (ADR-0053 decision 7).
     *
     * @param packName the descriptor name of the pack that writes both references
     * @param from the reference on one side
     * @param to the reference on the other side
     * @return {@code true} when both mean the same artifact of the writing pack
     */
    public static boolean joinsItself(String packName, PackQualifiedReference from, PackQualifiedReference to) {
        Objects.requireNonNull(packName, "packName");
        return from.urn().equals(to.urn())
                && from.packName().orElse(packName).equals(packName)
                && to.packName().orElse(packName).equals(packName);
    }
}
