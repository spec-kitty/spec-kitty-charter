package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The mission-type rules of WP06, each proven by a planted pack that holds exactly one fault, refused by validate mode,
 * the store's load and the registration's staged load, beside a control that differs only in that fault:
 *
 * <ul>
 *   <li>{@code planted/r32-mission-step-unlinked}: the step {@code b} of {@code mission_type:rx} has no {@code requires}
 *       edge from its type (FR-011, operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN);
 *   <li>{@code planted/r33-authored-action-sequence} and {@code planted/r34-authored-template-set}: a mission type that
 *       authors the retired field, refused as an unknown field by its name (FR-009, #520).
 * </ul>
 */
@DisplayName("the mission-type rules: every step linked, no retired field (FR-009, FR-011)")
class MissionTypePlantsTest {

    private static final String TYPE = "missions/rx/rx.mission-type.yaml";
    private static final String GRAPH = "mission_type.graph.yaml";
    private static final String UNLINKED = "r32-mission-step-unlinked";
    /** The plants' mission type without the retired field: the control of r33 and r34. */
    private static final String CLEAN_TYPE = """
            schema_version: '1.0'
            id: rx
            title: Rx
            description: A mission type.
            steps:
            - id: a
              title: A
            - id: b
              title: B
            """;

    // ---- mission_step_unlinked --------------------------------------------------------------------------------------

    @Test
    @DisplayName("validate mode: one blocking mission_step_unlinked at the mission type file, naming the step")
    void validateModeRefusesTheUnlinkedStep(@TempDir Path host) {
        List<PackFinding> findings = validate(planted(host, UNLINKED));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.MISSION_STEP_UNLINKED);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.detail()).contains("the step b of mission_type:rx has no requires edge");
        });
    }

    @Test
    @DisplayName("the store's load: one violation of the pack at the mission type file")
    void theLoadRefusesTheUnlinkedStep(@TempDir Path host) {
        WholePackStore.LoadedPack loaded =
                new WholePackStore(new YamlSerializationStrategy()).load(planted(host, UNLINKED));

        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(TYPE);
            assertThat(violation.kind()).isEqualTo(MissionStepLinkRule.CODE);
            assertThat(violation.reason()).contains("action:rx/b");
        });
    }

    @Test
    @DisplayName("registration: 422 validation_failed with one mission_step_unlinked violation at the type")
    void registrationRefusesTheUnlinkedStep(@TempDir Path host) {
        Path pack = planted(host, UNLINKED);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo(TYPE);
                        assertThat(violation.code()).isEqualTo(MissionStepLinkRule.CODE);
                    });
                });
    }

    @Test
    @DisplayName("a requires edge from the type to an action that is not its step is a fault of the graph document")
    void aStrayEdgeIsAFaultOfTheGraphDocument(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNLINKED);
        Path graph = pack.resolve(GRAPH);
        Files.writeString(
                graph,
                Files.readString(graph, StandardCharsets.UTF_8)
                        + "- source: mission_type:rx\n  target: action:rx/c\n  relation: requires\n",
                StandardCharsets.UTF_8);
        String stray = "the requires edge from mission_type:rx to action:rx/c names no step of the type";

        assertThat(validate(pack))
                .extracting(PackFinding::code, PackFinding::file)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PackFindingCode.MISSION_STEP_UNLINKED, GRAPH),
                        org.assertj.core.groups.Tuple.tuple(PackFindingCode.MISSION_STEP_UNLINKED, TYPE));
        assertThat(validate(pack).getFirst().detail()).isEqualTo("qa/" + GRAPH + ": " + stray);
        assertThat(new WholePackStore(new YamlSerializationStrategy())
                        .load(pack)
                        .pack()
                        .violations())
                .extracting(violation -> violation.file() + " " + violation.reason())
                .containsExactly(
                        TYPE + " the step b of mission_type:rx has no requires edge from its type: author"
                                + " mission_type:rx requires action:rx/b",
                        GRAPH + " " + stray);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a repeated step id", "a step whose prompt file is missing"})
    @DisplayName("a mission type its checks refuse is refused at its file alone, the same in validate mode and at load")
    void aTypeItsChecksRefuseIsNotJudgedForItsLinks(String fault, @TempDir Path host) throws IOException {
        Path pack = planted(host, UNLINKED);
        String broken = fault.startsWith("a repeated")
                ? CLEAN_TYPE + "- id: b\n  title: B again\n"
                : CLEAN_TYPE.replace(
                        "- id: b\n  title: B\n", "- id: b\n  title: B\n  prompt_path: steps/b.prompt.md\n");
        assertThat(broken).isNotEqualTo(CLEAN_TYPE);
        Files.writeString(pack.resolve(TYPE), broken, StandardCharsets.UTF_8);

        assertThat(validate(pack)).extracting(PackFinding::file).containsExactly(TYPE);
        assertThat(validate(pack)).extracting(PackFinding::code).doesNotContain(PackFindingCode.MISSION_STEP_UNLINKED);
        // The load names a domain refusal by the type's id and a check refusal by its file (as before WP06); either way
        // it is the type's one fault, and the step links are not judged.
        assertThat(new WholePackStore(new YamlSerializationStrategy())
                        .load(pack)
                        .pack()
                        .violations())
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.file()).isIn(TYPE, "rx");
                    assertThat(violation.kind()).isNotEqualTo(MissionStepLinkRule.CODE);
                });
    }

    @Test
    @DisplayName("control: with the edge to b the same pack validates, loads and registers")
    void theLinkedTypeIsFine(@TempDir Path host) throws IOException {
        Path pack = planted(host, UNLINKED);
        Path graph = pack.resolve(GRAPH);
        Files.writeString(
                graph,
                Files.readString(graph, StandardCharsets.UTF_8)
                        + "- source: mission_type:rx\n  target: action:rx/b\n  relation: requires\n",
                StandardCharsets.UTF_8);

        assertClean(pack);
    }

    // ---- the retired fields -----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"r33-authored-action-sequence", "r34-authored-template-set"})
    @DisplayName("validate mode: one blocking artifact_schema_error at the mission type, naming the field")
    void validateModeRefusesTheRetiredField(String plant, @TempDir Path host) {
        List<PackFinding> findings = validate(planted(host, plant));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(TYPE);
            assertThat(finding.detail())
                    .as("an unknown field, by its name")
                    .contains("unknown key '" + field(plant) + "'");
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"r33-authored-action-sequence", "r34-authored-template-set"})
    @DisplayName("the store's load and registration refuse the type, naming the field")
    void theLoadAndRegistrationRefuseTheRetiredField(String plant, @TempDir Path host) {
        Path pack = planted(host, plant);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(store.load(pack).pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(TYPE);
            assertThat(violation.reason())
                    .as("an unknown field, by its name")
                    .contains("additionalProperties violated at the document: undeclared key '" + field(plant) + "'");
        });
        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo(TYPE);
                        assertThat(violation.detail())
                                .as("an unknown field, by its name")
                                .contains("undeclared key '" + field(plant) + "'");
                    });
                });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"r33-authored-action-sequence", "r34-authored-template-set"})
    @DisplayName("control: the same pack without the field validates, loads and registers")
    void withoutTheFieldTheTypeIsFine(String plant, @TempDir Path host) throws IOException {
        Path pack = planted(host, plant);
        Path type = pack.resolve(TYPE);
        String text = Files.readString(type, StandardCharsets.UTF_8);
        assertThat(text).contains(field(plant));
        Files.writeString(type, CLEAN_TYPE, StandardCharsets.UTF_8);

        assertClean(pack);
    }

    private static String field(String plant) {
        return plant.endsWith("action-sequence") ? "action_sequence" : "template_set";
    }

    private static void assertClean(Path pack) {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(validate(pack)).isEmpty();
        assertThat(store.load(pack).pack().violations()).isEmpty();
        assertThat(store.loadStaged(new StagedPack(pack)).pack().violations()).isEmpty();
    }

    private static List<PackFinding> validate(Path pack) {
        return new PackValidation(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()))
                .findings();
    }

    /** A copy of the planted pack under {@code host}, so a test may change it. */
    private static Path planted(Path host, String plant) {
        String resource = "/planted/" + plant + "/files";
        try {
            Path source = Path.of(Objects.requireNonNull(MissionTypePlantsTest.class.getResource(resource), resource)
                    .toURI());
            Path target = host.resolve("qa");
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.sorted().toList()) {
                    Path copy = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy);
                    }
                }
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
