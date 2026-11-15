package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A source of {@code requires} (its first rectangle) and {@code suggests}: a guidance kind, an agent profile or a
 * skill.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface Advisor extends Kind
        permits Directive, Tactic, Styleguide, Toolguide, Paradigm, Procedure, AgentProfile, Skill {}
