package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A target of {@code requires} in its first rectangle: a guidance kind, an asset or a template.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface RequiresTarget extends Kind
        permits Directive, Tactic, Styleguide, Toolguide, Paradigm, Procedure, Asset, Template {}
