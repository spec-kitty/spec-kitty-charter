package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.EndpointRule.Bound;
import ai.speckitty.charter.kernel.domain.EndpointRule.Fault;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class EndpointRuleTest {

    private static EndpointRule.Binding bind(RelationType type, String from, String to) {
        return EndpointRule.bind(
                type,
                Urn.parse(from).orElseThrow(),
                Optional.empty(),
                Urn.parse(to).orElseThrow(),
                Optional.empty(),
                Optional.of("when"),
                Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(RelationType.class)
    @DisplayName("every pair of a relation's rows binds, keeping the edge's fields")
    void everyRowBinds(RelationType type) {
        for (EndpointPair pair : type.endpoints()) {
            Urn from = new Urn(pair.source(), "a");
            Urn to = new Urn(pair.target(), "b");
            EndpointRule.Binding binding = EndpointRule.bind(
                    type, from, Optional.empty(), to, Optional.of("other"), Optional.of("w"), Optional.empty());
            assertThat(binding).as("%s %s", type.token(), pair).isInstanceOf(Bound.class);
            Relationship bound = ((Bound) binding).relationship();
            assertThat(bound.type()).isEqualTo(type);
            assertThat(bound.from()).isEqualTo(from);
            assertThat(bound.toPack()).contains("other");
            assertThat(bound.when()).contains("w");
        }
    }

    @ParameterizedTest
    @EnumSource(RelationType.class)
    @DisplayName("every relation refuses a pair next to its rows")
    void everyRelationRefusesANeighbour(RelationType type) {
        EndpointPair neighbour = neighbourOf(type);
        EndpointRule.Binding binding = EndpointRule.bind(
                type,
                new Urn(neighbour.source(), "a"),
                Optional.empty(),
                new Urn(neighbour.target(), "b"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertThat(binding).as("%s %s", type.token(), neighbour).isInstanceOf(Fault.class);
        assertThat(((Fault) binding).code()).isEqualTo(EndpointRule.ENDPOINT_NOT_ALLOWED);
    }

    @ParameterizedTest
    @CsvSource({
        "REJECTS, SKILL, ANTI_PATTERN",
        "APPLIES, ACTION, STYLEGUIDE",
        "ENHANCES, MISSION_TYPE, MISSION_TYPE",
        "OVERRIDES, STEP_ENHANCEMENT, STEP_ENHANCEMENT",
        "REQUIRES, ACTION, DIRECTIVE",
        "SCOPE, DIRECTIVE, DIRECTIVE",
        "INSTANTIATES, DIRECTIVE, TEMPLATE",
        "SPECIALIZES_FROM, DIRECTIVE, DIRECTIVE",
        "VOCABULARY, GUARD, GLOSSARY_CONTEXT",
        "VOCABULARY, TERM, GLOSSARY_CONTEXT",
        "REFINES, SKILL, DIRECTIVE",
        "SUGGESTS, ASSET, DIRECTIVE",
        "RECONCILES_TENSION, AGENT_PROFILE, DIRECTIVE"
    })
    @DisplayName("a relation refuses the source-side and diagonal neighbours of its rows")
    void sourceSideAndDiagonalNeighboursAreRefused(RelationType type, NodeKind source, NodeKind target) {
        assertThat(type.endpoints()).doesNotContain(new EndpointPair(source, target));
        EndpointRule.Binding binding = EndpointRule.bind(
                type,
                new Urn(source, "a"),
                Optional.empty(),
                new Urn(target, "b"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertThat(binding).isInstanceOf(Fault.class);
        assertThat(((Fault) binding).code()).isEqualTo(EndpointRule.ENDPOINT_NOT_ALLOWED);
    }

    /** A pair outside the relation's rows that shares its source with a row: the nearest wrong edge. */
    private static EndpointPair neighbourOf(RelationType type) {
        for (EndpointPair row : type.endpoints()) {
            for (NodeKind target : NodeKind.values()) {
                EndpointPair candidate = new EndpointPair(row.source(), target);
                if (!type.endpoints().contains(candidate)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("no neighbour for " + type);
    }

    @Test
    @DisplayName("a procedure may not require a step's action (#350)")
    void aProcedureMayNotRequireAnAction() {
        assertThat(bind(RelationType.REQUIRES, "procedure:p", "action:software-dev/implement"))
                .isEqualTo(
                        new Fault(EndpointRule.ENDPOINT_NOT_ALLOWED, "requires", NodeKind.PROCEDURE, NodeKind.ACTION));
    }

    @Test
    @DisplayName("an overrides edge between two kinds is refused by the table")
    void overridesAcrossKindsIsRefused() {
        Fault fault = (Fault) bind(RelationType.OVERRIDES, "directive:d", "tactic:t");

        assertThat(fault.code()).isEqualTo(EndpointRule.ENDPOINT_NOT_ALLOWED);
        assertThat(fault.message()).isEqualTo("an overrides edge may not connect directive to tactic");
    }

    @Test
    @DisplayName("an overrides edge from a non-activatable kind reports slice C's code first, whatever its target")
    void overridesFromATemplateReportsTheSourceRuleFirst() {
        Fault fault = (Fault) bind(RelationType.OVERRIDES, "template:t", "directive:d");

        assertThat(fault.code()).isEqualTo(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE);
        assertThat(fault.message()).contains("template is not");
    }

    @Test
    @DisplayName(
            "an overrides edge from a step is the table's fault, not the source rule's: a step has no artifact kind")
    void overridesFromAStepIsTheTablesFault() {
        Fault fault = (Fault) bind(RelationType.OVERRIDES, "action:software-dev/plan", "action:software-dev/build");

        assertThat(fault.code()).isEqualTo(EndpointRule.ENDPOINT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("a tension binds between two guidance kinds only, and names its token in the fault")
    void aTensionJoinsGuidanceOnly() {
        assertThat(bind(RelationType.TENSION, "directive:a", "procedure:b")).isInstanceOf(EndpointRule.Bound.class);
        assertThat(bind(RelationType.TENSION, "directive:a", "skill:b"))
                .isEqualTo(new Fault(EndpointRule.ENDPOINT_NOT_ALLOWED, "tension", NodeKind.DIRECTIVE, NodeKind.SKILL));
    }

    @Test
    @DisplayName("a tension's endpoints are every pair of guidance kinds; it is bidirectional and authored")
    void aTensionsEndpointsAreTheGuidancePairs() {
        java.util.Set<EndpointPair> pairs = RelationType.TENSION.endpoints();

        assertThat(pairs).hasSize(36);
        for (NodeKind one : NodeKind.values()) {
            for (NodeKind other : NodeKind.values()) {
                boolean guidance = EndpointTable.GUIDANCE.contains(one) && EndpointTable.GUIDANCE.contains(other);
                assertThat(pairs.contains(new EndpointPair(one, other)))
                        .as("%s to %s", one, other)
                        .isEqualTo(guidance);
            }
        }
        assertThat(RelationType.TENSION.directionality()).isEqualTo(Directionality.BIDIRECTIONAL);
        assertThat(RelationType.TENSION.derived()).isFalse();
    }

    // ---- the source rule of #447, folded into the bridge (operator ruling DM-01M7B1Z03VF9A36FBV65431HR3) ----------
    // These cases moved here from the packs module's OverrideSourceRuleTest when the rule was folded in.

    private static final String TARGET = "base::directive:D";

    private static Urn sourceOf(ArtifactKind kind) {
        return new Urn(NodeKind.of(kind), "t");
    }

    @ParameterizedTest
    @EnumSource(
            value = ArtifactKind.class,
            names = {"TEMPLATE", "ASSET", "GUARD", "LIFECYCLE"})
    @DisplayName("an overrides edge from each kind that is not activatable is refused, naming the edge as written")
    void anOverridesEdgeFromEachKindThatIsNotActivatableIsRefused(ArtifactKind kind) {
        Urn from = sourceOf(kind);

        assertThat(EndpointRule.overridesSourceRefusal(RelationType.OVERRIDES, from, from.toString(), TARGET))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains(from.toString())
                        .contains(TARGET)
                        .contains("not an activatable kind"));
        assertThat(EndpointRule.bind(
                        RelationType.OVERRIDES,
                        from,
                        Optional.empty(),
                        new Urn(NodeKind.DIRECTIVE, "D"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOfSatisfying(Fault.class, fault -> {
                    assertThat(fault.code()).isEqualTo(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE);
                    assertThat(fault.reason(from.toString(), TARGET))
                            .isEqualTo(EndpointRule.overridesSourceRefusal(
                                            RelationType.OVERRIDES, from, from.toString(), TARGET)
                                    .orElseThrow());
                });
    }

    @ParameterizedTest
    @EnumSource(
            value = ArtifactKind.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"TEMPLATE", "ASSET", "GUARD", "LIFECYCLE"})
    @DisplayName("an overrides edge from each activatable kind passes the source rule")
    void anOverridesEdgeFromEachActivatableKindPasses(ArtifactKind kind) {
        assertThat(kind.activatable()).isTrue();
        Urn from = sourceOf(kind);

        assertThat(EndpointRule.overridesSourceRefusal(RelationType.OVERRIDES, from, from.toString(), TARGET))
                .isEmpty();
    }

    @Test
    @DisplayName("the four kinds the source rule refuses are exactly the kinds that are not activatable")
    void theFourKindsTheRuleRefusesAreExactlyTheKindsThatAreNotActivatable() {
        assertThat(ArtifactKind.values())
                .filteredOn(kind -> !kind.activatable())
                .containsExactlyInAnyOrder(
                        ArtifactKind.TEMPLATE, ArtifactKind.ASSET, ArtifactKind.GUARD, ArtifactKind.LIFECYCLE);
    }

    @Test
    @DisplayName("the source rule judges overrides only, and the source only")
    void theSourceRuleJudgesOverridesAndTheSourceOnly() {
        Urn template = new Urn(NodeKind.TEMPLATE, "t.md");
        for (RelationType relation : RelationType.values()) {
            if (relation != RelationType.OVERRIDES) {
                assertThat(EndpointRule.overridesSourceRefusal(relation, template, template.toString(), TARGET))
                        .as(relation.token())
                        .isEmpty();
            }
        }
        assertThat(EndpointRule.overridesSourceRefusal(
                        RelationType.OVERRIDES, new Urn(NodeKind.DIRECTIVE, "E"), "directive:E", "template:t.md"))
                .as("an overrides edge to a template")
                .isEmpty();
        assertThat(EndpointRule.overridesSourceRefusal(RelationType.OVERRIDES, template, "main::template:t.md", TARGET))
                .as("a source written with a pack name is named as written")
                .hasValueSatisfying(reason -> assertThat(reason).contains("main::template:t.md"));
    }

    @Test
    @DisplayName("the source rule's code is the one spelling of #447")
    void theSourceRulesCodeIsOneToken() {
        assertThat(EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE).isEqualTo("overrides_source_not_activatable");
    }
}
