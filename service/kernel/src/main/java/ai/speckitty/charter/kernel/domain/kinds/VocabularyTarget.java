package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A kind a {@code vocabulary} edge may end at: a glossary context or one of its terms (ADR-0049 decision 4).
 *
 * <p>A group of the endpoint table (ADR-0064 decision 13): its permitted leaves are exactly the kinds of one side of
 * one rectangle, checked against {@code RelationType.endpoints()} by {@code EndpointTableAgreementTest}.
 */
public sealed interface VocabularyTarget extends Kind permits GlossaryContext, Term {}
