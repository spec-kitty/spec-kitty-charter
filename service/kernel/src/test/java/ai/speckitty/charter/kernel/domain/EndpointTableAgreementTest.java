package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.kinds.Advisor;
import ai.speckitty.charter.kernel.domain.kinds.AntiPattern;
import ai.speckitty.charter.kernel.domain.kinds.Kind;
import ai.speckitty.charter.kernel.domain.kinds.Overridable;
import ai.speckitty.charter.kernel.domain.kinds.Skill;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The typed factories of {@link Relationship} and the endpoint table of {@link RelationType} say the same thing
 * (ADR-0064 decision 13). The table is the one source. Each factory's generic bounds are expanded to the leaf kinds the
 * compiler accepts, and their union per relation must equal the relation's rows exactly. A factory whose two ends share
 * one type variable accepts only the diagonal. Both controls run the very comparison the main test runs.
 */
class EndpointTableAgreementTest {

    @Test
    @DisplayName("every relation's typed factories accept exactly the endpoint table's pairs")
    void theFactoriesEqualTheTable() {
        assertThat(disagreements(Relationship.class, RelationType::endpoints)).isEmpty();
    }

    @Test
    @DisplayName("a table with one pair too many disagrees with the factories (positive control)")
    void aSkewedTableIsCaught() {
        Function<RelationType, Set<EndpointPair>> skewed = type -> {
            Set<EndpointPair> rows = new HashSet<>(type.endpoints());
            if (type == RelationType.REJECTS) {
                rows.add(new EndpointPair(NodeKind.SKILL, NodeKind.ANTI_PATTERN));
            }
            return rows;
        };

        assertThat(disagreements(Relationship.class, skewed)).containsExactly("rejects");
    }

    @Test
    @DisplayName(
            "each too-wide factory, bounded by a group, by Kind or ?, or by an intersection, accepts a pair outside its row")
    void aTooWideFactoryIsCaught() {
        Map<RelationType, Set<EndpointPair>> accepted = new EnumMap<>(RelationType.class);
        acceptedByFactories(TooWide.class, accepted);

        assertThat(outside(accepted, RelationType.REJECTS))
                .contains(new EndpointPair(NodeKind.SKILL, NodeKind.ANTI_PATTERN));
        assertThat(accepted.get(RelationType.REQUIRES)).hasSize(NodeKind.values().length * NodeKind.values().length);
        assertThat(outside(accepted, RelationType.REQUIRES))
                .contains(new EndpointPair(NodeKind.GUARD, NodeKind.LIFECYCLE));
        assertThat(outside(accepted, RelationType.OVERRIDES))
                .contains(new EndpointPair(NodeKind.DIRECTIVE, NodeKind.SKILL))
                .doesNotContain(new EndpointPair(NodeKind.STEP_ENHANCEMENT, NodeKind.SKILL));
        assertThat(disagreements(TooWide.class, RelationType::endpoints)).contains("rejects", "requires", "overrides");
    }

    private static Set<EndpointPair> outside(Map<RelationType, Set<EndpointPair>> accepted, RelationType type) {
        Set<EndpointPair> extra = new HashSet<>(accepted.getOrDefault(type, Set.of()));
        extra.removeAll(type.endpoints());
        return extra;
    }

    @Test
    @DisplayName("a static method that takes a KindRef but is not a two-KindRef factory fails the scan")
    void anOddShapeIsRefused() {
        Map<RelationType, Set<EndpointPair>> accepted = new EnumMap<>(RelationType.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> acceptedByFactories(OddShape.class, accepted))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("requiresThree");
    }

    @Test
    @DisplayName("every relation that can be authored has a row, and no retired one does")
    void everyLiveRelationHasRows() {
        Set<RelationType> retired = EnumSet.of(RelationType.IMPACTS, RelationType.REPLACES, RelationType.DELEGATES_TO);
        for (RelationType type : RelationType.values()) {
            assertThat(type.endpoints().isEmpty()).as(type.token()).isEqualTo(retired.contains(type));
        }
    }

    /** The relations whose factories in {@code factories} disagree with {@code table}, by token. */
    private static List<String> disagreements(Class<?> factories, Function<RelationType, Set<EndpointPair>> table) {
        Map<RelationType, Set<EndpointPair>> accepted = new EnumMap<>(RelationType.class);
        acceptedByFactories(factories, accepted);
        List<String> out = new ArrayList<>();
        for (RelationType type : RelationType.values()) {
            boolean scanned = accepted.containsKey(type) || factories == Relationship.class;
            if (scanned && !accepted.getOrDefault(type, Set.of()).equals(table.apply(type))) {
                out.add(type.token());
            }
        }
        return out;
    }

