package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NodeKindTest {

    /**
     * The table, one row per constant in declaration order, written out: the constant, its URN token, the token of the
     * artifact kind it stands for (null for none), its plural, whether a graph may declare it and whether an edge may
     * name it.
     */
    static List<Arguments> table() {
        return List.of(
                row(NodeKind.DIRECTIVE, "directive", "directive", "directives", true, true),
                row(NodeKind.TACTIC, "tactic", "tactic", "tactics", true, true),
                row(NodeKind.STYLEGUIDE, "styleguide", "styleguide", "styleguides", true, true),
                row(NodeKind.TOOLGUIDE, "toolguide", "toolguide", "toolguides", true, true),
                row(NodeKind.PARADIGM, "paradigm", "paradigm", "paradigms", true, true),
                row(NodeKind.PROCEDURE, "procedure", "procedure", "procedures", true, true),
                row(NodeKind.AGENT_PROFILE, "agent_profile", "agent_profile", "agent_profiles", true, true),
                row(
                        NodeKind.MISSION_STEP_CONTRACT,
                        "mission_step_contract",
                        "mission_step_contract",
                        "mission_step_contracts",
                        true,
                        true),
                row(NodeKind.GLOSSARY_PACK, "glossary_pack", "glossary_pack", "glossary_packs", true, true),
                row(NodeKind.SKILL, "skill", "skill", "skills", true, true),
                row(NodeKind.ANTI_PATTERN, "anti_pattern", "anti_pattern", "anti_patterns", true, true),
                row(NodeKind.ASSET, "asset", "asset", "assets", true, true),
                row(NodeKind.TEMPLATE, "template", "template", "templates", true, true),
                row(NodeKind.ACTION, "action", null, "actions", true, true),
                row(NodeKind.MISSION_TYPE, "mission_type", "mission_type", "mission_types", true, true),
                row(NodeKind.GUARD, "guard", "guard", "guards", false, false),
                row(NodeKind.LIFECYCLE, "lifecycle", "lifecycle", "lifecycles", false, false),
                row(
                        NodeKind.STEP_ENHANCEMENT,
                        "step_enhancement",
                        "step_enhancement",
                        "step_enhancements",
                        true,
                        true));
    }

    private static Arguments row(
            NodeKind kind,
            String token,
            @Nullable String artifactKind,
            String plural,
            boolean declarable,
            boolean edgeTarget) {
        return Arguments.of(kind, token, Optional.ofNullable(artifactKind), plural, declarable, edgeTarget);
    }

    @ParameterizedTest
    @MethodSource("table")
    void eachNodeKindStatesItsRow(
            NodeKind kind,
            String token,
            Optional<String> artifactKind,
            String plural,
            boolean declarable,
            boolean edgeTarget) {
        assertThat(kind.hasPlural()).as("has a plural").isTrue();

        assertThat(kind.token()).as("token").isEqualTo(token);
        assertThat(kind.artifactKind().map(ArtifactKind::token))
                .as("artifact kind")
                .isEqualTo(artifactKind);
        assertThat(kind.plural()).as("plural").isEqualTo(plural);
        assertThat(kind.declarableInGraphs()).as("declarable in graphs").isEqualTo(declarable);
        assertThat(kind.edgeTarget()).as("edge target").isEqualTo(edgeTarget);
    }

    @Test
    void theTableHasOneRowPerConstantInDeclarationOrder() {
        assertThat(table().stream().map(row -> row.get()[0]).toList()).containsExactly((Object[]) NodeKind.values());
    }

    @Test
    void everyArtifactKindHasTheNodeKindThatStandsForIt() {
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(NodeKind.of(kind).artifactKind()).as(kind.token()).contains(kind);
        }
    }

    @Test
    void theIndexOfTheTableIsTheIndexOfEveryArtifactKind() {
        // Positive control for the two refusals below: the declared table indexes cleanly, one node kind per kind.
        assertThat(NodeKind.byArtifactKind(List.of(NodeKind.values()), NodeKind::artifactKind))
                .hasSize(ArtifactKind.values().length)
                .containsEntry(ArtifactKind.DIRECTIVE, NodeKind.DIRECTIVE)
                .doesNotContainValue(NodeKind.ACTION);
    }

    @Test
    void twoNodeKindsStandingForOneArtifactKindAreRefused() {
        // ACTION made to stand for the directive kind as well: of(DIRECTIVE) would have two answers.
        assertThatThrownBy(() -> NodeKind.byArtifactKind(
                        List.of(NodeKind.values()),
                        kind -> kind == NodeKind.ACTION ? Optional.of(ArtifactKind.DIRECTIVE) : kind.artifactKind()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the node kinds directive and action both stand for the artifact kind directive");
    }

    @Test
    void anArtifactKindNoNodeKindStandsForIsRefused() {
        assertThatThrownBy(() -> NodeKind.byArtifactKind(
                        List.of(NodeKind.values()),
                        kind -> kind == NodeKind.SKILL ? Optional.empty() : kind.artifactKind()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no node kind stands for the artifact kind skill");
    }

    @Test
    void aTokenIsLookedUpExactly() {
        assertThat(NodeKind.fromToken("mission_type")).contains(NodeKind.MISSION_TYPE);
        assertThat(NodeKind.fromToken("action")).contains(NodeKind.ACTION);
        assertThat(NodeKind.fromToken("Directive")).isEmpty();
        assertThat(NodeKind.fromToken("directives")).isEmpty();
    }

    @Test
    void noTwoNodeKindsShareAToken() {
        assertThat(Arrays.stream(NodeKind.values()).map(NodeKind::token).collect(Collectors.toSet()))
                .hasSize(NodeKind.values().length);
    }

    @Test
    void everyArtifactPluralNamesItsNodeKind() {
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(NodeKind.fromPlural(kind.plural())).as(kind.plural()).contains(NodeKind.of(kind));
        }
    }

    @Test
    void missionTypesAndActionsNameTheirNodeKinds() {
        assertThat(NodeKind.fromPlural("mission_types")).contains(NodeKind.MISSION_TYPE);
        assertThat(NodeKind.fromPlural("actions")).contains(NodeKind.ACTION);
    }

    @Test
    void coresAliasesAndSingularsAreRefused() {
        assertThat(NodeKind.fromPlural("mission_steps")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("mission_type")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("anti_pattern")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("")).isEqualTo(Optional.empty());
    }

    @Test
    void theFifteenPluralsCoverEveryNodeKindExactlyOnce() {
        long named = java.util.Arrays.stream(NodeKind.values())
                .filter(k -> NodeKind.fromPlural(pluralOf(k)).isPresent())
                .count();
        assertThat(named).isEqualTo(NodeKind.values().length);
    }

    private static String pluralOf(NodeKind kind) {
        return switch (kind) {
            case ACTION -> "actions";
            default -> kind.artifactKind().orElseThrow().plural();
        };
    }
}
