package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#ANTI_PATTERN} kind, as a type. It is never instantiated. */
public final class AntiPattern implements Kind, GraphAuthored, Enhanceable, Overridable {

    private AntiPattern() {}
}
