package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0063: a pack's relationships are authored only in its graph documents, so no artifact field mints one. The check
 * is structural, over every kind the pack store binds: restoring a minting override, or a reference field, on any kind
 * turns it red, and the kind list it walks is pinned to be the whole registry, so it cannot pass over an empty list.
 */
@DisplayName("No artifact field mints an edge (ADR-0063)")
class NoArtifactFieldMintsAnEdgeTest {

    /** The accessors a relationship-authoring field used to have on an entity. */
    private static final Set<String> RETIRED_ACCESSORS =
            Set.of("references", "directiveRefs", "relationships", "procedures");

    /** The domain packages the walk follows: each slice's {@code ai.speckitty.charter.<slice>.domain}. */
    private static final Pattern DOMAIN = Pattern.compile("ai\\.speckitty\\.charter\\.[a-z]+\\.domain");

    private static List<Class<?>> entityTypes() {
        return KindBindings.all().stream()
                .<Class<?>>map(KindBinding::entityType)
                .toList();
    }

    @Test
    @DisplayName("the check walks every kind the store binds, the guard among them")
    void theCheckWalksEveryBoundKind() {
        assertThat(entityTypes()).hasSameSizeAs(KindBindings.kinds());
        assertThat(KindBindings.kinds()).contains(ArtifactKind.DIRECTIVE, ArtifactKind.PARADIGM, ArtifactKind.GUARD);
    }

    @Test
    @DisplayName("only a guard derives a relationship, and it derives a derived relation (implemented_by)")
    void onlyAGuardDerivesARelationship() {
        List<String> deriving = entityTypes().stream()
                .filter(type -> declares(type, "derivedRelationships"))
                .map(Class::getSimpleName)
                .toList();

        assertThat(deriving).containsExactly("Guard");
        assertThat(RelationType.IMPLEMENTED_BY.derived()).isTrue();
    }

    @Test
    @DisplayName("the walk reaches the value types nested in an entity, a tactic's and a mission type's steps")
    void theWalkReachesNestedValueTypes() {
        assertThat(domainTypes()).extracting(Class::getSimpleName).contains("TacticStep", "MissionStep");
    }

    @Test
    @DisplayName("no entity, nor a value type nested in one, carries a reference or relationship accessor")
    void noEntityCarriesAReferenceAccessor() {
        List<String> found = domainTypes().stream()
                .flatMap(type -> Arrays.stream(type.getMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0)
                        .map(Method::getName)
                        .filter(RETIRED_ACCESSORS::contains)
                        .map(name -> type.getSimpleName() + "." + name))
                .toList();

        assertThat(found).isEmpty();
    }

    /** The entity types and every domain type reachable from their zero-argument accessors, list elements included. */
    private static Set<Class<?>> domainTypes() {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(entityTypes());
        while (!pending.isEmpty()) {
            Class<?> type = pending.pop();
            if (!seen.add(type)) {
                continue;
            }
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0) {
                    reached(method.getGenericReturnType()).forEach(pending::push);
                }
            }
        }
        return seen;
    }

    private static List<Class<?>> reached(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .flatMap(argument -> reached(argument).stream())
                    .toList();
        }
        if (type instanceof Class<?> raw && DOMAIN.matcher(raw.getPackageName()).matches() && !raw.isEnum()) {
            return List.of(raw);
        }
        return List.of();
    }

    private static boolean declares(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(name) && !m.isSynthetic() && !m.isBridge());
    }
}
