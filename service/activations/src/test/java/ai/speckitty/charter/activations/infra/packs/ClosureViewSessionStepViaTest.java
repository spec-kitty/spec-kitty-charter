package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ReachResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A step of a mission type writes the {@code requires} its mission type is answerable for (the cascade's shared and required reports). */
class ClosureViewSessionStepViaTest {

    private static final ArtifactRef M1 = new ArtifactRef(ArtifactKind.MISSION_TYPE, "M1");
    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");

    @Test
    void aViaWrittenByAStepOfAMissionTypeIsKeptAsTheMissionTypesOwn() {
        Urn d = new Urn(NodeKind.DIRECTIVE, "D");
        Urn step = Urn.action("M1", "specify");
        Map<Urn, List<ReachResult.Via>> reached = Map.of(d, List.of(new ReachResult.Via(step, RelationType.REQUIRES)));

        Reach reach = ClosureViewSession.reachOf(
                new ReachResult(reached, Map.of(), Set.of(), Set.of(), Map.of(), Map.of(), Set.of()));

        assertThat(reach.reached().get(D)).containsExactly(new Reach.Via(M1, RelationType.REQUIRES));
    }
}
