package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The rule of FR-014 (operator ruling of 2026-11-12 on #447, option 1): an {@code overrides} edge whose source is not of
 * an {@linkplain ArtifactKind#activatable() activatable} kind is refused. An {@code overrides} edge says that one
 * governance artifact stands in for another (rule C27). A template, an asset, a guard or a lifecycle has no activation
 * state and cannot stand in, and the activations side drops such a node from what it reports (D16). Refusing the edge
 * where it enters means that the read and the deactivation write (IC-13) can never disagree about what satisfies a
 * required artifact.
 *
 * <p>The rule judges the source kind only. An {@code overrides} edge from an activatable kind, whatever its target, and
 * an edge of another relation from any kind are not its concern. A source written with a pack name,
 * {@code <pack>::template:t}, is judged like a bare one. An endpoint that is no URN is left to the graph rules.
 *
 * <p>The load records each fault as a violation of the pack at its graph document, so a registration lists one
 * violation per edge inside {@code 422 validation_failed} and a composing read answers {@code 500 pack_unreadable}
 * naming the document. Validate mode reports the same fault as a blocking finding at the document. The relationship
 * write refuses the edge before anything is written. It is pure.
 */
public final class OverrideSourceRule {

    /**
     * The violation code of a refused edge, at registration, on the relationship write and at load: the one spelling,
     * which {@link PackPolicy#violationKind} answers for {@link PackFindingCode#OVERRIDES_SOURCE_NOT_ACTIVATABLE}.
     */
    public static final String CODE = "overrides_source_not_activatable";

    private OverrideSourceRule() {}

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
     * One refused edge.
     *
     * @param file the pack-relative path of the graph document that holds the edge
     * @param reason what is wrong, naming the edge's source and target
     */
    public record Fault(String file, String reason) {
        /**
         * Checks that every field is present.
         *
         * @param file the graph document
         * @param reason the reason
         */
        public Fault {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Judges a pack's edges.
     *
     * @param edges the edges of the pack's graph documents, in document order
     * @return one fault per {@code overrides} edge whose source kind is not activatable, in the order of the edges
     */
    public static List<Fault> judge(List<Edge> edges) {
        List<Fault> faults = new ArrayList<>();
        for (Edge edge : edges) {
            if (!RelationType.OVERRIDES.token().equals(edge.relation())) {
                continue;
            }
            sourceUrn(edge.source())
                    .flatMap(OverrideSourceRule::inactivatableKind)
                    .map(kind -> reason(kind, edge.source(), edge.target()))
                    .ifPresent(reason -> faults.add(new Fault(edge.file(), reason)));
        }
        return List.copyOf(faults);
    }

    /**
     * Judges one relationship a write would register.
     *
     * @param relationship the relationship
     * @return the reason it is refused, or empty when it is no {@code overrides} edge or its source kind is activatable
     */
    public static Optional<String> refusal(Relationship relationship) {
        if (relationship.type() != RelationType.OVERRIDES) {
            return Optional.empty();
        }
        return inactivatableKind(relationship.from())
                .map(kind -> reason(
                        kind,
                        relationship.from().toString(),
                        relationship.toReference().toString()));
    }

    /**
     * The load violation of one fault: a violation of the pack at the graph document, of the kind {@link #CODE}. The
     * registration copies the kind into the {@code code} of its violation.
     *
     * @param fault the fault
     * @return the violation
     */
    public static PackElementViolation violationOf(Fault fault) {
        return new PackElementViolation(fault.file(), CODE, fault.reason());
    }

    private static Optional<Urn> sourceUrn(String source) {
        try {
            return PackQualifiedReference.parse(source).asUrn();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** The source's artifact kind when it is not activatable; empty for an activatable kind and for a step. */
    private static Optional<ArtifactKind> inactivatableKind(Urn source) {
        return source.kind().artifactKind().filter(kind -> !kind.activatable());
    }

    private static String reason(ArtifactKind kind, String source, String target) {
        String article = "aeiou".indexOf(kind.token().charAt(0)) >= 0 ? "an " : "a ";
        return "the overrides edge from " + source + " to " + target + " is refused: " + article + kind.token()
                + " is not an activatable kind, so it cannot stand in for the artifact it overrides";
    }
}
