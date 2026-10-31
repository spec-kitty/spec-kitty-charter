package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The companion-file seam of {@link FilesystemPackStore} (ADR-0039), proved with a test-only binding before any real
 * kind uses it. The fixture stores a {@code *.tactic.yaml} file that may name one companion note; a stub strategy
 * reads and writes its two lines, so neither the bundled schemas nor the YAML formats are involved. A companion
 * conflict (taken, named by another artifact, differs only in case) is a {@code registration_conflict} and a path the
 * rule refuses a {@code validation_failed}: client errors the API answers 409 and 422, never {@code pack_unreadable}.
 */
@DisplayName("Companion files are read, contained and upserted with their artifact (ADR-0039)")
class CompanionFilesTest {

    private static final PackIdentifier PACK = new PackIdentifier("p");
    private static final String PACK_YAML = "pack_id: p\npack_version: 1.0.0\nname: p\npack_level: org\n";

    @TempDir
    Path root;

    private Path pack;
    private FilesystemPackStore store;

    @BeforeEach
    void setUp() throws IOException {
        pack = Files.createDirectories(root.resolve(PACK.value()));
        store = new FilesystemPackStore(root, new FixtureStrategy());
    }

    @Test
    @DisplayName("an artifact is read with its companion's text, byte for byte")
    void readsTheCompanionText() throws IOException {
        String text = "line one\r\nline two — no final newline";
        artifact("tactics/a.tactic.yaml", "a", "a.note.md");
        Files.writeString(pack.resolve("tactics/a.note.md"), text, StandardCharsets.UTF_8);

        assertThat(store.readAll(PACK, Fixture.INSTANCE))
                .containsExactly(new FixtureDso("a", Optional.of("a.note.md"), Optional.of(text)));
        assertThat(store.readOne(PACK, Fixture.INSTANCE, "a"))
                .contains(new FixtureDso("a", Optional.of("a.note.md"), Optional.of(text)));
    }

