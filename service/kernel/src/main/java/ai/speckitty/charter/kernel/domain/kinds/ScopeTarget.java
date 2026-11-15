package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A target of {@code scope}: a guidance kind or an agent profile.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface ScopeTarget extends Kind
        permits Directive, Tactic, Styleguide, Toolguide, Paradigm, Procedure, AgentProfile {}
