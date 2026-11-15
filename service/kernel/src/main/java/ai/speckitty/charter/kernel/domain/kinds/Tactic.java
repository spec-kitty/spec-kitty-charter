package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#TACTIC} kind, as a type. It is never instantiated. */
public final class Tactic
        implements Kind,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeTarget,
                AppliesTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {

    private Tactic() {}
}
