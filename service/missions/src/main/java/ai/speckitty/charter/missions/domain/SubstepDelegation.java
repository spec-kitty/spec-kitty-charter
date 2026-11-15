package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;

/**
 * The {@code delegates_to} of a {@link Substep}: the artifacts that could concretize it, as data (operator ruling
 * DM-01M7B1YH1GF15RD0G4ZPSA8KW9). The charter's selections decide which candidate applies. Each candidate also has a
 * {@code scope} edge from the step's action, which the pack load checks, so the kind is one a {@code scope} edge from an
 * {@code action} may reach (squad finding M4), except {@code agent_profile}: a mission type names roles, never profiles
 * (ADR-0032 decision 1, operator ruling DM-01M7DTN8JWR4G1P35NXGCMS65M), so a substep delegates to a guidance kind only.
 *
 * @param kind the kind of every candidate
 * @param candidates the candidates, in authored order; at least one, each once
 */
public record SubstepDelegation(ArtifactKind kind, List<SubstepCandidate> candidates) {

    private static final ModularRuleset<ArtifactKind> KIND_RULES = ModularRuleset.<ArtifactKind>emptyRules()
            .must(Constraints.of(
                    "A substep delegates to a guidance kind, which a scope edge from an action may reach; never to"
                            + " an agent_profile, since a mission type names roles, not profiles.",
                    SubstepDelegation::delegable));
    private static final ModularRuleset<List<SubstepCandidate>> CANDIDATE_RULES =
            ModularRuleset.<List<SubstepCandidate>>emptyRules()
                    .must(Constraints.of("A substep delegation names at least one candidate.", list -> !list.isEmpty()))
                    .must(Constraints.of(
                            "A substep delegation names each candidate once.",
                            list -> list.stream().distinct().count() == list.size()));

    /**
     * Checks the kind and copies the candidates.
     *
     * @param kind the kind
     * @param candidates the candidates
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the kind is no scope target of an
     *     action, there is no candidate, or a candidate stands twice
     */
    public SubstepDelegation {
        KIND_RULES.guard(Objects.requireNonNull(kind, "kind"));
        candidates = List.copyOf(candidates);
        CANDIDATE_RULES.guard(candidates);
    }

    /**
     * Whether a substep may delegate to artifacts of this kind: a {@code scope} edge from an {@code action} may reach it
     * (the endpoint table, ADR-0064 decision 13), and it is not {@code agent_profile}, which the {@code scope} row allows
     * but a mission type never names (ADR-0032 decision 1, operator ruling DM-01M7DTN8JWR4G1P35NXGCMS65M).
     *
     * @param kind the kind
     * @return {@code true} for a guidance kind
     */
    public static boolean delegable(ArtifactKind kind) {
        return kind != ArtifactKind.AGENT_PROFILE && RelationType.SCOPE.allows(NodeKind.ACTION, NodeKind.of(kind));
    }
}
