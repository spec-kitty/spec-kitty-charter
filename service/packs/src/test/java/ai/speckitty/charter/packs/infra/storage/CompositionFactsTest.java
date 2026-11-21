package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the composing read tells the compose about a pack's graph (#554, WP05's {@code CompositionFactsBuilder}, as
 * {@code DomainRepositoryService#compositionFacts} answers it): a step link the pack makes to its own mission type's
 * step is no reference to resolve (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN), the nodes a graph declares for a
 * kind with no file of its own are defined, and the typed edges keep the graph documents' first, the registered shard's
 * last.
 */
class CompositionFactsTest {

    private static final String PACK = "pack-descriptor-only";

    private static final String MISSION_TYPE = """
            schema_version: "1.0"
            id: software-dev
            title: Software development
            description: Build
            steps:
              - id: plan
                title: plan
            on_demand_steps:
              - id: fix
                title: fix
            """;

    private static PackCompositionFacts factsOf(Path pack) {
        PackLocation location = new PackLocation(PACK, PackLevel.PROJECT, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                .compositionFacts(new PackIdentifier(PACK));
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void aStepLinkToTheTypesOwnStepIsNoReferenceAndAnyOtherLinkIs(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        write(pack, "missions/software-dev/software-dev.mission-type.yaml", MISSION_TYPE);
        write(pack, "mission_type.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'mission_type:software-dev', target: 'action:software-dev/plan', relation: requires}
                - {source: 'mission_type:software-dev', target: 'action:software-dev/fix', relation: requires}
                - {source: 'base::mission_type:other', target: 'action:other/plan', relation: requires}
                - {source: 'mission_type:unheld', target: 'action:unheld/plan', relation: requires}
                """);

        List<String> references = factsOf(pack).references();

        assertThat(references)
                .as("the type's own steps, in steps and on_demand_steps, are not references")
                .doesNotContain("action:software-dev/plan", "action:software-dev/fix");
        assertThat(references)
                .as("another pack's type and a type the pack does not hold are")
                .contains("action:other/plan", "action:unheld/plan");
    }

    @Test
    void theNodesAGraphDeclaresForAKindWithNoFileAreDefined(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        write(pack, "nodes.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - {urn: 'anti_pattern:copy-paste', kind: anti_pattern}
                - {urn: 'action:software-dev/plan', kind: action}
                - {urn: 'tactic:declared-only', kind: tactic}
                edges: []
                """);
        write(pack, "drg/fragment.yaml", """
                nodes:
                - {id: shotgun-surgery, kind: anti_patterns, title: Shotgun surgery}
                - {id: fragment-only, kind: tactics, title: Fragment only}
                edges: []
                """);

        assertThat(factsOf(pack).defined())
                .contains("anti_pattern:copy-paste", "action:software-dev/plan", "anti_pattern:shotgun-surgery")
                .as("a kind with files of its own is defined by its file, not by a node")
                .doesNotContain("tactic:declared-only", "tactic:fragment-only");
    }

    @Test
    void theTypedEdgesAreTheGraphDocumentsThenTheRegisteredShardEachKeptOnce(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        write(pack, "missions/software-dev/software-dev.mission-type.yaml", MISSION_TYPE);
        Urn type = Urn.parse("mission_type:software-dev").orElseThrow();
        Urn plan = Urn.parse("action:software-dev/plan").orElseThrow();
        Urn fix = Urn.parse("action:software-dev/fix").orElseThrow();
        write(
                pack,
                RelationshipShard.SHARD_PATH,
                RelationshipShard.write(List.of(
                        Relationship.of(type, fix, RelationType.REQUIRES),
                        Relationship.of(type, plan, RelationType.REQUIRES))));
        write(pack, "z.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'mission_type:software-dev', target: 'action:software-dev/plan', relation: requires}
                """);

        assertThat(factsOf(pack).edges())
                .extracting(edge -> edge.to().toString())
                .as("z.graph.yaml sorts after registered.graph.yaml, yet its edge comes first; the shard's repeat of it"
                        + " is kept where it was first seen")
                .containsExactly("action:software-dev/plan", "action:software-dev/fix");
    }

    @Test
    void aGraphThatCannotBeReadRefusesThePackBeforeItsMissingDescriptor(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.delete(pack.resolve("pack.yaml"));
        write(pack, "broken.graph.yaml", "nodes: [\n");

        // The composing read refuses the graph first, as it always has (S7), whatever else the pack lacks.
        assertThatThrownBy(() -> factsOf(pack))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage())
                                .startsWith("a graph document of " + PACK + " cannot be read: " + PACK
                                        + "/broken.graph.yaml: "));
    }
}
