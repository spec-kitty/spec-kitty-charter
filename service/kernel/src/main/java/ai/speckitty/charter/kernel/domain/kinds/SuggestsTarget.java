package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A target of {@code suggests}: a guidance kind, an asset, a template or an agent profile.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface SuggestsTarget extends Kind
        permits Directive, Tactic, Styleguide, Toolguide, Paradigm, Procedure, Asset, Template, AgentProfile {}
