package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The endpoint table of ADR-0064 decision 13 applied to the edges of a pack's graph documents (FR-008, #350): an edge
 * whose relation does not allow its source and target kinds is refused with {@value EndpointRule#ENDPOINT_NOT_ALLOWED}.
 *
 * <p>The table is the kernel's ({@link EndpointRule}); this class only reads a document's edges as written. Two faults
 * belong to earlier rules and are not reported again here:
 *
 * <ul>
 *   <li>an {@code overrides} edge from a kind that is not activatable, {@link OverrideSourceRule}'s
 *       {@code overrides_source_not_activatable};
 *   <li>an {@code enhances} or {@code overrides} edge from a step enhancement the pack holds, written bare or qualified
 *       with the pack's own name, which the step-enhancement rules of ADR-0066 judge first under
 *       {@code prompt_fragment_invalid}. An edge from a step enhancement the pack does not hold, or from another pack's,
 *       is no fact of those rules, so the table judges it here.
 * </ul>
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

    /** The violation code of a refused edge: the one spelling, at registration, on the write and at load. */
    public static final String CODE = EndpointRule.ENDPOINT_NOT_ALLOWED;

    private static final String TENSION = "in_tension_with";

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
     * @return the reason it is refused, or empty when its relation allows its endpoints or another rule judges it
     */
    public static Optional<String> refusal(OverrideSourceRule.Edge edge, Scope scope) {
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
        return binding instanceof EndpointRule.Fault fault && CODE.equals(fault.code())
                ? Optional.of(fault.message())
                : Optional.empty();
    }

    /**
     * Judges a pack's edges.
     *
     * @param edges the edges of the pack's graph documents, in document order
     * @param scope the pack
     * @return one violation per refused edge, at its graph document, in the order of the edges
     */
    public static List<PackElementViolation> violations(List<OverrideSourceRule.Edge> edges, Scope scope) {
        List<PackElementViolation> out = new ArrayList<>();
        for (OverrideSourceRule.Edge edge : edges) {
            refusal(edge, scope).ifPresent(reason -> out.add(new PackElementViolation(edge.file(), CODE, reason)));
        }
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
