package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.application.ReachRequest;
import ai.speckitty.charter.packs.application.ReachResult;
import ai.speckitty.charter.packs.application.Reachability;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A7 Q1, rule W20's step term, on the production path: a pack holds the steps its graph documents declare
 * ({@code action:<M>/<step>}), so a reach over the index built from its real composition facts crosses from the mission
 * type to its step, and on to what the step requires. The fixture is the one of the contract scenario "Selecting a
 * mission type with a cascade selects what its steps reach" ({@code activation-kind-model.feature}).
 */
class MissionTypeStepsHeldTest {

    private static final String PACK = "pack-descriptor-only";
    private static final Urn SOFTWARE_DEV =
            Urn.parse("mission_type:software-dev").orElseThrow();
    private static final Urn IMPLEMENT =
            Urn.parse("action:software-dev/implement").orElseThrow();
    private static final Urn SD = Urn.parse("directive:SD").orElseThrow();
    private static final Urn OTHER = Urn.parse("directive:OTHER").orElseThrow();

    private static Path packWithSteps(Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.createDirectories(pack.resolve("missions/software-dev"));
        Files.writeString(pack.resolve("missions/software-dev/software-dev.mission-type.yaml"), """
                schema_version: "1.0"
                id: software-dev
                title: Software development
                description: Build software
                steps:
                  - id: implement
                    title: Implement
                    role: implementer
                """);
        Files.createDirectories(pack.resolve("directives"));
        Files.writeString(pack.resolve("directives/SD.directive.yaml"), """
                schema_version: '1.0'
                id: SD
                title: SD
                intent: The directive the implement step requires
                enforcement: required
                """);
        Files.writeString(pack.resolve("directives/OTHER.directive.yaml"), """
                schema_version: '1.0'
                id: OTHER
                title: OTHER
                intent: A directive nothing reaches
                enforcement: required
                """);
        Files.writeString(pack.resolve("steps.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - urn: action:software-dev/implement
                  kind: action
                  label: implement
                edges:
                - source: action:software-dev/implement
                  target: directive:SD
                  relation: requires
                """);
        return pack;
    }

    private static DomainRepositoryService over(Path pack) {
        PackLocation location = new PackLocation(PACK, PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    @Test
    void aPackHoldsTheStepNodesItsGraphDocumentsDeclare(@TempDir Path host) throws IOException {
        PackCompositionFacts facts = over(packWithSteps(host)).compositionFacts(new PackIdentifier(PACK));

        assertThat(facts.defined())
                .contains("mission_type:software-dev", "action:software-dev/implement", "directive:SD");
    }

    @Test
    void aReachFromTheMissionTypeOverItsRealFactsCrossesToItsStepAndWhatTheStepRequires(@TempDir Path host)
            throws IOException {
        DomainRepositoryService service = over(packWithSteps(host));
        PackIdentifier id = new PackIdentifier(PACK);
        PackCompositionFacts facts = service.compositionFacts(id);
        PackUnderResolution pack = new PackUnderResolution(
                new ConfiguredPack(id, PackLevel.ORG), service.get(id), facts.changed(), facts.defined());
        EdgeIndex index = EdgeIndex.of(List.of(pack), Map.of(PACK, facts));

        ReachResult result = Reachability.reach(
                index,
                new ReachRequest(
                        Set.of(SOFTWARE_DEV),
                        EnumSet.of(RelationType.REQUIRES, RelationType.SCOPE),
                        urn -> false,
                        Optional.of(ReachRequest.Lineage.specializesFrom())));

        assertThat(result.reached()).containsOnlyKeys(SOFTWARE_DEV, IMPLEMENT, SD);
        assertThat(result.reached().get(IMPLEMENT))
                .containsExactly(new ReachResult.Via(SOFTWARE_DEV, RelationType.REQUIRES));
        assertThat(result.reached().get(SD)).containsExactly(new ReachResult.Via(IMPLEMENT, RelationType.REQUIRES));
        assertThat(result.reached()).doesNotContainKey(OTHER);
    }
}
