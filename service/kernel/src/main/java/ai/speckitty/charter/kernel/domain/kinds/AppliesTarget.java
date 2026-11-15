package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A target of {@code applies}: a directive or a tactic.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface AppliesTarget extends Kind permits Directive, Tactic {}
