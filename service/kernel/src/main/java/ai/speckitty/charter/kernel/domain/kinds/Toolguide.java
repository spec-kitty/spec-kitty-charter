package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#TOOLGUIDE} kind, as a type. It is never instantiated. */
public final class Toolguide
        implements Kind,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {

    private Toolguide() {}
}
