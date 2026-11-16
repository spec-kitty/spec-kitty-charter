package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The kernel's endpoint rule ({@link EndpointRule}) applied to the edges of a pack's graph documents (FR-014, FR-015,
 * #350, #447): an edge is refused with the fault the bridge gives, in the bridge's order.
 *
 * <ol>
 *   <li>An {@code overrides} edge whose source is not of an activatable kind is refused with
 *       {@value EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} (slice C's rule, folded into the bridge by operator
 *       ruling DM-01M7B1Z03VF9A36FBV65431HR3). It is judged on the source alone, whatever the target is, a source
 *       written with a pack name like a bare one ({@link EndpointRule#overridesSourceRefusal}).
 *   <li>Otherwise an edge whose relation does not allow its source and target kinds is refused with
 *       {@value EndpointRule#ENDPOINT_NOT_ALLOWED} ({@link EndpointRule#bind}).
 * </ol>
 *
 * <p>An {@code enhances} or {@code overrides} edge from a step enhancement the pack holds, written bare or qualified with
 * the pack's own name, is judged before both by the step-enhancement rules of ADR-0066, under
 * {@code prompt_fragment_invalid}, so the table does not judge it. An edge from a step enhancement the pack does not
 * hold, or from another pack's, is no fact of those rules, so the table judges it here.
 *
 * <p>An endpoint that is no reference, an unknown relation, a derived relation and a bare self-loop are faults of the
 * graph rules, which refuse them on their own. An {@code in_tension_with} edge joins two guidance kinds
 * ({@link EndpointRule#bindTension}).
 *
 * <p>The load records each fault as a violation of the pack at its graph document, so a registration lists one
 * violation per edge inside {@code 422 validation_failed} and a composing read answers {@code 500 pack_unreadable}
 * naming the document. Validate mode reports the same fault as a blocking finding at the edge. It is pure.
 */
public final class RelationshipEndpointRule {

    /** The violation code of an edge the table refuses: the one spelling, at registration, on the write and at load. */
    public static final String CODE = EndpointRule.ENDPOINT_NOT_ALLOWED;

    /**
     * One edge of a graph document, as written.
     *
     * @param file the pack-relative path of the graph document
     * @param source the source, bare or written with a pack name
     * @param target the target, as written
     * @param relation the relation token, as written
     */
    public record Edge(String file, String source, String target, String relation) {
        /**
         * Checks that every field is present.
         *
         * @param file the graph document
         * @param source the source
         * @param target the target
         * @param relation the relation
         */
        public Edge {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
        }
    }

    /**
     * Why one edge is refused.
     *
     * @param code the bridge's fault code, {@value EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} or {@value #CODE}
     * @param reason what is wrong, naming the edge
     */
    public record Refusal(String code, String reason) {
        /**
         * Checks that every field is present.
         *
         * @param code the code
         * @param reason the reason
         */
        public Refusal {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private static final String TENSION = GraphBinding.TENSION;

    private RelationshipEndpointRule() {}

    /**
     * The step enhancements whose edges the step-enhancement rules judge: the pack's own name, and the ids of the
     * enhancements it holds.
     *
     * @param packName the name composition knows the pack by
     * @param heldEnhancements the ids of the step enhancements the pack holds
     */
    public record Scope(String packName, Set<String> heldEnhancements) {
        /**
         * Copies the ids.
         *
         * @param packName the pack's name
         * @param heldEnhancements the held ids
         */
        public Scope {
            Objects.requireNonNull(packName, "packName");
            heldEnhancements = Set.copyOf(heldEnhancements);
        }

        /** Whether the step-enhancement rules judge an edge of this relation from this source, as written. */
        boolean judgesEnhancementEdge(RelationType relation, PackQualifiedReference source, Urn sourceUrn) {
            return (relation == RelationType.ENHANCES || relation == RelationType.OVERRIDES)
                    && sourceUrn.kind() == NodeKind.STEP_ENHANCEMENT
                    && source.packName().map(packName::equals).orElse(true)
                    && heldEnhancements.contains(sourceUrn.id());
        }
    }

    /**
     * Judges one edge as written.
     *
     * @param edge the edge
     * @param scope the pack whose document holds it
     * @return why it is refused, or empty when the bridge allows it or another rule judges it
     */
    public static Optional<Refusal> refusal(Edge edge, Scope scope) {
        Optional<String> overridesSource = overridesSourceRefusal(edge);
        if (overridesSource.isPresent()) {
            return Optional.of(new Refusal(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE, overridesSource.get()));
        }
        return tableRefusal(edge, scope).map(reason -> new Refusal(CODE, reason));
    }

    /** The bridge's source rule on the source as written; a source that is no reference is the graph rules'. */
    private static Optional<String> overridesSourceRefusal(Edge edge) {
        if (!RelationType.OVERRIDES.token().equals(edge.relation())) {
            return Optional.empty();
        }
        return reference(edge.source())
                .flatMap(PackQualifiedReference::asUrn)
                .flatMap(from -> EndpointRule.overridesSourceRefusal(
                        RelationType.OVERRIDES, from, edge.source(), edge.target()));
    }

    /** The bridge's table, for an edge the source rule does not refuse. */
    private static Optional<String> tableRefusal(Edge edge, Scope scope) {
        Optional<PackQualifiedReference> source = reference(edge.source());
        Optional<PackQualifiedReference> target = reference(edge.target());
        if (source.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        Optional<Urn> sourceUrn = source.get().asUrn();
        Optional<Urn> targetUrn = target.get().asUrn();
        if (sourceUrn.isEmpty()
                || targetUrn.isEmpty()
                || bareSelfLoop(source.get(), sourceUrn.get(), target.get(), targetUrn.get())) {
            return Optional.empty();
        }
        if (TENSION.equals(edge.relation())) {
            return EndpointRule.bindTension(sourceUrn.get(), targetUrn.get()).map(EndpointRule.Fault::message);
        }
        Optional<RelationType> relation = RelationType.fromToken(edge.relation());
        if (relation.isEmpty()
                || relation.get().derived()
                || scope.judgesEnhancementEdge(relation.get(), source.get(), sourceUrn.get())) {
            return Optional.empty();
        }
        EndpointRule.Binding binding;
        try {
            binding = EndpointRule.bind(
                    relation.get(),
                    sourceUrn.get(),
                    source.get().packName(),
                    targetUrn.get(),
                    target.get().packName(),
                    Optional.empty(),
                    Optional.empty());
        } catch (IllegalArgumentException malformed) {
            // A qualifier that breaks the pack-name grammar: the graph rules refuse the edge on their own.
            return Optional.empty();
        }
        return binding instanceof EndpointRule.Fault fault ? Optional.of(fault.message()) : Optional.empty();
    }

    /**
     * Judges a pack's edges.
     *
     * @param edges the edges of the pack's graph documents, in document order
     * @param scope the pack
     * @return one violation per refused edge, at its graph document, under the fault's code: first every
     *     {@value EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} in the order of the edges, then every {@value #CODE}
     */
    public static List<PackElementViolation> violations(List<Edge> edges, Scope scope) {
        List<PackElementViolation> sources = new ArrayList<>();
        List<PackElementViolation> table = new ArrayList<>();
        for (Edge edge : edges) {
            refusal(edge, scope)
                    .ifPresent(refused -> (CODE.equals(refused.code()) ? table : sources)
                            .add(new PackElementViolation(edge.file(), refused.code(), refused.reason())));
        }
        List<PackElementViolation> out = new ArrayList<>(sources);
        out.addAll(table);
        return List.copyOf(out);
    }

    /** Only a bare pair is a self-loop: a qualifier names another pack's artifact of the same id (ADR-0053 D7). */
    private static boolean bareSelfLoop(
            PackQualifiedReference source, Urn sourceUrn, PackQualifiedReference target, Urn targetUrn) {
        return sourceUrn.equals(targetUrn)
                && source.packName().isEmpty()
                && target.packName().isEmpty();
    }

    private static Optional<PackQualifiedReference> reference(String written) {
        try {
            return Optional.of(PackQualifiedReference.parse(written));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
