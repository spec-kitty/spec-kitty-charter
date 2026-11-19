package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * The one checked path from untyped endpoints to a {@link Relationship} (ADR-0064 decision 13). A reader or a writer
 * that holds only URNs binds through here; the endpoint table decides, and a disallowed edge comes back as a
 * {@link Fault} naming the relation and both kinds. Code that knows its kinds uses the typed factories of
 * {@link Relationship} instead.
 *
 * <p>The checks run in a fixed order, so an edge that breaks two rules reports the first: an {@code overrides} edge
 * whose source is not an activatable kind ({@value #OVERRIDES_SOURCE_NOT_ACTIVATABLE}, slice C's rule for #447, folded
 * in here by operator ruling DM-01M7B1Z03VF9A36FBV65431HR3), then the table ({@value #ENDPOINT_NOT_ALLOWED}). The source
 * rule is the one predicate and the one code of #447: a reader of edges as written applies it on the source alone,
 * before it reads the target, through {@link #overridesSourceRefusal}.
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

        /**
         * A sentence naming the edge as its writer wrote it: for {@value #OVERRIDES_SOURCE_NOT_ACTIVATABLE} the reason
         * {@link #overridesSourceRefusal} gives, otherwise {@link #message()}.
         *
         * @param writtenSource the source as written, bare or with a pack name
         * @param writtenTarget the target as written
         * @return the reason
         */
        public String reason(String writtenSource, String writtenTarget) {
            return OVERRIDES_SOURCE_NOT_ACTIVATABLE.equals(code)
                    ? overridesSourceReason(source, writtenSource, writtenTarget)
                    : message();
        }
    }

    /**
     * The source rule of {@link #bind} on its own (#447): an {@code overrides} edge whose source is an artifact kind an
     * activation cannot select (a template, an asset, a guard or a lifecycle) is refused, whatever its target. A reader
     * of edges as written applies it before it reads the target, so the fault is reported even when the target is not
     * a reference.
     *
     * @param type the relation
     * @param from the source node
     * @param writtenSource the source as written, bare or with a pack name
     * @param writtenTarget the target as written
     * @return the reason the edge is refused under {@value #OVERRIDES_SOURCE_NOT_ACTIVATABLE}, or empty
     */
    public static Optional<String> overridesSourceRefusal(
            RelationType type, Urn from, String writtenSource, String writtenTarget) {
        if (type != RelationType.OVERRIDES || !inactivatableArtifact(from.kind())) {
            return Optional.empty();
        }
        return Optional.of(overridesSourceReason(from.kind(), writtenSource, writtenTarget));
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
        if (type == RelationType.OVERRIDES && inactivatableArtifact(from.kind())) {
            return new Fault(OVERRIDES_SOURCE_NOT_ACTIVATABLE, type.token(), from.kind(), to.kind());
        }
        if (!type.allows(from.kind(), to.kind())) {
            return new Fault(ENDPOINT_NOT_ALLOWED, type.token(), from.kind(), to.kind());
        }
        return new Bound(new Relationship(from, fromPack, to, toPack, type, when, rationale));
    }

    /** An artifact kind without activation state; a node-only kind (a step) is the table's to judge. */
    private static boolean inactivatableArtifact(NodeKind kind) {
        return kind.artifactKind().map(artifact -> !artifact.activatable()).orElse(false);
    }

    private static String overridesSourceReason(NodeKind kind, String writtenSource, String writtenTarget) {
        String token = kind.artifactKind().map(ArtifactKind::token).orElse(kind.token());
        String article = "aeiou".indexOf(token.charAt(0)) >= 0 ? "an " : "a ";
        return "the overrides edge from " + writtenSource + " to " + writtenTarget + " is refused: " + article + token
                + " is not an activatable kind, so it cannot stand in for the artifact it overrides";
    }
}
