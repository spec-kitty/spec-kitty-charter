package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * The one checked path from untyped endpoints to a {@link Relationship} (ADR-0064 decision 13). A reader or a writer
 * that holds only URNs binds through here; the endpoint table decides, and a disallowed edge comes back as a
 * {@link Fault} naming the relation and both kinds. Code that knows its kinds uses the typed factories of
 * {@link Relationship} instead.
 *
 * <p>The checks run in a fixed order, so an edge that breaks two rules reports the first: an {@code overrides} edge
 * whose source is not an activatable kind ({@value #OVERRIDES_SOURCE_NOT_ACTIVATABLE}, slice C's rule for #447), then
 * the table ({@value #ENDPOINT_NOT_ALLOWED}).
 */
public final class EndpointRule {

    /** The violation code of an {@code overrides} edge whose source kind an activation cannot select (#447). */
    public static final String OVERRIDES_SOURCE_NOT_ACTIVATABLE = "overrides_source_not_activatable";

    /** The violation code of an edge whose endpoint kinds its relation does not allow. */
    public static final String ENDPOINT_NOT_ALLOWED = "relationship_endpoint_not_allowed";

    private EndpointRule() {}

    /** The outcome of binding an edge. */
    public sealed interface Binding permits Bound, Fault {}

    /**
     * The edge is allowed.
     *
     * @param relationship the relationship it binds to
     */
    public record Bound(Relationship relationship) implements Binding {}

    /**
     * The edge is refused.
     *
     * @param code the violation code
     * @param relation the relation
     * @param source the source's kind
     * @param target the target's kind
     */
    public record Fault(String code, String relation, NodeKind source, NodeKind target) implements Binding {

        /**
         * A sentence naming the edge and why it is refused.
         *
         * @return the message
         */
        public String message() {
            if (OVERRIDES_SOURCE_NOT_ACTIVATABLE.equals(code)) {
                return "an overrides edge's source must be an activatable kind, and " + source.token() + " is not";
            }
            String article = "aeiou".indexOf(relation.charAt(0)) >= 0 ? "an " : "a ";
            return article + relation + " edge may not connect " + source.token() + " to " + target.token();
        }
    }

    /**
     * Binds an edge between two nodes, checking its kinds.
     *
     * @param type the relation
     * @param from the source node
     * @param fromPack the pack that holds the source, when qualified
     * @param to the target node
     * @param toPack the pack that holds the target, when qualified
     * @param when when the relationship applies
     * @param rationale why it holds
     * @return the bound relationship, or the fault
     */
    public static Binding bind(
            RelationType type,
            Urn from,
            Optional<String> fromPack,
            Urn to,
            Optional<String> toPack,
            Optional<String> when,
            Optional<String> rationale) {
        if (type == RelationType.OVERRIDES && !activatable(from.kind())) {
            return new Fault(OVERRIDES_SOURCE_NOT_ACTIVATABLE, type.token(), from.kind(), to.kind());
        }
        if (!type.allows(from.kind(), to.kind())) {
            return new Fault(ENDPOINT_NOT_ALLOWED, type.token(), from.kind(), to.kind());
        }
        return new Bound(new Relationship(from, fromPack, to, toPack, type, when, rationale));
    }

    /**
     * Checks the kinds of a tension, which ADR-0028 holds beside the relations: guidance on both sides.
     *
     * @param one one side
     * @param other the other side
     * @return the fault, when either side is not a guidance kind
     */
    public static Optional<Fault> bindTension(Urn one, Urn other) {
        if (EndpointTable.tensionAllows(one.kind(), other.kind())) {
            return Optional.empty();
        }
        return Optional.of(new Fault(ENDPOINT_NOT_ALLOWED, "in_tension_with", one.kind(), other.kind()));
    }

    private static boolean activatable(NodeKind kind) {
        return kind.artifactKind().map(ArtifactKind::activatable).orElse(false);
    }
}
