package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator.Difference;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every glossary context and every skill of the example packs round-trips by meaning (ADR-0036), a skill with
 * its prompt body byte for byte (ADR-0039). Each file is read through the store, so a skill's body is read as its
 * companion, mapped to the domain and back, written with its companion into a fresh pack, read again, and compared
 * with its source. It covers the org example pack's skill, which the #187 built-in gate does
 * not load.
 *
 * <p>Reads the charter-owned example packs ({@code ExamplePacks}, ADR-0070) and runs under {@code :packs:test}.
 */
@DisplayName("Every example glossary context and skill round-trips by meaning, a skill with its body (#192)")
class GlossaryAndSkillCorpusTest {

    private static final PackIdentifier PROBE = new PackIdentifier("probe");

    private static final PackDirectory PROBE_DIRECTORY = PackDirectory.of(PROBE);

    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    @TempDir
    Path host;

    @Test
    @DisplayName("every example glossary context reads, writes and reads back with its meaning unchanged")
    void everyGlossaryContextRoundTrips() throws IOException {
        List<Path> files = files(".glossary.yaml");
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String written = glossary(file, UnaryOperator.identity());
            MeaningOfTexts.adr0036()
                    .compareTexts("glossary", source, written)
                    .ifPresent(difference -> problems.add(file + ": " + difference));
        }

