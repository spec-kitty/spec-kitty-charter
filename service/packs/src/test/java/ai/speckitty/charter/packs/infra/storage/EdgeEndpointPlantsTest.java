package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.domain.RelationshipEndpointRule;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * An edge whose relation does not allow its endpoint kinds is invalid content (ADR-0064 decision 13). Each planted pack
 * holds exactly that one fault in one graph document, and validate mode, the store's load and the registration's staged
 * load each refuse it with {@code relationship_endpoint_not_allowed}:
 *
 * <ul>
 *   <li>{@code planted/r30-edge-procedure-requires-action}: a procedure that requires a step's action (#350);
 *   <li>{@code planted/r31-edge-overrides-across-kinds}: a directive that overrides a tactic.
 * </ul>
 *
 * <p>The control is the same pack with only the edge's target changed to one the relation allows.
 */
@DisplayName("an edge its relation's endpoint table does not allow is refused (ADR-0064 decision 13, #350)")
class EdgeEndpointPlantsTest {

    @ParameterizedTest(name = "{0}: validate mode")
    @CsvSource({
        "r30-edge-procedure-requires-action, procedure.graph.yaml, a requires edge may not connect procedure to action",
        "r31-edge-overrides-across-kinds, overrides.graph.yaml, an overrides edge may not connect directive to tactic"
    })
    @DisplayName("validate mode: one blocking finding at the edge of the graph document")
    void validateModeRefusesIt(String plant, String document, String reason, @TempDir Path host) {
        Path pack = planted(host, plant);

        List<PackFinding> findings = validate(pack);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(document);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).endsWith(": " + reason);
        });
    }

    @ParameterizedTest(name = "{0}: the load")
    @CsvSource({
        "r30-edge-procedure-requires-action, procedure.graph.yaml, a requires edge may not connect procedure to action",
        "r31-edge-overrides-across-kinds, overrides.graph.yaml, an overrides edge may not connect directive to tactic"
    })
    @DisplayName("the store's load: one violation of the pack at the graph document")
    void theLoadRefusesIt(String plant, String document, String reason, @TempDir Path host) {
        Path pack = planted(host, plant);

        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(pack);

        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(document);
            assertThat(violation.kind()).isEqualTo(RelationshipEndpointRule.CODE);
            assertThat(violation.reason()).isEqualTo(reason);
        });
    }

    @ParameterizedTest(name = "{0}: registration")
    @CsvSource({
        "r30-edge-procedure-requires-action, procedure.graph.yaml",
        "r31-edge-overrides-across-kinds, overrides.graph.yaml"
    })
    @DisplayName("registration: 422 validation_failed with one violation at the graph document")
    void registrationRefusesIt(String plant, String document, @TempDir Path host) {
        Path pack = planted(host, plant);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo(document);
                        assertThat(violation.code()).isEqualTo(RelationshipEndpointRule.CODE);
                    });
                });
    }

    @ParameterizedTest(name = "{0}: a composing read")
    @CsvSource({
        "r30-edge-procedure-requires-action, procedure.graph.yaml, a requires edge may not connect procedure to action",
        "r31-edge-overrides-across-kinds, overrides.graph.yaml, an overrides edge may not connect directive to tactic"
    })
    @DisplayName("a composing read: pack_unreadable naming the graph document, then the code, then the reason")
    void aComposingReadNamesTheCode(String plant, String document, String reason, @TempDir Path host) {
        Path pack = planted(host, plant);
        DomainRepositoryService reads = new DomainRepositoryService(
                () -> List.of(new PackLocation("qa", PackLevel.ORG, pack)), new YamlSerializationStrategy());

        assertThatThrownBy(() -> reads.compositionFacts(new PackIdentifier("qa")))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage())
                            .isEqualTo("the file " + document + " of qa cannot be read: "
                                    + RelationshipEndpointRule.CODE + ": " + reason);
                });
    }

    @ParameterizedTest(name = "{0}: control with the target {3}")
    @CsvSource({
        "r30-edge-procedure-requires-action, procedure.graph.yaml, action:software-dev/implement, directive:D",
        "r31-edge-overrides-across-kinds, overrides.graph.yaml, tactic:t, directive:E"
    })
    @DisplayName("control: the same pack with a target the relation allows validates, loads and registers")
    void theAllowedTargetIsFine(String plant, String document, String refused, String allowed, @TempDir Path host)
            throws IOException {
        Path pack = planted(host, plant);
        Path graph = pack.resolve(document);
        Files.writeString(
                graph,
                Files.readString(graph, StandardCharsets.UTF_8).replace("target: " + refused, "target: " + allowed),
                StandardCharsets.UTF_8);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(validate(pack)).isEmpty();
        assertThat(store.load(pack).pack().violations()).isEmpty();
        assertThat(store.loadStaged(new StagedPack(pack)).pack().violations()).isEmpty();
    }

    @ParameterizedTest(name = "the load: {0} {1} {2}")
    @CsvSource({
        "step_enhancement:ghost, enhances, directive:D, an enhances edge may not connect step_enhancement to directive",
        "other::step_enhancement:e, enhances, directive:D, an enhances edge may not connect step_enhancement to directive"
    })
    @DisplayName("the store's load refuses an edge from a step enhancement the pack does not hold, or another pack's")
    void theLoadRefusesAnEnhancementEdgeNoOtherRuleJudges(
            String source, String relation, String target, String reason, @TempDir Path host) throws IOException {
        Path pack = planted(host, "r31-edge-overrides-across-kinds");
        Files.writeString(
                pack.resolve("overrides.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                generated_by: planted
                nodes: []
                edges:
                - source: %s
                  target: %s
                  relation: %s
                """.formatted(source, target, relation), StandardCharsets.UTF_8);

        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(pack);

        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("overrides.graph.yaml");
            assertThat(violation.kind()).isEqualTo(RelationshipEndpointRule.CODE);
            assertThat(violation.reason()).isEqualTo(reason);
        });
    }

    private static List<PackFinding> validate(Path pack) {
        return new PackValidation(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()))
                .findings();
    }

    /** A copy of the planted pack under {@code host}, so a test may change it. */
    private static Path planted(Path host, String plant) {
        return PlantedPacks.copyOf(plant, host);
    }
}
