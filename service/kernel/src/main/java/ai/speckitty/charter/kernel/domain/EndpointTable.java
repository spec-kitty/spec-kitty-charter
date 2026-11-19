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

    /** One relation's row; a switch expression over every relation, so a relation added without its row fails to compile. */
    private static Set<EndpointPair> rows(RelationType type) {
        Set<EndpointPair> pairs = switch (type) {
            case REQUIRES ->
                rectangles(
                        product(advisors(), union(GUIDANCE, EnumSet.of(NodeKind.ASSET, NodeKind.TEMPLATE))),
                        pair(NodeKind.ASSET, NodeKind.ASSET),
                        pair(NodeKind.MISSION_TYPE, NodeKind.ACTION));
            case SUGGESTS ->
                product(
                        advisors(),
                        union(GUIDANCE, EnumSet.of(NodeKind.ASSET, NodeKind.TEMPLATE, NodeKind.AGENT_PROFILE)));
            case APPLIES -> product(EnumSet.of(NodeKind.ACTION), EnumSet.of(NodeKind.DIRECTIVE, NodeKind.TACTIC));
            case SCOPE ->
                product(
                        EnumSet.of(NodeKind.MISSION_TYPE, NodeKind.ACTION),
                        union(GUIDANCE, EnumSet.of(NodeKind.AGENT_PROFILE)));
            case VOCABULARY -> product(graphDeclarable(), EnumSet.of(NodeKind.GLOSSARY_CONTEXT, NodeKind.TERM));
            case INSTANTIATES -> pair(NodeKind.ACTION, NodeKind.TEMPLATE);
            case REFINES, RECONCILES_TENSION, TENSION -> product(GUIDANCE, GUIDANCE);
            case SPECIALIZES_FROM -> pair(NodeKind.AGENT_PROFILE, NodeKind.AGENT_PROFILE);
            case ENHANCES ->
                rectangles(
                        pair(NodeKind.STEP_ENHANCEMENT, NodeKind.ACTION),
                        diagonal(activatableExcept(NodeKind.MISSION_TYPE, NodeKind.STEP_ENHANCEMENT)));
            case OVERRIDES -> diagonal(activatableExcept(NodeKind.STEP_ENHANCEMENT));
            case REJECTS -> product(GUIDANCE, EnumSet.of(NodeKind.ANTI_PATTERN));
            case IMPLEMENTED_BY -> pair(NodeKind.GUARD, NodeKind.ASSET);
        };
        return Collections.unmodifiableSet(pairs);
    }

    private static Set<NodeKind> advisors() {
        return union(GUIDANCE, EnumSet.of(NodeKind.AGENT_PROFILE, NodeKind.SKILL));
    }

    /** The kinds a vocabulary edge may start at: every kind a graph document may declare as a node. */
    private static Set<NodeKind> graphDeclarable() {
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

    private static Set<EndpointPair> product(Set<NodeKind> sources, Set<NodeKind> targets) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        for (NodeKind source : sources) {
            for (NodeKind target : targets) {
                pairs.add(new EndpointPair(source, target));
            }
        }
        return pairs;
    }

    private static Set<EndpointPair> diagonal(Set<NodeKind> kinds) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        for (NodeKind kind : kinds) {
            pairs.add(new EndpointPair(kind, kind));
        }
        return pairs;
    }

    private static Set<EndpointPair> pair(NodeKind source, NodeKind target) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        pairs.add(new EndpointPair(source, target));
        return pairs;
    }

    @SafeVarargs
    private static Set<EndpointPair> rectangles(Set<EndpointPair>... parts) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        for (Set<EndpointPair> part : parts) {
            pairs.addAll(part);
        }
        return pairs;
    }
}
