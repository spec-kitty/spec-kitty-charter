package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A kind that may override another of its own kind: every activatable kind but step_enhancement.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface Overridable extends Kind
        permits Directive,
                Tactic,
                Styleguide,
                Toolguide,
                Paradigm,
                Procedure,
                AgentProfile,
                MissionStepContract,
                GlossaryContext,
                Skill,
                AntiPattern,
                MissionType {}
