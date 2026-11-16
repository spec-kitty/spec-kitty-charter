package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The planted glossary cases (FR-003, FR-005, C-001; ADR-0049): each {@code planted/glossary-*} directory holds one
 * pack, and its {@code expected.json} names the findings validate mode reports and whether the store loads it. The
 * refusal cases are the control pack {@code glossary-context-valid} with one fault each, so a loader that checked
 * nothing would load them all and fail every refusal row, and a loader that refused everything would fail the control.
 * The old {@code glossary_pack} shape appears here only as a refusal case.
 *
 * <p>A repeated term id is a rule no schema can state; the glossary format reports it as a {@code uniqueItems} violation
 * at the repeat's id ({@code GlossaryYaml#rules}), as a repeated principle name and a repeated step id are reported, so
 * validate mode and the load refuse the same file with the same pointer. The domain keeps the invariant as well.
 *
 * <p>One plant carries two faults: {@code glossary-context-id-not-kebab} declares {@code id: Core} in
 * {@code core.glossary.yaml}, which breaks the id pattern and differs from the file stem. A schema violation makes
 * the file unreadable before the stem rule runs, so the pattern is reported alone, which is the one finding that plant
 * expects; the stem rule has its own plant, {@code glossary-context-id-not-the-stem}.
 *
 * <p>The control's fragment holds one {@code vocabulary} edge onto a term, so a term as a vocabulary target loads; the
 * two {@code glossary-term-as-*} plants turn that edge around, or give it another relation, and are refused (ADR-0049
 * decision 4; operator ruling of 2026-11-14).
 */
@DisplayName("the planted glossary cases fail or load exactly as their expected.json says (ADR-0049)")
class GlossaryPlantedRefusalsTest {

    private static final String CONTROL = "glossary-context-valid";
    /** The fault a load refuses a whole pack with when one of its graph documents breaks its schema. */
    private static final String GRAPH_REFUSAL = "graphRefusal";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Every planted glossary case, by directory name. */
    static Stream<String> cases() {
        return PlantedPacks.names().stream().filter(name -> name.startsWith("glossary-"));
    }

    /** Every planted glossary refusal case: every case but the control. */
    static Stream<String> refusals() {
        return cases().filter(name -> !name.equals(CONTROL));
    }

