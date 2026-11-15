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
    @DisplayName("a tension joins two guidance kinds only")
    void aTensionJoinsGuidanceOnly() {
        assertThat(EndpointRule.bindTension(
                        Urn.parse("directive:a").orElseThrow(),
                        Urn.parse("procedure:b").orElseThrow()))
                .isEmpty();
        assertThat(EndpointRule.bindTension(
                        Urn.parse("directive:a").orElseThrow(),
                        Urn.parse("skill:b").orElseThrow()))
                .contains(new Fault(
                        EndpointRule.ENDPOINT_NOT_ALLOWED, "in_tension_with", NodeKind.DIRECTIVE, NodeKind.SKILL));
    }
}
