package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.MeaningComparator.Difference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every glossary pack and every skill of the pinned open-packs checkout round-trips by meaning (ADR-0036), a skill with
 * its prompt body byte for byte (ADR-0039). Each file is read through the store, so a skill's body is read as its
 * companion, mapped to the domain and back, written with its companion into a fresh pack, read again, and compared
 * with its source. It covers the packs no pack-level gate loads: the #187 gate covers only the built-in pack, which has
 * no skill, and {@code qa} has no {@code pack.yaml}.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Every pinned glossary pack and skill round-trips by meaning, a skill with its body (#192)")
class GlossaryPackAndSkillCorpusTest {

    private static final PackIdentifier PROBE = new PackIdentifier("probe");

    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    @TempDir
    Path host;

    @Test
    @DisplayName("the 4 pinned glossary packs read, write and read back with their meaning unchanged")
    void everyGlossaryPackRoundTrips() throws IOException {
        List<Path> files = files(".glossary-pack.yaml");
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String written = glossary(file, UnaryOperator.identity());
            MeaningComparator.adr0036()
                    .compareTexts("glossary-pack", source, written)
                    .ifPresent(difference -> problems.add(file + ": " + difference));
        }

        assertThat(problems).as("glossary packs that do not round-trip").isEmpty();
        assertThat(files)
                .as("pinned glossary packs: built-in, event-driven, internal and qa")
                .extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder(
                        "spec-kitty-core.glossary-pack.yaml",
                        "evd-glossary.glossary-pack.yaml",
                        "spk-internal.glossary-pack.yaml",
                        "qa-glossary.glossary-pack.yaml");
    }

    @Test
    @DisplayName("the 5 pinned skills and their bodies are read and written as pairs, the body byte for byte")
    void everySkillRoundTripsWithItsBody() throws IOException {
        List<Path> files = files(".skill.yaml");
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            SkillDso source = skill(file);
            SkillDso back = SkillBinding.INSTANCE.toDso(SkillBinding.INSTANCE.toEntity(PROBE, source));
            Path pack = writeSkill(back);
            Optional<SkillDso> reread =
                    store(pack).readAll(PROBE, SkillBinding.INSTANCE).stream().findFirst();

            MeaningComparator.adr0036()
                    .compareTexts(
                            "skill",
                            Files.readString(file, StandardCharsets.UTF_8),
                            Files.readString(pack.resolve(SkillBinding.INSTANCE.fileName(back.id()))))
                    .ifPresent(difference -> problems.add(file + ": " + difference));
            if (!reread.equals(Optional.of(source))) {
                problems.add(file + ": the written pair reads back differently");
            }
            Path body = file.resolveSibling(source.bodyPath().orElseThrow());
            if (!Files.readString(pack.resolve(source.bodyPath().orElseThrow()), StandardCharsets.UTF_8)
                    .equals(Files.readString(body, StandardCharsets.UTF_8))) {
                problems.add(file + ": the body is not written byte for byte");
            }
        }

        assertThat(problems).as("skills that do not round-trip").isEmpty();
        assertThat(files)
                .as("pinned skills: the internal pack's five")
                .extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder(
                        "issue-triage.skill.yaml",
                        "land-pr.skill.yaml",
                        "mission-from-issue.skill.yaml",
                        "report-debrief.skill.yaml",
                        "semi-auto.skill.yaml");
    }

    @Test
    @DisplayName("the corpus exercises every optional term field, multi-line definitions and skill side effects")
    void theCorpusCoversTheSchemas() throws IOException {
        int seeAlso = 0;
        int synonyms = 0;
        int introduced = 0;
        int aliases = 0;
        int multiLine = 0;
        for (Path file : files(".glossary-pack.yaml")) {
            for (GlossaryPackDso.Term term : glossary(file).terms()) {
                seeAlso += term.seeAlso().isEmpty() ? 0 : 1;
                synonyms += term.synonymsToAvoid().isEmpty() ? 0 : 1;
                introduced += term.introducedInMission().isPresent() ? 1 : 0;
                aliases += term.aliases().isEmpty() ? 0 : 1;
                multiLine += term.definition().contains("\n") ? 1 : 0;
            }
        }
        List<SkillDso> skills = new ArrayList<>();
        for (Path file : files(".skill.yaml")) {
            skills.add(skill(file));
        }

        assertThat(seeAlso).as("terms with see_also").isPositive();
        assertThat(synonyms).as("terms with synonyms_to_avoid").isPositive();
        assertThat(introduced).as("terms with introduced_in_mission").isPositive();
        assertThat(aliases).as("terms with aliases").isPositive();
        assertThat(multiLine).as("multi-line definitions").isPositive();
        assertThat(skills).allSatisfy(skill -> {
            assertThat(skill.body()).as("%s has its body", skill.id()).isPresent();
            assertThat(skill.invocation().orElseThrow().sideEffects()).isNotEmpty();
        });
    }

    /**
     * Positive control: a writer that drops every {@code see_also} must turn the corpus red at exactly those terms, and
     * a body that loses one character must fail the byte comparison. If this passes, the comparisons above can see a
     * lossy writer.
     */
    @Test
    @DisplayName("a writer that drops see_also, or a changed body, is caught")
    void aLossyWriterIsCaught() throws IOException {
        List<Difference> differences = new ArrayList<>();
        int seeAlso = 0;
        for (Path file : files(".glossary-pack.yaml")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            seeAlso += (int) glossary(file).terms().stream()
                    .filter(term -> !term.seeAlso().isEmpty())
                    .count();
            differences.addAll(MeaningComparator.adr0036()
                    .differencesOfTexts(
                            "glossary-pack", source, glossary(file, GlossaryPackAndSkillCorpusTest::dropSeeAlso)));
        }

        assertThat(differences)
                .isNotEmpty()
                .allSatisfy(difference -> assertThat(difference.path()).matches("terms\\[\\d+]\\.see_also"));
        assertThat(differences)
                .as("one difference per term that carries see_also")
                .hasSize(seeAlso);

        SkillDso skill = skill(files(".skill.yaml").getFirst());
        String body = skill.body().orElseThrow();
        SkillDso shortened = skill.withBody(Optional.of(body.substring(0, body.length() - 1)));
        Path pack = writeSkill(shortened);
        assertThat(Files.readString(pack.resolve(skill.bodyPath().orElseThrow()), StandardCharsets.UTF_8))
                .isNotEqualTo(body);
    }

    private String glossary(Path file, UnaryOperator<GlossaryPackDso> writer) throws IOException {
        GlossaryPackBinding binding = GlossaryPackBinding.INSTANCE;
        GlossaryPackDso back = binding.toDso(binding.toEntity(PROBE, glossary(file)));
        String written = yaml.write(binding, writer.apply(back));
        assertThat(yaml.read(binding, written, file.toString()))
                .as("%s: the written form reads", file)
                .isInstanceOf(FileRead.Read.class);
        return written;
    }

    private GlossaryPackDso glossary(Path file) throws IOException {
        FileRead<GlossaryPackDso> read = yaml.read(
                GlossaryPackBinding.INSTANCE, Files.readString(file, StandardCharsets.UTF_8), file.toString());
        assertThat(read).as("%s reads", file).isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<GlossaryPackDso>) read).value();
    }

    /** The skill read through the store, its body filled in as its companion. */
    private SkillDso skill(Path file) {
        Path directory = file.getParent();
        FilesystemPackStore store = new FilesystemPackStore(directory.getParent(), yaml);
        PackIdentifier pack = new PackIdentifier(String.valueOf(directory.getFileName()));
        String id = file.getFileName().toString().replace(".skill.yaml", "");
        return store.readOne(pack, SkillBinding.INSTANCE, id)
                .orElseThrow(() -> new AssertionError(file + " does not read with its body"));
    }

    /** A fresh pack holding the one skill, written with its body through the store. */
    private Path writeSkill(SkillDso dso) throws IOException {
        Path pack =
                Files.createDirectories(Files.createTempDirectory(host, "w-").resolve(PROBE.value()));
        store(pack).write(PROBE, SkillBinding.INSTANCE, dso);
        return pack;
    }

    /** The store a pack directory named after {@link #PROBE} is addressed through. */
    private FilesystemPackStore store(Path pack) {
        return new FilesystemPackStore(pack.getParent(), yaml);
    }

    private static GlossaryPackDso dropSeeAlso(GlossaryPackDso dso) {
        return new GlossaryPackDso(
                dso.id(),
                dso.provenance(),
                dso.description(),
                dso.terms().stream()
                        .map(term -> new GlossaryPackDso.Term(
                                term.surface(),
                                term.definition(),
                                term.confidence(),
                                term.status(),
                                List.of(),
                                term.introducedInMission(),
                                term.synonymsToAvoid(),
                                term.aliases(),
                                term.bannedSynonyms()))
                        .toList());
    }

    private static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(OpenPacksCorpus.packsDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }
}