        assertThat(problems).as("glossary contexts that do not round-trip").isEmpty();
        assertThat(files).as("glossary contexts in the example packs").isNotEmpty();
        PackFiles.assertEachPackHolds("glossary context", files, ExamplePacks.BUILT_IN);
    }

    @Test
    @DisplayName("every example skill and its body are read and written as pairs, the body byte for byte")
    void everySkillRoundTripsWithItsBody() throws IOException {
        List<Path> files = files(".skill.yaml");
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            SkillDso source = skill(file);
            SkillDso back = SkillBinding.INSTANCE.toDso(SkillBinding.INSTANCE.toEntity(PROBE, source));
            Path pack = writeSkill(back);
            Optional<SkillDso> reread = store(pack).readAll(PROBE_DIRECTORY, SkillBinding.INSTANCE).stream()
                    .findFirst();

            MeaningOfTexts.adr0036()
                    .compareTexts(
                            "skill",
                            Files.readString(file, StandardCharsets.UTF_8),
                            Files.readString(pack.resolve(SkillBinding.INSTANCE.fileName(back.id()))))
                    .ifPresent(difference -> problems.add(file + ": " + difference));
            if (!reread.equals(Optional.of(source))) {
                problems.add(file + ": the written pair reads back differently");
            }
            if (source.bodyPath().isPresent() && !bodyWrittenByteForByte(file, source, pack)) {
                problems.add(file + ": the body is not written byte for byte");
            }
        }

        assertThat(problems).as("skills that do not round-trip").isEmpty();
        assertThat(files).as("skills in the example packs").isNotEmpty();
        PackFiles.assertEachPackHolds("skill", files, ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT);
    }

    @Test
    @DisplayName("the corpus exercises every optional field, every status, multi-line definitions and skill side"
            + " effects")
    void theCorpusCoversTheSchemas() throws IOException {
        int seeAlso = 0;
        int avoid = 0;
        int examples = 0;
        int aliases = 0;
        int multiLine = 0;
        int tags = 0;
        int appliesTo = 0;
        java.util.Set<String> statuses = new java.util.TreeSet<>();
        for (Path file : files(".glossary.yaml")) {
            GlossaryDso context = glossary(file);
            tags += context.tags().isEmpty() ? 0 : 1;
            appliesTo += context.appliesTo().paths().isEmpty() ? 0 : 1;
            for (GlossaryDso.Term term : context.terms()) {
                seeAlso += term.seeAlso().isEmpty() ? 0 : 1;
                avoid += term.avoid().isEmpty() ? 0 : 1;
                examples += term.examples().isEmpty() ? 0 : 1;
                aliases += term.aliases().isEmpty() ? 0 : 1;
                multiLine += term.definition().contains("\n") ? 1 : 0;
                statuses.add(term.status());
            }
        }
        List<SkillDso> skills = new ArrayList<>();
        for (Path file : files(".skill.yaml")) {
            skills.add(skill(file));
        }

        assertThat(seeAlso).as("terms with see_also").isPositive();
        assertThat(avoid).as("terms with avoid").isPositive();
        assertThat(examples).as("terms with examples").isPositive();
        assertThat(aliases).as("terms with aliases").isPositive();
        assertThat(multiLine).as("multi-line definitions").isPositive();
        assertThat(tags).as("contexts with tags").isPositive();
        assertThat(appliesTo).as("contexts with applies_to paths").isPositive();
        assertThat(statuses).as("term statuses").containsExactly("active", "deprecated", "draft");
        assertThat(skills).allSatisfy(skill -> {
            if (skill.bodyPath().isPresent()) {
                assertThat(skill.body()).as("%s has its body", skill.id()).isPresent();
            } else {
                assertThat(skill.expandsTo())
                        .as("%s, a wrapper, expands", skill.id())
                        .isPresent();
            }
        });
        assertThat(skills)
                .as("skills with a body and wrapper skills")
                .anySatisfy(skill -> assertThat(skill.body()).isPresent())
                .anySatisfy(skill -> assertThat(skill.expandsTo()).isPresent());
        assertThat(skills)
                .as("skills declaring a side effect")
                .anySatisfy(skill -> assertThat(skill.invocation().orElseThrow().sideEffects())
                        .isNotEmpty());
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
        for (Path file : files(".glossary.yaml")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            seeAlso += (int) glossary(file).terms().stream()
                    .filter(term -> !term.seeAlso().isEmpty())
                    .count();
            differences.addAll(MeaningOfTexts.adr0036()
                    .differencesOfTexts("glossary", source, glossary(file, GlossaryAndSkillCorpusTest::dropSeeAlso)));
        }

        assertThat(differences)
                .isNotEmpty()
                .allSatisfy(difference -> assertThat(difference.path()).matches("terms\\[\\d+]\\.see_also"));
        assertThat(differences)
                .as("one difference per term that carries see_also")
                .hasSize(seeAlso);

        Path file = files(".skill.yaml").stream()
                .filter(path -> skill(path).bodyPath().isPresent())
                .findFirst()
                .orElseThrow();
        SkillDso skill = skill(file);
        String body = skill.body().orElseThrow();
        Path shortened = writeSkill(skill.withBody(Optional.of(body.substring(0, body.length() - 1))));
        assertThat(bodyWrittenByteForByte(file, skill, shortened))
                .as("the comparison the corpus test uses sees a body one character short")
                .isFalse();
        assertThat(bodyWrittenByteForByte(file, skill, writeSkill(skill)))
                .as("and passes the unchanged one")
                .isTrue();
    }

    /** Whether the body written in {@code pack} has exactly the bytes of the source skill's body file. */
    private static boolean bodyWrittenByteForByte(Path file, SkillDso source, Path pack) throws IOException {
        String bodyPath = source.bodyPath().orElseThrow();
        return Files.mismatch(file.resolveSibling(bodyPath), pack.resolve(bodyPath)) == -1;
    }

    private String glossary(Path file, UnaryOperator<GlossaryDso> writer) throws IOException {
        GlossaryBinding binding = GlossaryBinding.INSTANCE;
        GlossaryDso back = binding.toDso(binding.toEntity(PROBE, glossary(file)));
        String written = yaml.write(binding, writer.apply(back));
        assertThat(yaml.read(binding, written, file.toString()))
                .as("%s: the written form reads", file)
                .isInstanceOf(FileRead.Read.class);
        return written;
    }

    private GlossaryDso glossary(Path file) throws IOException {
        FileRead<GlossaryDso> read =
                yaml.read(GlossaryBinding.INSTANCE, Files.readString(file, StandardCharsets.UTF_8), file.toString());
        assertThat(read).as("%s reads", file).isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<GlossaryDso>) read).value();
    }

    /** The skill read through the store, its body filled in as its companion. */
    private SkillDso skill(Path file) {
        Path directory = file.getParent();
        FilesystemPackStore store = new FilesystemPackStore(directory.getParent(), yaml);
        PackDirectory pack = PackDirectory.of(directory);
        String id = file.getFileName().toString().replace(".skill.yaml", "");
        return store.readOne(pack, SkillBinding.INSTANCE, id)
                .orElseThrow(() -> new AssertionError(file + " does not read with its body"));
    }

    /** A fresh pack holding the one skill, written with its body through the store. */
    private Path writeSkill(SkillDso dso) throws IOException {
        Path pack =
                Files.createDirectories(Files.createTempDirectory(host, "w-").resolve(PROBE.value()));
        store(pack).write(PROBE_DIRECTORY, SkillBinding.INSTANCE, dso);
        return pack;
    }

    /** The store a pack directory named after {@link #PROBE} is addressed through. */
    private FilesystemPackStore store(Path pack) {
        return new FilesystemPackStore(pack.getParent(), yaml);
    }

    private static GlossaryDso dropSeeAlso(GlossaryDso dso) {
        return new GlossaryDso(
                dso.schemaVersion(),
                dso.id(),
                dso.name(),
                dso.description(),
                dso.tags(),
                dso.appliesTo(),
                dso.terms().stream()
                        .map(term -> new GlossaryDso.Term(
                                term.id(),
                                term.name(),
                                term.definition(),
                                term.examples(),
                                term.aliases(),
                                term.avoid(),
                                term.status(),
                                List.of()))
                        .toList());
    }

    private static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(ExamplePacks.packsDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }
}
