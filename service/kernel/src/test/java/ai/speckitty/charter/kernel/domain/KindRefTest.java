package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KindRefTest {

    @Test
    @DisplayName("every node kind has exactly one leaf kind type and one reference factory")
    void everyNodeKindHasOneFactory() {
        Set<NodeKind> made = EnumSet.noneOf(NodeKind.class);
        for (Method method : KindRef.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Class<?> leaf = (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
            NodeKind kind = KindTypes.nodeKindOf(leaf).orElseThrow();
            assertThat(made.add(kind)).as("a second factory for %s", kind).isTrue();
        }
        assertThat(made).containsExactlyInAnyOrder(NodeKind.values());
        Arrays.stream(NodeKind.values())
                .forEach(kind ->
                        assertThat(KindTypes.nodeKindOf(KindTypes.leafOf(kind))).contains(kind));
    }

    @Test
    @DisplayName("a typed factory builds the relationship its relation names, qualified as the reference is")
    void aTypedFactoryBuildsItsRelation() {
        Relationship relationship = Relationship.requires(
                KindRef.procedure("p"), KindRef.directive("DIRECTIVE_001").inPack("org-base"));

        assertThat(relationship.type()).isEqualTo(RelationType.REQUIRES);
        assertThat(relationship.from()).isEqualTo(Urn.parse("procedure:p").orElseThrow());
        assertThat(relationship.toReference().toString()).isEqualTo("org-base::directive:DIRECTIVE_001");
        assertThat(Relationship.overrides(KindRef.skill("a"), KindRef.skill("b"))
                        .type())
                .isEqualTo(RelationType.OVERRIDES);
        assertThat(Relationship.requiresStep(KindRef.missionType("m"), KindRef.action("m", "s"))
                        .annotated(java.util.Optional.of("w"), java.util.Optional.empty())
                        .when())
                .contains("w");
    }

    @Test
    @DisplayName("a pack qualifier follows the pack-name grammar")
    void aBadPackNameIsRefused() {
        assertThatThrownBy(() -> KindRef.directive("d").inPack("Not A Pack"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
