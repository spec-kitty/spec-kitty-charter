package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A kind a graph document may name, the source of {@code vocabulary}: every kind but guard, lifecycle and term.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface GraphAuthored extends Kind
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
                Asset,
                Template,
                Action,
                MissionType,
                StepEnhancement {}
