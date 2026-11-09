package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ArtifactKindTest {

    @Test
    void factsMatchTheLogicalModelTable() {
        assertThat(Arrays.stream(ArtifactKind.values())
                        .filter(ArtifactKind::activatable)
                        .map(ArtifactKind::token))
                .containsExactly(
                        "directive",
                        "tactic",
                        "styleguide",
                        "toolguide",
                        "paradigm",
                        "procedure",
                        "agent_profile",
                        "mission_step_contract",
                        "glossary_pack",
                        "skill",
                        "anti_pattern",
                        "mission_type",
                        "step_enhancement");
    }

    @Test
    void theMissionTypeIsAnActivatableKindLikeAnyOther() {
        assertThat(ArtifactKind.MISSION_TYPE.activatable()).isTrue();
        assertThat(ArtifactKind.MISSION_TYPE.plural()).isEqualTo("mission_types");
    }

    @Test
    void theStepEnhancementIsAnActivatableKindWithItsGraphPlural() {
        assertThat(ArtifactKind.STEP_ENHANCEMENT.activatable()).isTrue();
        assertThat(ArtifactKind.STEP_ENHANCEMENT.plural()).isEqualTo("step_enhancements");
    }

    @Test
    void theRegistryHoldsOnlyTheFactsItsConsumersRead() {
        Set<String> facts = Arrays.stream(ArtifactKind.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(Method::getName)
                .filter(name -> !Set.of("values", "valueOf").contains(name))
                .collect(Collectors.toSet());

        assertThat(facts).containsExactlyInAnyOrder("token", "plural", "activatable", "fromToken", "fromPlural");
        assertThat(ArtifactKind.class.getDeclaredClasses()).isEmpty();
    }

    @Test
    void everyArtifactKindIsANodeKind() {
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(NodeKind.of(kind).artifactKind()).contains(kind);
        }
        assertThat(NodeKind.ACTION.artifactKind()).isEmpty();
        assertThat(NodeKind.MISSION_TYPE.artifactKind()).contains(ArtifactKind.MISSION_TYPE);
    }

    @Test
    void pluralsResolve() {
        assertThat(ArtifactKind.fromPlural("agent_profiles")).contains(ArtifactKind.AGENT_PROFILE);
        assertThat(ArtifactKind.fromPlural("mission_types")).contains(ArtifactKind.MISSION_TYPE);
        assertThat(ArtifactKind.fromPlural("mission_steps")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ArtifactKind.class)
    void everyTokenReadsBackAsItsKind(ArtifactKind kind) {
        assertThat(ArtifactKind.fromToken(kind.token())).contains(kind);
        assertThat(ArtifactKind.fromPlural(kind.plural())).contains(kind);
    }

    @ParameterizedTest
    @EnumSource(ArtifactKind.class)
    void aTokenThatDiffersOnlyInCaseIsRefused(ArtifactKind kind) {
        assertThat(ArtifactKind.fromToken(kind.token().toUpperCase(Locale.ROOT)))
                .isEmpty();
        assertThat(ArtifactKind.fromToken(kind.name())).isEmpty();
        assertThat(ArtifactKind.fromPlural(kind.plural().toUpperCase(Locale.ROOT)))
                .isEmpty();
    }

    @Test
    void anUnknownOrPluralTokenIsRefused() {
        assertThat(ArtifactKind.fromToken("action")).isEmpty();
        assertThat(ArtifactKind.fromToken("directives")).isEmpty();
        assertThat(ArtifactKind.fromToken(" directive")).isEmpty();
        assertThat(ArtifactKind.fromToken("")).isEmpty();
        assertThat(ArtifactKind.fromToken("Mission_Type")).isEmpty();
    }
}
