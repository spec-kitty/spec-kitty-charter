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
                row(NodeKind.GLOSSARY_CONTEXT, "glossary_context", "glossary", "glossaries", true, true),
                row(NodeKind.TERM, "term", null, null, false, true),
                row(NodeKind.SKILL, "skill", "skill", "skills", true, true),
                row(NodeKind.ANTI_PATTERN, "anti_pattern", "anti_pattern", "anti_patterns", true, true),
                row(NodeKind.ASSET, "asset", "asset", "assets", true, true),
                row(NodeKind.TEMPLATE, "template", "template", "templates", true, true),
                row(NodeKind.ACTION, "action", null, "actions", true, true),
                row(NodeKind.MISSION_TYPE, "mission_type", "mission_type", "mission_types", true, true),
                row(NodeKind.GUARD, "guard", "guard", "guards", false, false),
                row(NodeKind.LIFECYCLE, "lifecycle", "lifecycle", "lifecycles", false, false),
                row(NodeKind.STEP_ENHANCEMENT, "step_enhancement", "step_enhancement", "step_enhancements", true, true),
                row(NodeKind.ROUTING_CATALOG, "routing_catalog", "routing_catalog", "routing_catalogs", false, false));
    }

    private static Arguments row(
            NodeKind kind,
            String token,
            @Nullable String artifactKind,
            @Nullable String plural,
            boolean declarable,
            boolean edgeTarget) {
        return Arguments.of(
                kind, token, Optional.ofNullable(artifactKind), Optional.ofNullable(plural), declarable, edgeTarget);
    }

    @ParameterizedTest
    @MethodSource("table")
    void eachNodeKindStatesItsRow(
            NodeKind kind,
            String token,
            Optional<String> artifactKind,
            Optional<String> plural,
            boolean declarable,
            boolean edgeTarget) {
        assertThat(kind.hasPlural()).as("has a plural").isEqualTo(plural.isPresent());

        assertThat(kind.token()).as("token").isEqualTo(token);
        assertThat(kind.artifactKind().map(ArtifactKind::token))
                .as("artifact kind")
                .isEqualTo(artifactKind);
        plural.ifPresentOrElse(
                expected -> assertThat(kind.plural()).as("plural").isEqualTo(expected),
                () -> assertThatThrownBy(kind::plural)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("the node kind " + token + " has no fragment plural"));
        assertThat(kind.declarableInGraphs()).as("declarable in graphs").isEqualTo(declarable);
        assertThat(kind.edgeTarget()).as("edge target").isEqualTo(edgeTarget);
    }

    @Test
    void aKindIsNamedByTheContractWhenAGraphDeclaresItAnEdgeNamesItOrItIsActivatable() {
        // ADR-0080: the routing catalog is no graph kind, yet a read names it; a guard and a lifecycle are not (#156).
        assertThat(java.util.Arrays.stream(NodeKind.values()).filter(kind -> !kind.named()))
                .containsExactlyInAnyOrder(NodeKind.GUARD, NodeKind.LIFECYCLE);
        assertThat(NodeKind.ROUTING_CATALOG.named()).isTrue();
        assertThat(NodeKind.ROUTING_CATALOG.declarableInGraphs()).isFalse();
        assertThat(NodeKind.ROUTING_CATALOG.edgeTarget()).isFalse();
        assertThat(NodeKind.TERM.named()).as("an edge target only").isTrue();
    }

    @Test
    void aTermIsAnEdgeEndpointOnlyAsTheTargetOfAVocabularyEdge() {
        assertThat(NodeKind.TERM.edgeSource()).as("term as a source").isFalse();
        assertThat(NodeKind.TERM.edgeTargetOf(Optional.of(RelationType.VOCABULARY)))
                .as("term as a vocabulary target")
                .isTrue();
        for (RelationType relation : RelationType.values()) {
            if (relation != RelationType.VOCABULARY) {
                assertThat(NodeKind.TERM.edgeTargetOf(Optional.of(relation)))
                        .as("term as a %s target", relation.token())
                        .isFalse();
            }
        }
        assertThat(NodeKind.TERM.edgeTargetOf(Optional.empty()))
                .as("term in a tension")
                .isFalse();
    }

    @Test
    void everyOtherEndpointKindIsASourceAndATargetOfEveryRelationAndANonEndpointIsNeither() {
        for (NodeKind kind : NodeKind.values()) {
            if (kind == NodeKind.TERM) {
                continue;
            }
            assertThat(kind.edgeSource()).as("%s as a source", kind.token()).isEqualTo(kind.edgeTarget());
            assertThat(kind.edgeTargetOf(Optional.empty()))
                    .as("%s in a tension", kind.token())
                    .isEqualTo(kind.edgeTarget());
            for (RelationType relation : RelationType.values()) {
                assertThat(kind.edgeTargetOf(Optional.of(relation)))
                        .as("%s as a %s target", kind.token(), relation.token())
                        .isEqualTo(kind.edgeTarget());
            }
        }
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
    void thePluralsCoverEveryNodeKindButTheTermExactlyOnce() {
        List<NodeKind> named = java.util.Arrays.stream(NodeKind.values())
                .filter(k -> pluralOf(k)
                        .flatMap(NodeKind::fromPlural)
                        .filter(k::equals)
                        .isPresent())
                .toList();
        assertThat(named)
                .containsExactly(java.util.Arrays.stream(NodeKind.values())
                        .filter(k -> k != NodeKind.TERM)
                        .toArray(NodeKind[]::new));
        assertThat(NodeKind.fromPlural("terms")).isEmpty();
    }

    @Test
    void theGlossaryContextsUrnTokenIsNotItsArtifactKindsToken() {
        // ADR-0053 decision 13: the kind token is glossary, the URN token glossary_context; each looks up only its own.
        assertThat(NodeKind.of(ArtifactKind.GLOSSARY)).isEqualTo(NodeKind.GLOSSARY_CONTEXT);
        assertThat(NodeKind.fromToken("glossary_context")).contains(NodeKind.GLOSSARY_CONTEXT);
        assertThat(NodeKind.fromToken("glossary")).isEmpty();
        assertThat(ArtifactKind.fromToken("glossary_context")).isEmpty();
        assertThat(NodeKind.fromPlural("glossaries")).contains(NodeKind.GLOSSARY_CONTEXT);
    }

    /** The plural a fragment would name the kind by, as the artifact kind or the action states it; none for a term. */
    private static Optional<String> pluralOf(NodeKind kind) {
        return kind == NodeKind.ACTION
                ? Optional.of("actions")
                : kind.artifactKind().map(ArtifactKind::plural);
    }
}
