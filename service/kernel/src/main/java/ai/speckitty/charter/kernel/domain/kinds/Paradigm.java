package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#PARADIGM} kind, as a type. It is never instantiated. */
public final class Paradigm
        implements Kind,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {

    private Paradigm() {}
}
