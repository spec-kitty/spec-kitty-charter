package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.TermStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("A glossary context round-trips through the YAML format and the store (ADR-0049)")
class GlossaryYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5YVECG10SSARYPACKF1XT0A");

    /** Every declared field, each optional one written, terms deliberately unsorted. */
    private static final String FULL = """
            # comments carry no meaning
            schema_version: "1.0"
            id: spk-internal
            name: Spec Kitty internal
            description: Maintainer terms.
            tags: [maintainers, workflow]
            applies_to:
              paths: ["src/**", "docs/**"]
              languages: [java]
              stacks: [spring]
            terms:
              - id: red-main
                name: red main
                definition: >
                  The policy that main may carry known-failing tests when they are
                  honestly documented.
                examples: ["Main is red, and the gap is recorded."]
                aliases: [red-main]
                avoid: [broken main, green-washing]
                status: active
                see_also: [term:spk-internal/release-readiness, term:qa/baseline, term:spk-internal/release-readiness]
              - id: adversarial-squad
                name: adversarial squad
                definition: A bounded set of delegates.
                status: draft
              - id: release-readiness
                name: release readiness
                definition: The state in which a release may be cut.
                status: deprecated
            """;

    private static final String MINIMAL = """
            schema_version: "1.0"
            id: qa
            name: QA
            description: Quality vocabulary.
            terms:
              - id: boundary-value-analysis
                name: boundary value analysis
                definition: A black-box technique.
                status: active
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("every field of the schema survives read, domain, write and read again")
    void everyFieldRoundTrips() {
        String written = throughTheDomain(read(FULL));

        assertThat(MeaningComparator.adr0036().compareTexts("glossary", FULL, written))
                .as("the written file means what the source meant")
                .isEmpty();
        assertThat(read(written)).isEqualTo(read(FULL));
    }

    @Test
    @DisplayName("the codec reads every field into its place in the domain")
    void theDomainHoldsEveryField() {
        GlossaryContext context = GlossaryBinding.INSTANCE.toEntity(PACK, read(FULL));

        assertThat(context.name()).isEqualTo("Spec Kitty internal");
        assertThat(context.description()).contains("Maintainer terms.");
        assertThat(context.tags()).containsExactly("maintainers", "workflow");
        assertThat(context.appliesTo().paths()).containsExactly("src/**", "docs/**");
        assertThat(context.appliesTo().languages()).containsExactly("java");
        assertThat(context.appliesTo().stacks()).containsExactly("spring");
        assertThat(context.terms())
                .extracting(term -> term.status())
                .containsExactly(TermStatus.ACTIVE, TermStatus.DRAFT, TermStatus.DEPRECATED);
        assertThat(context.terms().getFirst().examples()).containsExactly("Main is red, and the gap is recorded.");
        assertThat(context.terms().getFirst().aliases()).containsExactly("red-main");
        assertThat(context.terms().getFirst().avoid()).containsExactly("broken main", "green-washing");
        assertThat(context.terms().getFirst().seeAlso())
                .extracting(Object::toString)
                .containsExactly(
                        "term:spk-internal/release-readiness",
                        "term:qa/baseline",
                        "term:spk-internal/release-readiness");
    }

    @Test
    @DisplayName("keys are written in the shape's order, at the context and in each term")
    void keysAreWrittenInTheShapesOrder() {
        String written = throughTheDomain(read(FULL));

        assertThat(map(Yaml.load(written)).keySet())
                .containsExactly("schema_version", "id", "name", "description", "tags", "applies_to", "terms");
        assertThat(map(map(Yaml.load(written)).get("applies_to")).keySet())
                .containsExactly("paths", "languages", "stacks");
        assertThat(term(written, 0).keySet())
                .containsExactly("id", "name", "definition", "examples", "aliases", "avoid", "status", "see_also");
    }

    @Test
    @DisplayName("terms keep their authored order and a list keeps its repeats")
    void termsKeepTheirOrder() {
        GlossaryDso dso = read(throughTheDomain(read(FULL)));

        assertThat(dso.terms())
                .extracting(GlossaryDso.Term::id)
                .containsExactly("red-main", "adversarial-squad", "release-readiness");
        assertThat(dso.terms().getFirst().seeAlso())
                .containsExactly(
                        "term:spk-internal/release-readiness",
                        "term:qa/baseline",
                        "term:spk-internal/release-readiness");
    }

    @Test
    @DisplayName("an absent optional field stays absent: nothing is written for it, no null and no empty list")
    void absentStaysAbsent() {
        String written = throughTheDomain(read(MINIMAL));

        assertThat(map(Yaml.load(written))).containsOnlyKeys("schema_version", "id", "name", "description", "terms");
        assertThat(term(written, 0)).containsOnlyKeys("id", "name", "definition", "status");
        assertThat(MeaningComparator.adr0036().compareTexts("glossary", MINIMAL, written))
                .isEmpty();
    }

    @Test
    @DisplayName("an applies_to that states only some lists writes only those")
    void aPartialAppliesToWritesOnlyItsLists() {
        String partial = MINIMAL.replace("terms:\n", "applies_to:\n  languages: [python]\nterms:\n");

        assertThat(map(map(Yaml.load(throughTheDomain(read(partial)))).get("applies_to")))
                .containsOnlyKeys("languages");
    }

    @Test
    @DisplayName("the binding names the glossary schema and file token, places a context in glossaries/, and the"
            + " classification agrees")
    void theBindingMatchesTheClassification() {
        GlossaryBinding binding = GlossaryBinding.INSTANCE;

        assertThat(KindBindings.bySchema("glossary")).contains(binding);
        assertThat(binding.kind()).isEqualTo(ArtifactKind.GLOSSARY);
        assertThat(binding.fileToken()).isEqualTo("glossary");
        assertThat(binding.schemaName()).isEqualTo("glossary");
        assertThat(binding.fileName("qa")).isEqualTo("glossaries/qa.glossary.yaml");
        assertThat(binding.idIsFileStem()).isTrue();
        assertThat(binding.placedAtItsFileName()).isTrue();
        assertThat(GlossaryYaml.INSTANCE.kind()).isEqualTo(ArtifactKind.GLOSSARY);
        assertThat(PackFileKind.bundled().classify("qa/glossaries/qa.glossary.yaml"))
                .isEqualTo(new PackFileKind.Mapped(
                        "glossary", Optional.of("packs/schemas/glossary.schema.json"), ContentForm.YAML));
    }

    @Test
    @DisplayName("a status or a see_also the domain cannot read is refused by the binding, naming it")
    void theBindingRefusesWhatTheDomainCannotRead() {
        GlossaryDso dso = read(MINIMAL);
        GlossaryDso.Term term = dso.terms().getFirst();
        GlossaryDso badStatus = withTerm(
                dso,
                new GlossaryDso.Term(
                        term.id(),
                        term.name(),
                        term.definition(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "retired",
                        List.of()));
        GlossaryDso badLink = withTerm(
                dso,
                new GlossaryDso.Term(
                        term.id(),
                        term.name(),
                        term.definition(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "active",
                        List.of("lane")));

        assertThatThrownBy(() -> GlossaryBinding.INSTANCE.toEntity(PACK, badStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown term status 'retired'");
        assertThatThrownBy(() -> GlossaryBinding.INSTANCE.toEntity(PACK, badLink))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("see_also entry 'lane' is not a URN");
        assertThat(GlossaryBinding.INSTANCE.toEntity(PACK, dso).localId()).isEqualTo("qa");
    }

    @Test
    @DisplayName("a pack loads a context at glossaries/<id>.glossary.yaml and rewrites it in place")
    void aContextLoadsAndIsRewrittenInPlace(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossaries/spk-internal.glossary.yaml"), FULL);
        DomainRepositoryService service = service(pack);

        Pack loaded = service.load(PACK);

        assertThat(loaded.violations()).isEmpty();
        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY, GlossaryContext.class))
                .singleElement()
                .satisfies(context -> {
                    assertThat(context.localId()).isEqualTo("spk-internal");
                    assertThat(context.terms()).hasSize(3);
                });

        service.saveArtifact(loaded.artifacts(ArtifactKind.GLOSSARY).getFirst());

        assertThat(PackFileSet.files(pack, ArtifactKind.GLOSSARY))
                .containsExactly("glossaries/spk-internal.glossary.yaml");
    }

    @Test
    @DisplayName("a new context is created at glossaries/<id>.glossary.yaml, where a load finds it")
    void aNewContextIsFoundAgain(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        DomainRepositoryService service = service(pack);

        service.saveArtifact(GlossaryBinding.INSTANCE.toEntity(PACK, read(MINIMAL)));

        assertThat(pack.resolve("glossaries/qa.glossary.yaml")).isRegularFile();
        assertThat(service.load(PACK).artifacts(ArtifactKind.GLOSSARY))
                .extracting(artifact -> artifact.localId())
                .containsExactly("qa");
    }

    @Test
    @DisplayName("a context whose id is not its file stem is refused; the same file under its id loads")
    void anIdThatIsNotTheStemIsRefused(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossaries/other.glossary.yaml"), MINIMAL);

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::reason)
                .containsExactly("glossary id 'qa' is not its file stem 'other'");

        Files.move(pack.resolve("glossaries/other.glossary.yaml"), pack.resolve("glossaries/qa.glossary.yaml"));
        assertThat(service(pack).load(PACK).violations()).isEmpty();
    }

    @Test
    @DisplayName("a context outside glossaries/ is refused by placement, not skipped")
    void aContextOutsideItsDirectoryIsRefused(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("vocabulary/qa.glossary.yaml"), MINIMAL);

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::reason)
                .containsExactly("glossary 'qa' is not at its place 'glossaries/qa.glossary.yaml'");
    }

    @Test
    @DisplayName("a repeated term id is a domain violation of the pack, naming the id")
    void aRepeatedTermIdIsAViolation(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossaries/qa.glossary.yaml"), MINIMAL + """
                  - id: boundary-value-analysis
                    name: BVA
                    definition: Again.
                    status: draft
                """);

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY)).isEmpty();
        assertThat(loaded.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.kind()).isEqualTo("domain");
            assertThat(violation.reason()).contains("[boundary-value-analysis]");
        });
    }

    @Test
    @DisplayName("a context file with an unknown key is a violation of the pack, and it is not loaded")
    void anUnknownKeyIsAPackViolation(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("glossaries/qa.glossary.yaml"), MINIMAL + "surprise: 1\n");

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.GLOSSARY)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::file)
                .containsExactly("glossaries/qa.glossary.yaml");
    }

    private static GlossaryDso withTerm(GlossaryDso dso, GlossaryDso.Term term) {
        return new GlossaryDso(
                dso.schemaVersion(),
                dso.id(),
                dso.name(),
                dso.description(),
                dso.tags(),
                dso.appliesTo(),
                List.of(term));
    }

    private GlossaryDso read(String text) {
        FileRead<GlossaryDso> read = strategy.read(GlossaryBinding.INSTANCE, text, "glossaries/probe.glossary.yaml");
        assertThat(read).as("the planted file reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<GlossaryDso>) read).value();
    }

    private String throughTheDomain(GlossaryDso dso) {
        GlossaryBinding binding = GlossaryBinding.INSTANCE;
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
        Path pack = Files.createDirectories(host.resolve("glossary-fixture"));
        write(pack.resolve("pack.yaml"), """
                pack_id: 01M5YVECG10SSARYPACKF1XT0A
                pack_version: 1.0.0
                name: glossary-fixture
                pack_level: org
                """);
        return pack;
    }

    private static DomainRepositoryService service(Path pack) {
        PackLocation location = new PackLocation("glossary-fixture", PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
