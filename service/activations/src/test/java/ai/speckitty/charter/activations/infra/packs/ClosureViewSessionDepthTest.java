package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.CascadeRules;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code suggests} bound of a plan (IC-21) crosses the adapter in both directions. */
class ClosureViewSessionDepthTest {

    private static final ArtifactRef P = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "p");

    @Test
    void aPlansBoundIsAskedOfThePacksSlice() {
        ReachRequest bounded = ClosureViewSession.requestOf(
                new ReachPlan(Set.of(P), CascadeRules.CASCADE_RELATIONS, Set.of(), true, OptionalInt.of(0)));
        ReachRequest unlimited =
                ClosureViewSession.requestOf(new ReachPlan(Set.of(P), CascadeRules.CASCADE_RELATIONS, Set.of(), true));

        assertThat(bounded.suggestsBound()).isEqualTo(OptionalInt.of(0));
        assertThat(unlimited.suggestsBound()).isEmpty();
    }

    @Test
    void theFrontierPastTheBoundIsCopiedWithoutTheNodesThatAreNoArtifact() {
        Urn ha = new Urn(NodeKind.DIRECTIVE, "HA");
        Urn action = Urn.action("software-dev", "implement");

        Reach reach = ClosureViewSession.reachOf(
                new ReachResult(Map.of(), Map.of(), Set.of(), Set.of(), Map.of(), Map.of(), Set.of(ha, action)));

        assertThat(reach.beyondBound()).containsExactly(new ArtifactRef(ArtifactKind.DIRECTIVE, "HA"));
    }
}
