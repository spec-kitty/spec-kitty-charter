package ai.speckitty.charter.kernel.domain.kinds;

/**
 * The six guidance kinds: directive, tactic, styleguide, toolguide, paradigm and procedure.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface Guidance extends Kind permits Directive, Tactic, Styleguide, Toolguide, Paradigm, Procedure {}
