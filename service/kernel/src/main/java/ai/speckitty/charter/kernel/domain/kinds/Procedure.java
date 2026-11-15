package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#PROCEDURE} kind, as a type. It is never instantiated. */
public final class Procedure
        implements Kind,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {

    private Procedure() {}
}
