package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The census is the one owner of "held" (#554, FR-016, I13, S3): over every planted pack (and each descriptor-less plant
 * again with a descriptor, as the golden judges it) and every example pack, the files the one reader's census lists as
 * not held are the {@code file_not_held} diagnostics of the golden snapshot ({@code golden/surfaces.json}), file for
 * file and in the store's words: the registration's diagnostics when it accepted the pack. Validate mode's warnings
 * are checked against its own golden, {@code :pack-validation}'s {@code ValidateGoldenTest} (#554, WP09). The store's
 * load lists what the census lists. A retired file is on neither list.
 *
 * <p>Open case 9 has a plant for each of its two cases: a {@code policy.yaml} the descriptor does not name is not held,
 * with its own kind and reason, and judged on neither surface; an orphan prompt fragment is not listed as not held and
 * keeps its refusal, {@code prompt_fragment_invalid}.
 *
 * <p>Scope: the planted packs, the example packs and synthetic packs that plant every class of file. A plant whose links
 * its {@code expected.json} makes is left to the golden, which makes them. The scenario packs are built inside Cucumber
 * steps in {@code :deployable}, and no feature plants a nested shard, a {@code charter.yaml} or an excluded file.
 */
@DisplayName("the census's not-held files are the golden's file_not_held diagnostics (#554, open case 6 and 9)")
class HeldSetAgreementTest {

    /** The golden snapshot of every surface (WP01), read once. */
    private static final JsonNode GOLDEN = readGolden();

