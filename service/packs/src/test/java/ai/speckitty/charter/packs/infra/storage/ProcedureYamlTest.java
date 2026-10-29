package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Procedure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A sanitized procedure round-trips through the YAML format, the binding and the store (FR-002, FR-003); a procedure in
 * the retired shape is refused at load with the schema violation while its sanitized twin loads (SC-005).
 */
@DisplayName("A procedure round-trips through the YAML format and the store; the retired shape is refused (#194)")
class ProcedureYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M60H9BPROCEDURERETIRED0A");
    private static final String DIR = "/roundtrip-fixtures/procedure/";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("every field survives DSO, YAML, DSO, entity and DSO again, and the written text means the source")
    void theFullFixtureRoundTrips() throws IOException {
        String source = fixture("full");
        ProcedureDso dso = read(source);

        String written = strategy.write(ProcedureBinding.INSTANCE, dso);
        ProcedureDso reread = read(written);
        ProcedureDso throughTheDomain =
                ProcedureBinding.INSTANCE.toDso(ProcedureBinding.INSTANCE.toEntity(PACK, reread));

        assertThat(reread).isEqualTo(dso);
        assertThat(throughTheDomain).isEqualTo(dso);
        assertThat(MeaningComparator.adr0036().compareTexts("procedure", source, written))
                .isEmpty();
        assertThat(map(Yaml.load(written)).keySet())
                .as("keys in schema order")
                .containsExactly(
                        "schema_version",
                        "id",
                        "title",
                        "description",
                        "entry_condition",
                        "exit_condition",
                        "steps",
                        "anti_patterns",
                        "references",
                        "notes",
                        "applies_to_languages");
    }

    @Test
    @DisplayName(
            "anti-pattern entries keep exactly their stated fields: title only, with content, one or both examples")
    void antiPatternEntriesKeepTheirShape() throws IOException {
        ProcedureDso dso = read(fixture("full"));
        Procedure entity = ProcedureBinding.INSTANCE.toEntity(PACK, dso);

        assertThat(dso.antiPatterns())
                .containsExactly(
                        new ProcedureDso.AntiPattern(
                                "Bare avoid entry", Optional.empty(), Optional.empty(), Optional.empty()),
                        new ProcedureDso.AntiPattern(
                                "Landing unreviewed work",
                                Optional.of("Work lands only after review."),
                                Optional.empty(),
                                Optional.empty()),
                        new ProcedureDso.AntiPattern(
                                "Force pushing", Optional.empty(), Optional.of("git push --force"), Optional.empty()),
                        new ProcedureDso.AntiPattern(
                                "Skipping the merge tool",
                                Optional.of("Use the merge tool."),
                                Optional.of("git merge --no-verify"),
                                Optional.of("spec-kitty merge")));
        assertThat(entity.antiPatterns()).hasSize(4);
        assertThat(entries(written(dso), "anti_patterns"))
                .extracting(Map::keySet)
                .map(List::copyOf)
                .containsExactly(
                        List.of("title"),
                        List.of("title", "content"),
                        List.of("title", "bad_example"),
                        List.of("title", "content", "bad_example", "good_example"));
    }

    @Test
    @DisplayName("a step keeps its role when stated and stays role-less when not")
    void aStepKeepsOrOmitsItsRole() throws IOException {
        Procedure entity = ProcedureBinding.INSTANCE.toEntity(PACK, read(fixture("full")));

        assertThat(entity.steps().get(0).role()).contains("operator");
        assertThat(entity.steps().get(1).role()).isEmpty();
        assertThat(entries(written(read(fixture("full"))), "steps"))
                .extracting(Map::keySet)
                .map(List::copyOf)
                .containsExactly(List.of("title", "description", "role", "on_success", "on_failure"), List.of("title"));
    }

    @Test
    @DisplayName("references keep their when and reason when stated, omit them when not, and keep order and duplicates")
    void referencesKeepTheirOptionalTexts() throws IOException {
        ProcedureDso dso = read(fixture("full"));

        assertThat(ProcedureBinding.INSTANCE.toEntity(PACK, dso).references())
                .extracting(ref -> ref.type().token() + ":" + ref.id() + ":"
                        + ref.when().isPresent() + ":" + ref.reason().isPresent())
                .containsExactly(
                        "tactic:review-first:true:true",
                        "directive:REVIEW_FIRST:false:false",
                        "tactic:review-first:true:false",
                        "styleguide:commit-style:false:true");
        assertThat(entries(written(dso), "references"))
                .extracting(Map::keySet)
                .map(List::copyOf)
                .containsExactly(
                        List.of("type", "id", "when", "reason"),
                        List.of("type", "id"),
                        List.of("type", "id", "when"),
                        List.of("type", "id", "reason"));
    }

    @Test
    @DisplayName("a procedure with only the required fields writes no optional key back")
    void theMinimalFixtureWritesNoOptionalKey() throws IOException {
        String source = fixture("minimal");
        ProcedureDso dso = read(source);
        String written = written(dso);

        assertThat(read(written)).isEqualTo(dso);
        assertThat(map(Yaml.load(written)))
                .doesNotContainKeys("anti_patterns", "references", "notes", "applies_to_languages");
        assertThat(MeaningComparator.adr0036().compareTexts("procedure", source, written))
                .isEmpty();
    }

    @Test
    @DisplayName("at load, the retired shape is refused with the schema violation while its sanitized twin loads")
    void theRetiredShapeIsRefusedAtLoad(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("procedure/retired-pack", host);
        PackLocation location = new PackLocation("retired-pack", PackLevel.ORG, pack);

        Pack loaded = new DomainRepositoryService(() -> List.of(location), strategy).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.PROCEDURE, Procedure.class))
                .extracting(procedure -> procedure.id().value())
                .containsExactly("sanitized-shape");
        assertThat(loaded.violations()).isNotEmpty().allSatisfy(violation -> {
            assertThat(violation.file()).isEqualTo("old-shape.procedure.yaml");
            assertThat(violation.kind()).isEqualTo("schema");
        });
        assertThat(loaded.violations())
                .extracting(PackElementViolation::reason)
                .anySatisfy(reason -> assertThat(reason).contains("'name'"))
                .anySatisfy(reason -> assertThat(reason).contains("'purpose'"))
                .anySatisfy(reason -> assertThat(reason).contains("'actor'"));
    }

    private ProcedureDso read(String text) {
        FileRead<ProcedureDso> read = strategy.read(ProcedureBinding.INSTANCE, text, "probe.procedure.yaml");
        assertThat(read).as("the procedure reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<ProcedureDso>) read).value();
    }

    private String written(ProcedureDso dso) {
        return strategy.write(
                ProcedureBinding.INSTANCE,
                ProcedureBinding.INSTANCE.toDso(ProcedureBinding.INSTANCE.toEntity(PACK, dso)));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ProcedureYamlTest.class.getResourceAsStream(DIR + name + ".procedure.yaml")) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked") // YAML mappings read back from text this test wrote
    private static List<Map<String, Object>> entries(String written, String key) {
        return (List<Map<String, Object>>) map(Yaml.load(written)).get(key);
    }

    @SuppressWarnings("unchecked") // a YAML mapping read back from text this test wrote
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