    @Test
    @DisplayName("an artifact naming no companion reads as before")
    void anArtifactWithoutCompanionsIsUnchanged() throws IOException {
        artifact("a.tactic.yaml", "a", null);

        assertThat(store.readAll(PACK, Fixture.INSTANCE))
                .containsExactly(new FixtureDso("a", Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("each unreadable companion is a store violation naming its path, and its artifact is left out")
    void unreadableCompanionsAreViolations() throws IOException {
        Files.writeString(pack.resolve("pack.yaml"), PACK_YAML);
        artifact("missing.tactic.yaml", "missing", "gone.md");
        artifact("escape.tactic.yaml", "escape", "../outside.md");
        Files.writeString(root.resolve("outside.md"), "x");
        artifact("absolute.tactic.yaml", "absolute", "/etc/hostname");
        artifact("yaml.tactic.yaml", "yaml", "pack.yaml");
        artifact("slash.tactic.yaml", "slash", "pack.yaml/");
        artifact("dot.tactic.yaml", "dot", "escape.tactic.yaml/.");
        artifact("link.tactic.yaml", "link", "link.md");
        Files.writeString(pack.resolve("target.md"), "x");
        Files.createSymbolicLink(pack.resolve("link.md"), pack.resolve("target.md"));
        Path outside = Files.createDirectories(root.resolve("elsewhere"));
        Files.writeString(outside.resolve("note.md"), "x");
        Files.createSymbolicLink(pack.resolve("linked"), outside);
        artifact("linkdir.tactic.yaml", "linkdir", "linked/note.md");
        artifact("binary.tactic.yaml", "binary", "binary.md");
        Files.write(pack.resolve("binary.md"), new byte[] {(byte) 0xC3, (byte) 0x28});
        artifact("backslash.tactic.yaml", "backslash", "a\\..\\x.md");
        artifact("first.tactic.yaml", "first", "shared.md");
        artifact("second.tactic.yaml", "second", "sub/../shared.md"); // the same file, named again: one owner
        Files.writeString(pack.resolve("shared.md"), "x");

        FilesystemPackStore.PackRead read = store.read(PACK, List.of(Fixture.INSTANCE));

        assertThat(read.dsos(Fixture.INSTANCE)).extracting(FixtureDso::id).containsExactly("first");
        assertThat(read.violations())
                .allSatisfy(violation -> assertThat(violation.kind()).isEqualTo("companion"))
                .extracting(violation -> violation.file() + ": " + violation.reason())
                .containsExactlyInAnyOrder(
                        "absolute.tactic.yaml: /etc/hostname: leaves the pack",
                        "backslash.tactic.yaml: a\\..\\x.md: is not a valid path",
                        "binary.tactic.yaml: binary.md: is not valid UTF-8",
                        "dot.tactic.yaml: escape.tactic.yaml/.: is a YAML file",
                        "escape.tactic.yaml: ../outside.md: leaves the pack",
                        "link.tactic.yaml: link.md: is not a regular file",
                        "linkdir.tactic.yaml: linked/note.md: leaves the pack",
                        "missing.tactic.yaml: gone.md: missing",
                        "second.tactic.yaml: sub/../shared.md: named by two artifacts",
                        "slash.tactic.yaml: pack.yaml/: is a YAML file",
                        "yaml.tactic.yaml: pack.yaml: is a YAML file");
    }

    @Test
    @DisplayName("two artifacts naming one companion: the second is refused, case ignored")
    void aCompanionHasOneOwner() throws IOException {
        Files.writeString(pack.resolve("pack.yaml"), PACK_YAML);
        artifact("a.tactic.yaml", "a", "shared.md");
        artifact("b.tactic.yaml", "b", "SHARED.md");
        Files.writeString(pack.resolve("shared.md"), "x");
        Files.writeString(pack.resolve("SHARED.md"), "y"); // a case-insensitive file system folds the two

        FilesystemPackStore.PackRead read = store.read(PACK, List.of(Fixture.INSTANCE));

        assertThat(read.dsos(Fixture.INSTANCE)).extracting(FixtureDso::id).containsExactly("a");
        assertThat(read.violations())
                .extracting(violation -> violation.file() + ": " + violation.reason())
                .containsExactly("b.tactic.yaml: SHARED.md: named by two artifacts");
    }

    @Test
    @DisplayName("a new artifact is written with its companion beside it")
    void upsertCreatesTheCompanion() throws IOException {
        store.write(PACK, Fixture.INSTANCE, new FixtureDso("n", Optional.of("n.note.md"), Optional.of("body\n")));

        assertThat(Files.readString(pack.resolve("n.tactic.yaml"))).isEqualTo("id: n\nnote_path: n.note.md\n");
        assertThat(Files.readString(pack.resolve("n.note.md"))).isEqualTo("body\n");
        assertThat(store.readOne(PACK, Fixture.INSTANCE, "n"))
                .as("found again where a load finds it")
                .contains(new FixtureDso("n", Optional.of("n.note.md"), Optional.of("body\n")));
    }

    @Test
    @DisplayName("a new artifact never writes its companion over an existing file")
    void upsertRefusesATakenCompanion() throws IOException {
        Files.writeString(pack.resolve("taken.md"), "someone else's");

        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("n", Optional.of("taken.md"), Optional.of("mine"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessageContaining("companion taken.md is taken");
        assertThat(Files.readString(pack.resolve("taken.md"))).isEqualTo("someone else's");
        assertThat(pack.resolve("n.tactic.yaml")).doesNotExist();
    }

    @Test
    @DisplayName("a replaced artifact never takes over another file, not even through a path that normalizes to YAML")
    void upsertNeverReplacesAnotherFile() throws IOException {
        Files.writeString(pack.resolve("pack.yaml"), PACK_YAML);
        artifact("a.tactic.yaml", "a", "a.md");
        Files.writeString(pack.resolve("a.md"), "mine");
        Files.writeString(pack.resolve("README.md"), "the pack's own");

        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("a", Optional.of("README.md"), Optional.of("x"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessageContaining("companion README.md is taken");
        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("a", Optional.of("pack.yaml/"), Optional.of("x"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessageContaining("companion pack.yaml/ is a YAML file");

        assertThat(Files.readString(pack.resolve("README.md"))).isEqualTo("the pack's own");
        assertThat(Files.readString(pack.resolve("pack.yaml"))).isEqualTo(PACK_YAML);
        assertThat(Files.readString(pack.resolve("a.md"))).isEqualTo("mine");
    }

    @Test
    @DisplayName("an upsert never names a companion another artifact owns, case ignored, as the read would refuse it")
    void upsertRefusesAnotherArtifactsCompanion() throws IOException {
        artifact("a.tactic.yaml", "a", "body.md");
        Files.writeString(pack.resolve("body.md"), "a's");

        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("b", Optional.of("Body.md"), Optional.of("b's"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessageContaining("companion Body.md is named by another artifact");
        assertThat(pack.resolve("Body.md")).doesNotExist();
        assertThat(pack.resolve("b.tactic.yaml")).doesNotExist();
    }

    @Test
    @DisplayName("a companion renamed only in case is refused: it would orphan the old file or replace the new one")
    void upsertRefusesACaseOnlyRename() throws IOException {
        artifact("a.tactic.yaml", "a", "body.md");
        Files.writeString(pack.resolve("body.md"), "before");

        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("a", Optional.of("Body.md"), Optional.of("after"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessageContaining("companion Body.md differs only in case from body.md");
        assertThat(Files.readString(pack.resolve("body.md"))).isEqualTo("before");
    }

    @Test
    @DisplayName("a replaced artifact whose companion moved removes the old one and writes the new")
    void upsertRemovesADroppedCompanion() throws IOException {
        artifact("tactics/a.tactic.yaml", "a", "old.md");
        Files.writeString(pack.resolve("tactics/old.md"), "old");

        store.write(PACK, Fixture.INSTANCE, new FixtureDso("a", Optional.of("new.md"), Optional.of("new")));

        assertThat(pack.resolve("tactics/old.md")).doesNotExist();
        assertThat(Files.readString(pack.resolve("tactics/new.md"))).isEqualTo("new");
        assertThat(Files.readString(pack.resolve("tactics/a.tactic.yaml"))).contains("note_path: new.md");
    }

    @Test
    @DisplayName("a replaced artifact that keeps its companion rewrites it in place")
    void upsertRewritesAKeptCompanion() throws IOException {
        artifact("a.tactic.yaml", "a", "a.md");
        Files.writeString(pack.resolve("a.md"), "before");

        store.write(PACK, Fixture.INSTANCE, new FixtureDso("a", Optional.of("a.md"), Optional.of("after")));

        assertThat(Files.readString(pack.resolve("a.md"))).isEqualTo("after");
    }

    @Test
    @DisplayName("a companion with no content, or one breaking the path rule, is refused before anything is written")
    void upsertRefusesBadCompanions() throws IOException {
        assertThatThrownBy(() -> store.write(
                        PACK,
                        Fixture.INSTANCE,
                        new FixtureDso("n", Optional.of("first.md,second.md/.."), Optional.of("x"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessageContaining("companion second.md/.. leaves the pack");
        assertThat(pack.resolve("first.md"))
                .as("the valid first companion is not written")
                .doesNotExist();
        assertThat(pack.resolve("n.tactic.yaml")).doesNotExist();

        assertThatThrownBy(() ->
                        store.write(PACK, Fixture.INSTANCE, new FixtureDso("n", Optional.of("n.md"), Optional.empty())))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("companion n.md has no content");
        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("n", Optional.of("../n.md"), Optional.of("x"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessageContaining("companion ../n.md leaves the pack");
        assertThatThrownBy(() -> store.write(
                        PACK, Fixture.INSTANCE, new FixtureDso("n", Optional.of("x.tactic.yaml"), Optional.of("x"))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessageContaining("companion x.tactic.yaml is a YAML file");
        assertThat(pack.resolve("n.tactic.yaml")).doesNotExist();
        assertThat(root.resolve("n.md")).doesNotExist();
    }

    @Test
    @DisplayName("every bound kind that names companions says so, so its old companions are looked up and removed")
    void boundKindsDeclareTheirCompanions() throws NoSuchMethodException {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            boolean overrides =
                    binding.getClass().getMethod("companions", Object.class).getDeclaringClass() != KindBinding.class;
            assertThat(binding.hasCompanions()).as(binding.kind().token()).isEqualTo(overrides);
        }
        assertThat(Fixture.INSTANCE.hasCompanions()).isTrue();
    }

    @Test
    @DisplayName("a bound kind without companions reads exactly as its strategy reads it (positive control)")
    void aKindWithoutCompanionsIsUnchanged() throws IOException {
        Path tactics =
                Path.of(System.getProperty("charter.repositoryRoot", "../..")).resolve("packs/internal/tactics");
        Path source;
        try (java.util.stream.Stream<Path> files = Files.list(tactics)) {
            source = files.filter(f -> f.toString().endsWith(".tactic.yaml"))
                    .sorted()
                    .findFirst()
                    .orElseThrow();
        }
        Files.copy(source, pack.resolve(source.getFileName()));
        SerializationStrategy yaml = new YamlSerializationStrategy();
        FilesystemPackStore real = new FilesystemPackStore(root, yaml);

        FileRead<TacticDso> direct =
                yaml.read(TacticBinding.INSTANCE, Files.readString(source), String.valueOf(source.getFileName()));

        assertThat(direct).isInstanceOf(FileRead.Read.class);
        assertThat(real.readAll(PACK, TacticBinding.INSTANCE))
                .containsExactly(((FileRead.Read<TacticDso>) direct).value());
    }

    // ---- fixture --------------------------------------------------------------------------------------------------

    private void artifact(String file, String id, @org.jspecify.annotations.Nullable String notePath)
            throws IOException {
        Path path = pack.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "id: " + id + "\n" + (notePath == null ? "" : "note_path: " + notePath + "\n"));
    }

    /**
     * The fixture's persistence shape.
     *
     * @param id the id
     * @param notePath the companion path it declares
     * @param note the companion's text, once read
     */
    record FixtureDso(String id, Optional<String> notePath, Optional<String> note) {}

    /** A binding over {@code *.tactic.yaml} files whose artifacts may name one companion note. */
    enum Fixture implements KindBinding<FixtureDso, Tactic> {
        INSTANCE;

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.TACTIC;
        }

        @Override
        public Class<Tactic> entityType() {
            return Tactic.class;
        }

        @Override
        public Class<FixtureDso> dsoType() {
            return FixtureDso.class;
        }

        @Override
        public String idOf(FixtureDso dso) {
            return dso.id();
        }

        @Override
        public Tactic toEntity(PackIdentifier pack, FixtureDso dso) {
            throw new UnsupportedOperationException("the fixture has no entity");
        }

        @Override
        public FixtureDso toDso(Tactic entity) {
            throw new UnsupportedOperationException("the fixture has no entity");
        }

        @Override
        public boolean hasCompanions() {
            return true;
        }

        /** Each comma-separated entry of the note path is one companion, all holding the note. */
        @Override
        public List<Companion> companions(FixtureDso dso) {
            return dso.notePath().stream()
                    .flatMap(paths -> Arrays.stream(paths.split(",", -1)))
                    .map(path -> new Companion(path, dso.note().map(CompanionContent.Text::new)))
                    .toList();
        }

        /** The note is the first companion's text. */
        @Override
        public FixtureDso withCompanions(FixtureDso dso, Map<String, CompanionContent> contents) {
            return new FixtureDso(
                    dso.id(),
                    dso.notePath(),
                    dso.notePath()
                            .map(paths -> paths.split(",", -1)[0])
                            .map(contents::get)
                            .map(Companions::text));
        }
    }

    /** Reads and writes the fixture's two lines; nothing else of the strategy is used. */
    static final class FixtureStrategy implements SerializationStrategy {

        @Override
        public <D> String write(KindBinding<D, ?> kind, D dso) {
            FixtureDso fixture = (FixtureDso) dso;
            return "id: " + fixture.id() + "\n"
                    + fixture.notePath().map(p -> "note_path: " + p + "\n").orElse("");
        }

        @Override
        public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
            String id = "";
            Optional<String> notePath = Optional.empty();
            for (String line : text.split("\n", -1)) {
                if (line.startsWith("id: ")) {
                    id = line.substring(4);
                } else if (line.startsWith("note_path: ")) {
                    notePath = Optional.of(line.substring(11));
                }
            }
            return new FileRead.Read<>(file, kind.dsoType().cast(new FixtureDso(id, notePath, Optional.empty())));
        }

        @Override
        public String writeDescriptor(PackDescriptorDso descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileRead<PackDescriptorDso> readDescriptor(String text) {
            return new YamlSerializationStrategy().readDescriptor(text);
        }

        @Override
        public String writeGraph(GraphDocument document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileRead<GraphDocument> readGraph(String text, Shape shape, String file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String writePreset(PresetDso preset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileRead<PresetDso> readPreset(String text, String file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String writeManifest(PackManifestDso manifest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileRead<PackManifestDso> readManifest(String text, String file) {
            throw new UnsupportedOperationException();
        }
    }
}
