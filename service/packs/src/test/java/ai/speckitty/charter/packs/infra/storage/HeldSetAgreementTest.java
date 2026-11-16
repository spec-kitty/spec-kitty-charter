package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.WholePackWriteStore.ExcludedFile;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validate mode and the store agree on what a pack holds (#554, open case 6, IC-10): over every planted pack (and each
 * descriptor-less plant again with a descriptor, as the golden judges it), every example pack and a pack that plants
 * every class of file, the files validate mode lists as not held equal the store's {@code notHeld()}, file for file,
 * with the same kind and reason, and validate mode warns {@code file_not_held} for each unmodelled one in the store's
 * words. A retired file is on neither list.
 *
 * <p>Two derivations are pinned here until WP05 replaces them with one census; then this test is anchored to the
 * golden's {@code file_not_held} diagnostics, since one derivation compared with itself proves nothing (FR-016).
 */
@DisplayName("validate mode and the store list the same files as not held (#554, open case 6)")
class HeldSetAgreementTest {

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
    @DisplayName("each pack: validate mode's not-held files are the store's, with their kind and reason")
    void validateModeListsTheFilesTheStoreDoesNotHold(Subject subject, @TempDir Path host) {
        Path pack = subject.copy().apply(host);
        List<NotHeldFile> validated = validated(pack, subject.level());

        Optional<List<NotHeldFile>> stored = stored(pack);

        stored.ifPresent(list -> assertThat(validated)
                .as("%s: validate mode's not-held files against the store's", subject)
                .containsExactlyElementsOf(list));
        assertThat(validated)
                .as("%s: a not-held file is never one a rule maps or retires", subject)
                .allSatisfy(file -> assertThat(PackFileKind.bundled().classify(pack.getFileName() + "/" + file.path()))
                        .isNotInstanceOfAny(PackFileKind.Mapped.class, PackFileKind.Retired.class));
        assertThat(warnings(pack, subject.level()))
                .as("%s: one file_not_held warning per unmodelled file, in the store's words", subject)
                .containsExactlyElementsOf(validated.stream()
                        .filter(file -> file.kind() == NotHeldFile.Kind.UNMODELLED)
                        .map(NotHeldFile::finding)
                        .toList());
    }

    @Test
    @DisplayName("the planted and example packs give the agreement something to compare: a pack the store loads with"
            + " an excluded file, and one with an unmodelled file")
    void theSubjectsCoverBothKinds(@TempDir Path host) throws IOException {
        List<NotHeldFile> seen = new ArrayList<>();
        for (Subject subject : subjects()) {
            Path pack = subject.copy().apply(Files.createDirectories(host.resolve(String.valueOf(seen.size()))));
            stored(pack).ifPresent(seen::addAll);
        }
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
        assertThat(store().load(pack).notHeld())
                .extracting(ExcludedFile::asNotHeld)
                .containsExactlyElementsOf(validated);
    }

    @Test
    @DisplayName("a retired file is refused by name and is on neither not-held list (WP02 review L2)")
    void aRetiredFileIsOnNeitherList(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        write(pack, "PROVENANCE.md", "# Provenance\n");

        assertThat(store().load(pack).notHeld()).extracting(ExcludedFile::file).doesNotContain("PROVENANCE.md");
        assertThat(validated(pack, PackLevel.ORG)).extracting(NotHeldFile::path).doesNotContain("PROVENANCE.md");
        assertThat(PackPolicy.judge(inspect(pack, PackLevel.ORG)).findings())
                .filteredOn(finding -> finding.file().equals("PROVENANCE.md"))
                .extracting(PackFinding::code)
                .doesNotContain(PackFindingCode.FILE_NOT_HELD)
                .isNotEmpty();
    }

    private static List<NotHeldFile> validated(Path pack, PackLevel level) {
        return inspect(pack, level).notHeld();
    }

    private static PackContents inspect(Path pack, PackLevel level) {
        return new FileSystemPackInspector()
                .inspect(
                        new PackRoot(String.valueOf(pack.getFileName()), Optional.of(level), pack.toString()),
                        Scope.FULL);
    }

    private static List<PackFinding> warnings(Path pack, PackLevel level) {
        return PackPolicy.judge(inspect(pack, level)).findings().stream()
                .filter(finding -> finding.code() == PackFindingCode.FILE_NOT_HELD)
                .toList();
    }

    /** The store's not-held list, or empty when the store refuses to load the pack at all. */
    private static Optional<List<NotHeldFile>> stored(Path pack) {
        try {
            return Optional.of(store().load(pack).notHeld().stream()
                    .map(ExcludedFile::asNotHeld)
                    .toList());
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
