package ai.speckitty.charter.kernel.domain.kinds;

/** The {@link ai.speckitty.charter.kernel.domain.NodeKind#AGENT_PROFILE} kind, as a type. It is never instantiated. */
public final class AgentProfile
        implements Kind, Advisor, SuggestsTarget, ScopeTarget, GraphAuthored, Enhanceable, Overridable {

    private AgentProfile() {}
}