    @Test
    @DisplayName("the corpus holds the control and the nine refusal cases")
    void theCorpusIsComplete() {
        assertThat(cases())
                .containsExactlyInAnyOrder(
                        CONTROL,
                        "glossary-pack-old-shape",
                        "glossary-context-id-not-kebab",
                        "glossary-term-id-double-hyphen",
                        "glossary-term-id-digit-first",
                        "glossary-term-id-duplicate",
                        "glossary-see-also-not-a-urn",
                        "glossary-context-id-not-the-stem",
                        "glossary-term-as-an-edge-source",
                        "glossary-term-as-a-non-vocabulary-target");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("validate mode reports exactly the expected findings: code, severity, file and place")
    void validateModeReportsTheExpectedFindings(String plant, @TempDir Path host) {
        JsonNode expected = expected(plant);
        Path pack = copy(plant, host);

        List<PackFinding> findings = new PackValidation(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.of(level(expected)), pack.toString()))
                .findings();

        assertThat(findings.stream().map(GlossaryPlantedRefusalsTest::row).toList())
                .as(plant)
                .containsExactlyElementsOf(expectedRows(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("the store's load holds exactly the expected violations, and the pack does not load")
    void theLoadRefusesIt(String plant, @TempDir Path host) {
        JsonNode load = expected(plant).get("load");
        Path pack = copy(plant, host);
        assertThat(load.get("loads").asBoolean()).as(plant).isFalse();
        if (load.has(GRAPH_REFUSAL)) {
            // A graph document that breaks its schema cannot be read, so the whole load is refused with its fault.
            assertThatThrownBy(() -> new WholePackStore(new YamlSerializationStrategy()).load(pack))
                    .as(plant)
                    .isInstanceOf(CharterRefusal.class)
                    .hasMessageEndingWith(load.get(GRAPH_REFUSAL).asString());
            return;
        }

        List<PackElementViolation> violations = new WholePackStore(new YamlSerializationStrategy())
                .load(pack)
                .pack()
                .violations();

        assertThat(violations).as(plant).hasSameSizeAs(load.get("violations"));
        for (int i = 0; i < violations.size(); i++) {
            JsonNode want = load.get("violations").get(i);
            PackElementViolation got = violations.get(i);
            assertThat(got.kind()).as(plant).isEqualTo(want.get("kind").asString());
            assertThat(got.reason()).as(plant).isEqualTo(want.get("says").asString());
            if (want.has("file")) {
                assertThat(got.file()).as(plant).isEqualTo(want.get("file").asString());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("registration: 422 validation_failed holding each expected violation, by code and detail")
    void theRegistrationRefusesIt(String plant, @TempDir Path host) {
        JsonNode load = expected(plant).get("load");
        Path pack = copy(plant, host);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .as(plant)
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    if (load.has(GRAPH_REFUSAL)) {
                        String fault = load.get(GRAPH_REFUSAL).asString();
                        assertThat(refusal.getMessage()).as(plant).contains(fault.substring(0, fault.indexOf(':')));
                        assertThat(refusal.violations())
                                .as(plant)
                                .anySatisfy(violation -> assertThat(violation.detail())
                                        .endsWith(fault.substring(fault.indexOf(':') + 2)));
                        return;
                    }
                    for (JsonNode want : load.get("violations")) {
                        assertThat(refusal.violations()).as(plant).anySatisfy(violation -> {
                            assertThat(violation.code())
                                    .isEqualTo(want.get("kind").asString());
                            assertThat(violation.detail())
                                    .endsWith(want.get("says").asString());
                            if (want.has("file")) {
                                assertThat(violation.pointer())
                                        .isEqualTo(want.get("file").asString());
                            }
                        });
                    }
                });
    }

    @Test
    @DisplayName("positive control: glossary-context-valid validates clean, loads and registers its context, terms and"
            + " its vocabulary edge onto a term")
    void theControlLoads(@TempDir Path host) {
        JsonNode expected = expected(CONTROL);
        Path pack = copy(CONTROL, host);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(expected.get("load").get("loads").asBoolean()).isTrue();
        assertThat(store.load(pack).pack().violations()).isEmpty();
        WholePackWriteStore.Contents staged = store.loadStaged(new StagedPack(pack));
        assertThat(staged.pack().artifacts(ArtifactKind.GLOSSARY))
                .singleElement()
                .isInstanceOfSatisfying(
                        GlossaryContext.class,
                        context -> assertThat(context.terms())
                                .extracting(term -> term.id().value())
                                .containsExactly("lane", "mission", "work-package"));
        assertThat(staged.facts().edges())
                .extracting(edge -> edge.from() + " " + edge.type().token() + " " + edge.to())
                .contains("glossary_context:core vocabulary term:core/lane");
    }

    private static String row(PackFinding finding) {
        return String.join(
                " ",
                finding.code().name().toLowerCase(Locale.ROOT),
                finding.severity().name().toLowerCase(Locale.ROOT),
                finding.file(),
                String.join("/", finding.place()));
    }

    private static List<String> expectedRows(JsonNode expected) {
        List<String> rows = new ArrayList<>();
        for (JsonNode finding : expected.get("findings")) {
            List<String> place = new ArrayList<>();
            finding.get("place").forEach(segment -> place.add(segment.asString()));
            rows.add(String.join(
                    " ",
                    finding.get("code").asString(),
                    finding.get("severity").asString(),
                    finding.get("file").asString(),
                    String.join("/", place)));
        }
        return rows;
    }

    private static PackLevel level(JsonNode expected) {
        return PackLevel.fromToken(expected.get("configuredLevel").asString()).orElseThrow();
    }

    private static JsonNode expected(String plant) {
        return JSON.readTree(
                plantedRoot().resolve(plant).resolve("expected.json").toFile());
    }

    private static Path plantedRoot() {
        return PlantedPacks.directory();
    }

    /** A copy of the planted pack under {@code host}, named {@code qa}, so a test may load it in place. */
    private static Path copy(String plant, Path host) {
        return PlantedPacks.copyOf(plant, host);
    }
}