    private static JsonNode readGolden() {
        try (var in = HeldSetAgreementTest.class.getResourceAsStream("/golden/surfaces.json")) {
            return JsonMapper.builder().build().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C000000000000000FG\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    /** A pack the agreement is checked on: its name, its level, and how a copy of it is made. */
    record Subject(String name, PackLevel level, Function<Path, Path> copy) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Subject> subjects() {
        List<Subject> subjects = new ArrayList<>();
        for (String plant : PlantedPacks.names()) {
            Path files = PlantedPacks.plant(plant).resolve("files");
            if (!Files.isDirectory(files) || plantsLinks(plant)) {
                continue;
            }
            subjects.add(new Subject("planted/" + plant, PackLevel.ORG, host -> PlantedPacks.copyOf(plant, host)));
            if (!Files.exists(files.resolve("pack.yaml"))) {
                subjects.add(new Subject("planted/" + plant + "+pack.yaml", PackLevel.ORG, host -> {
                    Path pack = PlantedPacks.copyOf(plant, host);
                    write(pack, "pack.yaml", DESCRIPTOR);
                    return pack;
                }));
            }
        }
        for (String name : ExamplePacks.names()) {
            subjects.add(new Subject(
                    "example-packs/" + name, ExamplePacks.level(name), host -> ExamplePacks.copyOf(name, host)));
        }
        return List.copyOf(subjects);
    }

    /** A plant whose links are made from its {@code expected.json} is left to the golden, which makes them. */
    private static boolean plantsLinks(String plant) {
        try {
            return Files.readString(PlantedPacks.plant(plant).resolve("expected.json"), StandardCharsets.UTF_8)
                    .contains("\"symlinks\"");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("each pack: the census's not-held files are the golden's file_not_held diagnostics, on both surfaces")
    void theCensusListsTheGoldensNotHeldFiles(Subject subject, @TempDir Path host) {
        Path pack = subject.copy().apply(host);
        List<String> census = validated(pack, subject.level()).stream()
                .map(NotHeldFile::detail)
                .toList();
        JsonNode golden = GOLDEN.get(subject.name());
        assertThat(golden).as("%s has a golden entry", subject).isNotNull();

        JsonNode accepted = golden.get("registration").get("accepted");
        if (accepted != null) {
            assertThat(census)
                    .as("%s: the census's not-held files against the registration's diagnostics", subject)
                    .containsExactlyInAnyOrderElementsOf(notHeld(accepted.get("diagnostics")));
        }
        assertNeverClassified(pack, subject.level(), subject.toString());
        stored(pack)
                .ifPresent(list -> assertThat(list)
                        .as("%s: the store's load lists what the census lists", subject)
                        .containsExactlyElementsOf(validated(pack, subject.level())));
    }

    /** The details of the golden's file_not_held rows, findings or diagnostics. */
    private static List<String> notHeld(JsonNode rows) {
        List<String> details = new ArrayList<>();
        rows.forEach(row -> {
            if ("file_not_held".equals(row.get("code").asString())) {
                details.add(row.get("detail").asString());
            }
        });
        return details;
    }

    @Test
    @DisplayName("the planted and example packs give the agreement something to compare: a pack the store loads with"
            + " an excluded file, and one with an unmodelled file")
    void theSubjectsCoverBothKinds(@TempDir Path host) throws IOException {
        List<NotHeldFile> seen = new ArrayList<>();
        int loaded = 0;
        int copies = 0;
        for (Subject subject : subjects()) {
            // Each subject in a directory of its own: a copy into an earlier subject's directory would merge the trees.
            Path pack = subject.copy().apply(Files.createDirectories(host.resolve(String.valueOf(copies++))));
            Optional<List<NotHeldFile>> stored = stored(pack);
            if (stored.isPresent()) {
                loaded++;
                seen.addAll(stored.get());
            }
        }
        assertThat(loaded)
                .as("the subjects the store loads, so that the comparison is not vacuous")
                .isGreaterThan(20);
        assertThat(seen).extracting(NotHeldFile::kind).contains(NotHeldFile.Kind.EXCLUDED, NotHeldFile.Kind.UNMODELLED);
    }

    @Test
    @DisplayName("a pack with every class of file: excluded YAML, Markdown and hidden files, unmodelled files, a nested"
            + " shard, a charter.yaml, a retired file and a claimed companion")
    void everyClassOfFileAgrees(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        write(pack, "notes.txt", "an author's aid\n");
        write(pack, "charter.yaml", "catalog: {mission: software-dev}\n");
        write(pack, "drg/tactic.graph.yaml", "schema_version: \"1.0\"\nnodes: [{bogus: 1}]\n");
        write(pack, "graph/deep/x.graph.yaml", "not: [valid\n");
        write(pack, "docs/more-notes.md", "# Notes\n");
        write(pack, "missions/README.md", "# Missions\n");
        write(pack, "assets/.DS_Store", "x");
        write(pack, "PROVENANCE.md", "# Provenance\n");

        List<NotHeldFile> validated = validated(pack, PackLevel.ORG);

        assertThat(validated)
                .extracting(NotHeldFile::path)
                .contains(
                        "notes.txt",
                        "charter.yaml",
                        "drg/tactic.graph.yaml",
                        "graph/deep/x.graph.yaml",
                        "docs/more-notes.md",
                        "missions/README.md",
                        "assets/.DS_Store")
                .doesNotContain("PROVENANCE.md");
        assertThat(validated)
                .filteredOn(file -> file.kind() == NotHeldFile.Kind.UNMODELLED)
                .extracting(NotHeldFile::path)
                .containsExactly("charter.yaml", "drg/tactic.graph.yaml", "graph/deep/x.graph.yaml", "notes.txt");
        assertThat(store().load(pack).notHeld()).containsExactlyElementsOf(validated);
    }

    @Test
    @DisplayName("a retired file is refused by name and is on neither not-held list (WP02 review L2)")
    void aRetiredFileIsOnNeitherList(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        write(pack, "PROVENANCE.md", "# Provenance\n");

        assertThat(store().load(pack).notHeld()).extracting(NotHeldFile::path).doesNotContain("PROVENANCE.md");
        assertThat(validated(pack, PackLevel.ORG)).extracting(NotHeldFile::path).doesNotContain("PROVENANCE.md");
        assertThat(findings(pack, PackLevel.ORG))
                .filteredOn(finding -> finding.location().file().equals("PROVENANCE.md"))
                .extracting(Reason::code)
                .doesNotContain(StorageCodes.FILE_NOT_HELD)
                .isNotEmpty();
    }

    @Test
    @DisplayName(
            "open case 9: a policy.yaml the descriptor does not name is not held, with its own kind and reason, and"
                    + " judged on neither surface")
    void anUnnamedPolicyFileIsNotHeldAndJudgedNowhere(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        write(pack, "policy.yaml", "not: [a, valid, policy\n");

        NotHeldFile unnamed = NotHeldFile.unnamedPolicy("policy.yaml");
        assertThat(validated(pack, PackLevel.ORG)).contains(unnamed);
        assertThat(findings(pack, PackLevel.ORG))
                .filteredOn(finding -> finding.location().file().equals("policy.yaml"))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(tuple(StorageCodes.FILE_NOT_HELD, unnamed.detail()));
        assertThat(StagedReads.read(store(), pack).diagnostics())
                .filteredOn(diagnostic -> diagnostic.detail().startsWith("policy.yaml:"))
                .containsExactly(new Diagnostic(Diagnostic.Code.FILE_NOT_HELD, unnamed.detail()));
    }

    @Test
    @DisplayName("open case 9: an orphan prompt fragment is not listed as not held, and keeps its refusal on both"
            + " surfaces")
    void anOrphanFragmentKeepsItsRefusalAndIsNotListedAsNotHeld(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        String orphan = "missions/software-dev/enhancements/stray.prompt-fragment.md";
        write(pack, orphan, "## Stray\n\nNobody names this.\n");

        assertThat(validated(pack, PackLevel.ORG)).extracting(NotHeldFile::path).doesNotContain(orphan);
        assertThat(findings(pack, PackLevel.ORG))
                .filteredOn(finding -> finding.location().file().equals(orphan))
                .extracting(Reason::code)
                .containsExactly(PackCodes.PROMPT_FRAGMENT_INVALID);
        assertThatThrownBy(() -> StagedReads.contents(store(), pack))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.violations())
                                .filteredOn(violation -> violation.pointer().equals(orphan))
                                .extracting(RefusalViolation::code)
                                .containsExactly(PackCodes.PROMPT_FRAGMENT_INVALID.value()));
    }

    @Test
    @DisplayName("open case 9 (WP05 review M1): a companion no artifact names, an orphan guide or step prompt, is not"
            + " held, with its own kind and reason; both surfaces warn about it, and the registration removes it")
    void anOrphanCompanionIsNotHeld(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        String guide = "toolguides/orphan.guide.md";
        String prompt = "missions/software-dev/steps/orphan.prompt.md";
        write(pack, guide, "no heading, and no toolguide names it\n");
        write(pack, prompt, "---\nunclosed: [\n");

        List<NotHeldFile> census = validated(pack, PackLevel.BUILT_IN);
        assertThat(census).contains(NotHeldFile.unnamedCompanion(guide), NotHeldFile.unnamedCompanion(prompt));
        assertThat(findings(pack, PackLevel.BUILT_IN))
                .as("validate mode warns and judges nothing in either")
                .filteredOn(finding -> finding.location().file().equals(guide)
                        || finding.location().file().equals(prompt))
                .extracting(Reason::code, Reason::detail)
                .containsExactlyInAnyOrder(
                        tuple(
                                StorageCodes.FILE_NOT_HELD,
                                NotHeldFile.unnamedCompanion(guide).detail()),
                        tuple(
                                StorageCodes.FILE_NOT_HELD,
                                NotHeldFile.unnamedCompanion(prompt).detail()));
        PackRegistrationService.ReadUpload read = StagedReads.read(store(), pack);
        assertThat(read.diagnostics())
                .filteredOn(diagnostic -> diagnostic.detail().startsWith(guide + ":")
                        || diagnostic.detail().startsWith(prompt + ":"))
                .hasSize(2);
        store().removeNotHeld(new StagedPack(pack), read.read().notHeld());
        assertThat(pack.resolve(guide)).doesNotExist();
        assertThat(pack.resolve(prompt)).doesNotExist();
    }

    /**
     * An unnamed not-held file, a policy file or a companion nothing names though a rule maps it (open case 9), is never
     * one the read classifies: it is listed once, as not held. Any other not-held file is one no rule maps or retires.
     */
    private static void assertNeverClassified(Path pack, PackLevel level, String subject) {
        PackReadout read = inspect(pack, level);
        Set<String> classified = new HashSet<>();
        read.files().forEach(file -> classified.add(file.file()));
        assertThat(read.notHeld())
                .filteredOn(file -> file.kind() == NotHeldFile.Kind.UNNAMED)
                .as("%s: an unnamed file is never one the read classifies", subject)
                .allSatisfy(file -> assertThat(classified).doesNotContain(file.path()));
        assertThat(read.notHeld())
                .filteredOn(file -> file.kind() != NotHeldFile.Kind.UNNAMED)
                .as("%s: a not-held file other than an unnamed one is never one a rule maps or retires", subject)
                .allSatisfy(file -> assertThat(PackFileKind.bundled().classify(pack.getFileName() + "/" + file.path()))
                        .isNotInstanceOfAny(PackFileKind.Mapped.class, PackFileKind.Retired.class));
    }

    @Test
    @DisplayName(
            "open case 9: each unnamed file, a policy file, a guide and a step prompt nothing names, is listed once,"
                    + " as not held, and never as classified")
    void anUnnamedFileIsNeverClassified(@TempDir Path host) throws IOException {
        Path org = ExamplePacks.copyOf(ExamplePacks.ORG, host.resolve("org"));
        write(org, "policy.yaml", "schema_version: 1\npins: []\n");
        Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host.resolve("built-in"));
        write(builtIn, "toolguides/orphan.guide.md", "# Orphan\n");
        write(builtIn, "missions/software-dev/steps/orphan.prompt.md", "# Orphan\n");

        assertThat(inspect(org, PackLevel.ORG).notHeld()).contains(NotHeldFile.unnamedPolicy("policy.yaml"));
        assertThat(inspect(builtIn, PackLevel.BUILT_IN).notHeld())
                .contains(
                        NotHeldFile.unnamedCompanion("toolguides/orphan.guide.md"),
                        NotHeldFile.unnamedCompanion("missions/software-dev/steps/orphan.prompt.md"));
        assertNeverClassified(org, PackLevel.ORG, "org with an unnamed policy file");
        assertNeverClassified(builtIn, PackLevel.BUILT_IN, "built-in with an orphan guide and step prompt");
    }

    private static List<NotHeldFile> validated(Path pack, PackLevel level) {
        return inspect(pack, level).notHeld();
    }

    private static PackReadout inspect(Path pack, PackLevel level) {
        return new FileSystemPackInspector()
                .read(new PackRoot(String.valueOf(pack.getFileName()), Optional.of(level), pack.toString()));
    }

    /** The one read's findings, which validate mode and a registration judge. */
    private static List<Reason> findings(Path pack, PackLevel level) {
        return inspect(pack, level).rationale().failures();
    }

    /** The store's not-held list, or empty when the store refuses to load the pack at all. */
    private static Optional<List<NotHeldFile>> stored(Path pack) {
        try {
            return Optional.of(store().load(pack).notHeld().stream().toList());

        } catch (CharterRefusal refused) {
            return Optional.empty();
        }
    }

    private static WholePackStore store() {
        return new WholePackStore(new YamlSerializationStrategy());
    }

    private static void write(Path pack, String relative, String text) {
        try {
            Path file = pack.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
