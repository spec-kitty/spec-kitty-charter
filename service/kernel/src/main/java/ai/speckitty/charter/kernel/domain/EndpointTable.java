package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The endpoint table of ADR-0064 decision 13, written once: which source and target kinds each relation may connect.
 * {@link RelationType#endpoints()} answers from it, and {@code EndpointTableAgreementTest} checks the typed factories
 * of {@link Relationship} against it.
 */
final class EndpointTable {

    /** The six guidance kinds. */
    static final Set<NodeKind> GUIDANCE = Collections.unmodifiableSet(EnumSet.of(
            NodeKind.DIRECTIVE,
            NodeKind.TACTIC,
            NodeKind.STYLEGUIDE,
            NodeKind.TOOLGUIDE,
            NodeKind.PARADIGM,
            NodeKind.PROCEDURE));

    private static final Map<RelationType, Set<EndpointPair>> TABLE = table();

    private static final Set<EndpointPair> TENSION = tensionPairs();

    private EndpointTable() {}

    static Set<EndpointPair> of(RelationType type) {
        return TABLE.getOrDefault(type, Set.of());
    }

    private static Map<RelationType, Set<EndpointPair>> table() {
        Map<RelationType, Set<EndpointPair>> table = new EnumMap<>(RelationType.class);
        for (RelationType type : RelationType.values()) {
            table.put(type, rows(type));
        }
        return Collections.unmodifiableMap(table);
    }

    private static Set<EndpointPair> rows(RelationType type) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        switch (type) {
            case REQUIRES -> {
                product(pairs, advisors(), union(GUIDANCE, EnumSet.of(NodeKind.ASSET, NodeKind.TEMPLATE)));
                pairs.add(new EndpointPair(NodeKind.ASSET, NodeKind.ASSET));
                pairs.add(new EndpointPair(NodeKind.MISSION_TYPE, NodeKind.ACTION));
            }
            case SUGGESTS ->
                product(
                        pairs,
                        advisors(),
                        union(GUIDANCE, EnumSet.of(NodeKind.ASSET, NodeKind.TEMPLATE, NodeKind.AGENT_PROFILE)));
            case APPLIES ->
                product(pairs, EnumSet.of(NodeKind.ACTION), EnumSet.of(NodeKind.DIRECTIVE, NodeKind.TACTIC));
            case SCOPE ->
                product(
                        pairs,
                        EnumSet.of(NodeKind.MISSION_TYPE, NodeKind.ACTION),
                        union(GUIDANCE, EnumSet.of(NodeKind.AGENT_PROFILE)));
            case VOCABULARY -> product(pairs, graphAuthored(), EnumSet.of(NodeKind.GLOSSARY_CONTEXT, NodeKind.TERM));
            case INSTANTIATES -> pairs.add(new EndpointPair(NodeKind.ACTION, NodeKind.TEMPLATE));
            case REFINES, RECONCILES_TENSION -> product(pairs, GUIDANCE, GUIDANCE);
            case SPECIALIZES_FROM -> pairs.add(new EndpointPair(NodeKind.AGENT_PROFILE, NodeKind.AGENT_PROFILE));
            case ENHANCES -> {
                pairs.add(new EndpointPair(NodeKind.STEP_ENHANCEMENT, NodeKind.ACTION));
                diagonal(pairs, activatableExcept(NodeKind.MISSION_TYPE, NodeKind.STEP_ENHANCEMENT));
            }
            case OVERRIDES -> diagonal(pairs, activatableExcept(NodeKind.STEP_ENHANCEMENT));
            case REJECTS -> product(pairs, GUIDANCE, EnumSet.of(NodeKind.ANTI_PATTERN));
            case IMPLEMENTED_BY -> pairs.add(new EndpointPair(NodeKind.GUARD, NodeKind.ASSET));
            default -> throw new IllegalStateException("No endpoint row for " + type);
        }
        return Collections.unmodifiableSet(pairs);
    }

    /**
     * Whether a tension, which ADR-0028 holds beside the relations, may join two kinds: guidance on both sides.
     *
     * @param one one side's kind
     * @param other the other side's kind
     * @return whether both are guidance kinds
     */
    static boolean tensionAllows(NodeKind one, NodeKind other) {
        return TENSION.contains(new EndpointPair(one, other));
    }

    /**
     * The kinds a tension may join, as pairs in the order of {@link NodeKind}: guidance on both sides.
     *
     * @return the allowed pairs
     */
    static Set<EndpointPair> tension() {
        return TENSION;
    }

    private static Set<EndpointPair> tensionPairs() {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        product(pairs, GUIDANCE, GUIDANCE);
        return Collections.unmodifiableSet(pairs);
    }

    private static Set<NodeKind> advisors() {
        return union(GUIDANCE, EnumSet.of(NodeKind.AGENT_PROFILE, NodeKind.SKILL));
    }

    private static Set<NodeKind> graphAuthored() {
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        Arrays.stream(NodeKind.values()).filter(NodeKind::declarableInGraphs).forEach(kinds::add);
        return kinds;
    }

    private static Set<NodeKind> activatableExcept(NodeKind... excluded) {
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            if (kind.activatable()) {
                kinds.add(NodeKind.of(kind));
            }
        }
        kinds.removeAll(Arrays.asList(excluded));
        return kinds;
    }

    private static Set<NodeKind> union(Set<NodeKind> one, Set<NodeKind> other) {
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        kinds.addAll(one);
        kinds.addAll(other);
        return kinds;
    }

    private static void product(Set<EndpointPair> pairs, Set<NodeKind> sources, Set<NodeKind> targets) {
        for (NodeKind source : sources) {
            for (NodeKind target : targets) {
                pairs.add(new EndpointPair(source, target));
            }
        }
    }

    private static void diagonal(Set<EndpointPair> pairs, Set<NodeKind> kinds) {
        for (NodeKind kind : kinds) {
            pairs.add(new EndpointPair(kind, kind));
        }
    }
}
