package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#STYLEGUIDE} kind, as a type. It is never instantiated. */
public final class Styleguide
        implements Kind,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {

    private Styleguide() {}
}
