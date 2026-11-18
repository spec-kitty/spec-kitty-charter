package ai.speckitty.charter.missions.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import ai.speckitty.charter.missions.domain.Substep;
import ai.speckitty.charter.missions.domain.SubstepCandidate;
import ai.speckitty.charter.missions.domain.SubstepDelegation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A substep's candidate links its read only when the scope holds it (FR-004). */
class HeldCandidatesTest {

    private static MissionStep step(String id, ArtifactKind kind, String... candidates) {
        return new MissionStep(
                id,
                id,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(new Substep(
                        "run",
                        "Run",
                        Optional.empty(),
                        List.of(),
                        Optional.of(new SubstepDelegation(
                                kind,
                                List.of(candidates).stream()
                                        .map(SubstepCandidate::parse)
                                        .toList())),
                        Optional.empty())));
    }

    @Test
    void theHeldCandidatesOfStepsAndOnDemandStepsAreTheOnesTheScopeHoldsKeyedAsWritten() {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("rx"))
                .packId(new PackIdentifier("main"))
                .schemaVersion("1.0")
                .title("Rx")
                .description("Rx")
                .step(step("a", ArtifactKind.TACTIC, "tdd", "base::bdd", "absent"))
                .onDemandStep(step("b", ArtifactKind.DIRECTIVE, "D1"))
                .build();
        Set<HeldArtifact> held = Set.of(
                new HeldArtifact(ArtifactKind.TACTIC, "tdd"),
                new HeldArtifact(ArtifactKind.TACTIC, "bdd"),
                new HeldArtifact(ArtifactKind.DIRECTIVE, "D1"),
                new HeldArtifact(ArtifactKind.TACTIC, "D1"),
                new HeldArtifact(ArtifactKind.TACTIC, "unnamed"));

        assertThat(MissionTypeHolding.heldCandidates(type, held))
                .containsExactlyInAnyOrder(
                        new HeldArtifact(ArtifactKind.TACTIC, "tdd"),
                        new HeldArtifact(ArtifactKind.TACTIC, "base::bdd"),
                        new HeldArtifact(ArtifactKind.DIRECTIVE, "D1"));
    }
}
