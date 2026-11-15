package ai.speckitty.charter.kernel.domain.kinds;

/**
 * The {@link ai.speckitty.charter.kernel.domain.NodeKind#TERM} kind, as a type: one term of a glossary context, only
 * ever the target of a {@code vocabulary} edge (ADR-0049 decision 4). It is never instantiated.
 */
public final class Term implements Kind, VocabularyTarget {

    private Term() {}
}