    private static void acceptedByFactories(Class<?> factories, Map<RelationType, Set<EndpointPair>> accepted) {
        for (Method method : factories.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || Modifier.isPrivate(method.getModifiers())
                    || method.isSynthetic()) {
                continue;
            }
            Type[] parameters = method.getGenericParameterTypes();
            boolean takesKindRef = Arrays.stream(method.getParameterTypes()).anyMatch(KindRef.class::equals);
            if (!takesKindRef) {
                continue;
            }
            assertThat(method.getReturnType() == Relationship.class
                            && parameters.length == 2
                            && method.getParameterTypes()[0] == KindRef.class
                            && method.getParameterTypes()[1] == KindRef.class)
                    .as("%s takes a KindRef but is not a two-KindRef factory", method.getName())
                    .isTrue();
            RelationType built = relationOf(method.getName());
            Type from = argumentOf(parameters[0]);
            Type to = argumentOf(parameters[1]);
            Set<EndpointPair> pairs = accepted.computeIfAbsent(built, k -> new HashSet<>());
            if (from instanceof TypeVariable<?> variable && from.equals(to)) {
                for (NodeKind kind : leaves(variable)) {
                    pairs.add(new EndpointPair(kind, kind));
                }
            } else {
                for (NodeKind source : leaves(from)) {
                    for (NodeKind target : leaves(to)) {
                        pairs.add(new EndpointPair(source, target));
                    }
                }
            }
        }
    }

    /** The relation a factory builds: the longest relation token, in camel case, its name starts with. */
    private static RelationType relationOf(String factory) {
        RelationType best = null;
        for (RelationType type : RelationType.values()) {
            String camel = camel(type.token());
            if (factory.startsWith(camel)
                    && (best == null || camel.length() > camel(best.token()).length())) {
                best = type;
            }
        }
        assertThat(best).as("the relation of factory %s", factory).isNotNull();
        return best;
    }

    private static String camel(String token) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    private static Type argumentOf(Type kindRef) {
        assertThat(kindRef).as("a parameterized KindRef").isInstanceOf(ParameterizedType.class);
        return ((ParameterizedType) kindRef).getActualTypeArguments()[0];
    }

    /** Every node kind a type argument admits: an unbounded one admits every kind; an intersection, the common ones. */
    private static Set<NodeKind> leaves(Type type) {
        if (type instanceof WildcardType wildcard) {
            return intersection(wildcard.getUpperBounds());
        }
        if (type instanceof TypeVariable<?> variable) {
            return intersection(variable.getBounds());
        }
        if (type == Object.class) {
            return EnumSet.allOf(NodeKind.class);
        }
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        Set<Class<?>> seen = new HashSet<>();
        List<Class<?>> pending = new ArrayList<>(List.of((Class<?>) type));
        while (!pending.isEmpty()) {
            Class<?> next = pending.remove(pending.size() - 1);
            if (!seen.add(next)) {
                continue;
            }
            KindTypes.nodeKindOf(next).ifPresent(kinds::add);
            Class<?>[] permitted = next.getPermittedSubclasses();
            if (permitted != null) {
                pending.addAll(List.of(permitted));
            }
        }
        return kinds;
    }

    private static Set<NodeKind> intersection(Type[] bounds) {
        Set<NodeKind> kinds = EnumSet.allOf(NodeKind.class);
        for (Type bound : bounds) {
            kinds.retainAll(leaves(bound));
        }
        return kinds;
    }

    /** Factories wider than their rows, for the control: never called, only scanned. */
    @SuppressWarnings("unused")
    private static final class TooWide {

        static Relationship rejects(KindRef<? extends Advisor> from, KindRef<AntiPattern> to) {
            return Relationship.of(from.urn(), to.urn(), RelationType.REJECTS);
        }

        static Relationship requires(KindRef<?> from, KindRef<? extends Kind> to) {
            return Relationship.of(from.urn(), to.urn(), RelationType.REQUIRES);
        }

        static <K extends Kind & Overridable> Relationship overrides(KindRef<K> from, KindRef<Skill> to) {
            return Relationship.of(from.urn(), to.urn(), RelationType.OVERRIDES);
        }
    }

    /** A method of another shape, for the control. */
    @SuppressWarnings("unused")
    private static final class OddShape {

        static Relationship requiresThree(KindRef<Skill> from, KindRef<Skill> to, String why) {
            return Relationship.of(from.urn(), to.urn(), RelationType.REQUIRES);
        }
    }
}
