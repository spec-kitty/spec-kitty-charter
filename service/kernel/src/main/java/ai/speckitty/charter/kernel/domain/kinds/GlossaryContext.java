package ai.speckitty.charter.kernel.domain.kinds;

/**
 * The {@link ai.speckitty.charter.kernel.domain.NodeKind#GLOSSARY_CONTEXT} kind, as a type: a glossary context, the
 * artifact of the kind {@code glossary}. It is never instantiated.
 */
public final class GlossaryContext implements Kind, GraphAuthored, Enhanceable, Overridable, VocabularyTarget {

    private GlossaryContext() {}
}
