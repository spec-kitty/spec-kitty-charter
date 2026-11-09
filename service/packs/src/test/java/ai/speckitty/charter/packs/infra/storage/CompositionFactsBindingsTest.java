package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * IC-07, A7 Q1, rule E6: the composition facts carry the mission type each held artifact is bound to, read in the same
 * assembly as {@code defined} and {@code edges}, so the in-force resolution prunes a step contract whose mission type is
 * not in force from the verified read and never reads a pack again (NFR-007). A mission step contract is bound to the
 * mission its {@code mission} names.
 */
class CompositionFactsBindingsTest {

    private static final String PACK = "pack-descriptor-only";

    private static PackCompositionFacts factsOf(Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.createDirectories(pack.resolve("mission_step_contracts"));
        Files.writeString(pack.resolve("mission_step_contracts/sd-implement.step-contract.yaml"), """
                id: sd-implement
                schema_version: '1.0'
                action: implement
                mission: software-dev
                steps:
                  - id: first
                    description: The first step
                """);
        Files.writeString(pack.resolve("mission_step_contracts/research-gather.step-contract.yaml"), """
                id: research-gather
                schema_version: '1.0'
                action: gather
                mission: research
                steps:
                  - id: first
                    description: The first step
                """);
        Files.writeString(pack.resolve("jvm.tactic.yaml"), """
                id: jvm
                schema_version: '1.0'
                name: JVM
                steps:
                - title: Build
                """);
        PackLocation location = new PackLocation(PACK, PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                .compositionFacts(new PackIdentifier(PACK));
    }

    @Test
    void aMissionStepContractIsBoundToTheMissionTypeItsMissionNames(@TempDir Path host) throws IOException {
        Map<String, String> bindings = factsOf(host).bindings();

        assertThat(bindings)
                .containsEntry("mission_step_contract:sd-implement", "mission_type:software-dev")
                .containsEntry("mission_step_contract:research-gather", "mission_type:research");
    }

    @Test
    void anArtifactOfAnotherKindIsBoundToNothing(@TempDir Path host) throws IOException {
        assertThat(factsOf(host).bindings()).doesNotContainKey("tactic:jvm").hasSize(2);
    }
}
