package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A source of {@code scope}: a mission type or a step's action.
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface ScopeSource extends Kind permits MissionType, Action {}
