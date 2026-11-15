package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#ASSET} kind, as a type. It is never instantiated. */
public final class Asset implements Kind, RequiresTarget, SuggestsTarget, GraphAuthored {

    private Asset() {}
}
