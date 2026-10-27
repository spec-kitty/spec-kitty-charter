package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.GlossaryPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("A glossary pack round-trips through the YAML format and the store (#192)")
class GlossaryPackYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5YVECGLOSSARYPACKFIXT0A");

    /** Every declared field, each optional one written, terms deliberately unsorted. */
    private static final String FULL = """
            # comments carry no meaning
            id: spk-internal-glossary
            description: Maintainer terms.
            provenance: org
            terms:
              - surface: red main
                definition: >
                  The policy that main may carry known-failing tests when they are
                  honestly documented.
                confidence: 1.0
                status: active
                see_also: [baseline-red gotcha, release readiness, baseline-red gotcha]
                introduced_in_mission: "042"
                synonyms_to_avoid: [broken main]
                aliases: [red-main]
                banned_synonyms: [green-washing]
              - surface: adversarial squad
                definition: A bounded set of delegates.
                confidence: 0.6
                status: "1.0"
              - surface: charter
                definition: An integral confidence reads and writes as the same number.
                confidence: 1
                status: active
            """;

    private static final String MINIMAL = """
            id: qa-glossary
            provenance: org
            terms:
              - surface: boundary value analysis
                definition: A black-box technique.
                confidence: 0.9
                status: active
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("every field of the schema survives read, domain, write and read again")
    void everyFieldRoundTrips() {
        String written = throughTheDomain(read(FULL));

        assertThat(MeaningComparator.adr0036().compareTexts("glossary-pack", FULL, written))
                .as("the written file means what the source meant")
                .isEmpty();
        assertThat(read(written)).isEqualTo(read(FULL));
    }

    @Test
    @DisplayName("terms keep their authored order and a list keeps its repeats")
    void termsKeepTheirOrder() {
        GlossaryPackDso dso = read(throughTheDomain(read(FULL)));

        assertThat(dso.terms())
                .extracting(GlossaryPackDso.Term::surface)
                .containsExactly("red main", "adversarial squad", "charter");
        assertThat(dso.terms().getFirst().seeAlso())
                .containsExactly("baseline-red gotcha", "release readiness", "baseline-red gotcha");
    }

    @Test
    @DisplayName("confidence is written as a number, and a status that looks like a number stays a string")
    void numbersAndStringsKeepTheirTypes() {
        Map<String, Object> first = term(throughTheDomain(read(FULL)), 0);
        Map<String, Object> second = term(throughTheDomain(read(FULL)), 1);

        assertThat(first.get("confidence")).isInstanceOf(Number.class);
        assertThat(((Number) first.get("confidence")).doubleValue()).isEqualTo(1.0);
        assertThat(second.get("confidence")).isInstanceOf(Number.class);
        assertThat(second.get("status")).isEqualTo("1.0");
        assertThat(first.get("introduced_in_mission")).isEqualTo("042");
    }

    @Test
    @DisplayName("an absent optional field stays absent: nothing is written for it, no null and no empty list")
    void absentStaysAbsent() {
        String written = throughTheDomain(read(MINIMAL));

        assertThat(map(Yaml.load(written))).containsOnlyKeys("id", "provenance", "terms");
        assertThat(term(written, 0)).containsOnlyKeys("surface", "definition", "confidence", "status");
        assertThat(MeaningComparator.adr0036().compareTexts("glossary-pack", MINIMAL, written))
                .isEmpty();
    }

    @Test
    @DisplayName("the binding names the glossary-pack schema and file token, and the classification agrees")
    void theBindingMatchesTheClassification() {
        assertThat(KindBindings.bySchema("glossary-pack")).contains(GlossaryPackBinding.INSTANCE);
        assertThat(GlossaryPackBinding.INSTANCE.fileName("qa-glossary")).isEqualTo("qa-glossary.glossary-pack.yaml");
        assertThat(PackFileKind.bundled().classify("qa/glossary_packs/qa-glossary.glossary-pack.yaml"))
                .isEqualTo(new PackFileKind.Mapped("glossary-pack", "packs/schemas/glossary-pack.schema.json"));
    }

    @Test
    @DisplayName("a pack loads a glossary pack whose id is not its file stem, and keeps it where it was authored")
    void anIdThatIsNotTheStemIsKept(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossary_packs/spk-internal.glossary-pack.yaml"), FULL);
        DomainRepositoryService service = service(pack);

        Pack loaded = service.load(PACK);

        assertThat(loaded.violations()).isEmpty();
        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY_PACK, GlossaryPack.class))
                .singleElement()
                .satisfies(glossary -> {
                    assertThat(glossary.localId()).isEqualTo("spk-internal-glossary");
                    assertThat(glossary.terms()).hasSize(3);
                });

        service.saveArtifact(loaded.artifacts(ArtifactKind.GLOSSARY_PACK).getFirst());

        assertThat(PackFileSet.files(pack, ArtifactKind.GLOSSARY_PACK))
                .as("rewritten in place, not under its id")
                .containsExactly("glossary_packs/spk-internal.glossary-pack.yaml");
    }

    @Test
    @DisplayName("a new glossary pack is created as <id>.glossary-pack.yaml, where a load finds it")
    void aNewGlossaryPackIsFoundAgain(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        DomainRepositoryService service = service(pack);

        service.saveArtifact(GlossaryPackBinding.INSTANCE.toEntity(PACK, read(MINIMAL)));

        assertThat(pack.resolve("qa-glossary.glossary-pack.yaml")).isRegularFile();
        assertThat(service.load(PACK).artifacts(ArtifactKind.GLOSSARY_PACK))
                .extracting(artifact -> artifact.localId())
                .containsExactly("qa-glossary");
    }

    @Test
    @DisplayName("a repeated surface is a domain violation of the pack, naming the surface")
    void aRepeatedSurfaceIsAViolation(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossary_packs/g.glossary-pack.yaml"), MINIMAL + """
                  - surface: boundary value analysis
                    definition: Again.
                    confidence: 0.5
                    status: draft
                """);

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY_PACK)).isEmpty();
        assertThat(loaded.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.kind()).isEqualTo("domain");
            assertThat(violation.reason()).contains("[boundary value analysis]");
        });
    }

    @Test
    @DisplayName("a glossary pack file with an unknown key is a violation of the pack, and it is not loaded")
    void anUnknownKeyIsAPackViolation(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossary_packs/g.glossary-pack.yaml"), MINIMAL + "surprise: 1\n");

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY_PACK)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::file)
                .containsExactly("glossary_packs/g.glossary-pack.yaml");
    }

    private GlossaryPackDso read(String text) {
        FileRead<GlossaryPackDso> read = strategy.read(GlossaryPackBinding.INSTANCE, text, "probe.glossary-pack.yaml");
        assertThat(read).as("the planted file reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<GlossaryPackDso>) read).value();
    }

    private String throughTheDomain(GlossaryPackDso dso) {
        GlossaryPackBinding binding = GlossaryPackBinding.INSTANCE;
        return strategy.write(binding, binding.toDso(binding.toEntity(PACK, dso)));
    }

    @SuppressWarnings("unchecked") // a YAML list of mappings read back from text this test wrote
    private static Map<String, Object> term(String written, int index) {
        Object terms = map(Yaml.load(written)).get("terms");
        assertThat(terms).isInstanceOf(List.class);
        return map(((List<Object>) terms).get(index));
    }

    @SuppressWarnings("unchecked") // a YAML mapping read back from text this test wrote
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("glossary-pack-fixture"));
        write(pack.resolve("pack.yaml"), """
                pack_id: 01M5YVECGLOSSARYPACKFIXT0A
                pack_version: 1.0.0
                name: glossary-pack-fixture
                pack_level: org
                """);
        return pack;
    }

    private static DomainRepositoryService service(Path pack) {
        PackLocation location = new PackLocation("glossary-pack-fixture", PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
