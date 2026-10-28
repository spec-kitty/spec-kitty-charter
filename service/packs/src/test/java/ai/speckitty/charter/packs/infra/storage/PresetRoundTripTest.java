package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Presets through the whole-pack store (#195, ADR-0043): read into the {@code Pack}, written back at
 * {@code presets/<id>.yaml}, and equal by meaning, the must-have mark included. Each refusal is paired with a clean
 * control on the same one-preset pack.
 */
@DisplayName("Preset round trip (#195, ADR-0043)")
class PresetRoundTripTest {

    private static final String FIXTURE = "presets-pack";
    private static final String PACK_YAML =
            "pack_id: 01M60H1APRESETSPACK0000000\npack_version: 0.1.0\nname: one-preset\npack_level: project\n";
    private static final String PRESET =
            "schema_version: '1.0'\nid: p\ndescription: d\nactivated_tactics:\n  - id: t\n";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    @Test
    @DisplayName("every preset is read, written at presets/<id>.yaml and reloads equal, its files equal by meaning")
    void presetsRoundTripByMeaning(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        WholePackStore.LoadedPack loaded = store.load(pack);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().presets())
                .extracting(Preset::id)
                .containsExactly("commented", "convenience", "governing");
        assertThat(loaded.unmodelled()).noneMatch(file -> file.startsWith(PresetFiles.PRESETS));
        Preset governing = loaded.pack().presets().get(2);
        assertThat(governing.required())
                .containsEntry(ArtifactKind.DIRECTIVE, List.of("DIRECTIVE_001"))
                .containsEntry(ArtifactKind.TACTIC, List.of("acceptance-test-first"))
                .containsEntry(ArtifactKind.AGENT_PROFILE, List.of("implementer"));
        assertThat(governing.requiredMissionTypes()).containsExactly("software-dev");

        Path target = host.resolve("written").resolve(FIXTURE);
        store.write(loaded, target);
        WholePackStore.LoadedPack reloaded = store.load(target);

        assertThat(reloaded.pack().presets()).isEqualTo(loaded.pack().presets());
        assertThat(differences(pack, target)).isEmpty();
    }

    @Test
    @DisplayName("a writer that drops must_have goes red at the item; the faithful writer does not")
    void aWriterThatDropsMustHaveGoesRedAtTheItem(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path faithful = host.resolve("faithful").resolve(FIXTURE);
        Path lossy = host.resolve("lossy").resolve(FIXTURE);

        store.write(loaded, faithful);
        new WholePackStore(new MustHaveDroppingWriter(new YamlSerializationStrategy())).write(loaded, lossy);

        assertThat(differences(pack, faithful)).isEmpty();
        assertThat(differences(pack, lossy))
                .containsExactlyInAnyOrder(
                        "presets/commented.yaml activated_directives[0].must_have",
                        "presets/governing.yaml activated_directives[0].must_have",
                        "presets/governing.yaml activated_tactics[0].must_have",
                        "presets/governing.yaml activated_agent_profiles[0].must_have",
                        "presets/governing.yaml activated_mission_types[0].must_have");
    }

    @Test
    @DisplayName("a pack without presets is written without a presets directory")
    void aPackWithoutPresetsHasNoPresetsDirectory(@TempDir Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("bare"));
        Files.writeString(pack.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Path target = host.resolve("written").resolve("bare");

        store.write(store.load(pack), target);

        assertThat(target.resolve("presets")).doesNotExist();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenPresets")
    @DisplayName(
            "a preset that breaks the shape is refused on its file, naming why; the same pack without the break loads")
    void aBrokenPresetIsRefusedOnItsFile(String name, String stem, String addition, String reason, @TempDir Path host)
            throws IOException {
        String broken = PRESET + addition;
        Path clean = onePresetPack(host.resolve("clean"), "p", PRESET);
        Path refused = onePresetPack(host.resolve("refused"), stem, broken);

        assertThat(store.load(clean).pack().violations()).isEmpty();
        assertThat(store.load(clean).pack().presets()).extracting(Preset::id).containsExactly("p");
        WholePackStore.LoadedPack loaded = store.load(refused);
        assertThat(loaded.pack().presets()).isEmpty();
        assertThat(loaded.pack().violations())
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.file()).isEqualTo("presets/" + stem + ".yaml"))
                .extracting(PackElementViolation::reason)
                .contains(reason);
    }

    @Test
    @DisplayName("an empty preset document is refused on its file; a written one loads")
    void aNullDocumentIsRefused(@TempDir Path host) throws IOException {
        Path clean = onePresetPack(host.resolve("clean"), "p", PRESET);
        Path refused = onePresetPack(host.resolve("refused"), "p", "# nothing but a comment\n");

        assertThat(store.load(clean).pack().violations()).isEmpty();
        assertThat(store.load(refused).pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("presets/p.yaml");
            assertThat(violation.reason()).startsWith("type violated at the document");
        });
    }

    @Test
    @DisplayName("a registration through the real service leaves every preset file byte-identical")
    void aRegistrationLeavesThePresetsByteIdentical(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        Map<String, byte[]> before = presetBytes(pack);
        DomainRepositoryService.Locations locations = () -> List.of(new PackLocation(FIXTURE, PackLevel.PROJECT, pack));
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations, strategy);
        CommitStorePackWriteStore writeStore =
                new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
        PackIdentifier id = new PackIdentifier(FIXTURE);

        new ToolguideRegistrationService(service, writeStore, new PermissiveWritePolicyGate())
                .registerToolguide(id, toolguide(id), true, null);

        assertThat(service.load(id).toolguides())
                .extracting(t -> t.id().value())
                .containsExactly("ripgrep");
        Map<String, byte[]> after = presetBytes(pack);
        assertThat(after).containsOnlyKeys(before.keySet());
        before.forEach((file, bytes) -> assertThat(after.get(file)).as(file).isEqualTo(bytes));
    }

    /** Each break of the shape: its name, the file stem, what is added to a valid preset, and the exact reason. */
    static Stream<Arguments> brokenPresets() {
        return Stream.of(
                Arguments.of(
                        "unknown key name",
                        "p",
                        "name: p\n",
                        "additionalProperties violated at the document: undeclared key 'name'"),
                Arguments.of(
                        "unknown key required_directives",
                        "p",
                        "required_directives:\n  - D\n",
                        "additionalProperties violated at the document: undeclared key 'required_directives'"),
                Arguments.of(
                        "unknown key mission_type_activations",
                        "p",
                        "mission_type_activations:\n  - software-dev\n",
                        "additionalProperties violated at the document: undeclared key 'mission_type_activations'"),
                Arguments.of(
                        "must_have false",
                        "p",
                        "activated_directives:\n  - id: D\n    must_have: false\n",
                        "const violated at /activated_directives/0/must_have"),
                Arguments.of(
                        "empty list", "p", "activated_directives: []\n", "minItems violated at /activated_directives"),
                Arguments.of("id is not stem", "q", "", "preset id 'p' is not its file stem 'q'"),
                Arguments.of(
                        "duplicate id",
                        "p",
                        "activated_directives:\n  - id: D\n  - id: D\n    must_have: true\n",
                        "Constraint breached: Preset 'p': the activation list of directives must name each id once;"
                                + " 'D' is listed twice."));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static Path fixture(Path host) throws IOException, URISyntaxException {
        Path source = Path.of(
                PresetRoundTripTest.class.getResource("/pack-level/" + FIXTURE).toURI());
        Path target = host.resolve(FIXTURE);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return target;
    }

    private static Path onePresetPack(Path directory, String stem, String preset) throws IOException {
        Files.createDirectories(directory.resolve("presets"));
        Files.writeString(directory.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Files.writeString(directory.resolve("presets/" + stem + ".yaml"), preset);
        return directory;
    }

    /** Every difference of every preset file, as {@code <file> <path>}, between a source and a written pack. */
    private static List<String> differences(Path source, Path written) throws IOException {
        List<String> out = new ArrayList<>();
        for (String file : presetBytes(source).keySet()) {
            MeaningComparator.adr0036()
                    .differencesOfTexts(
                            PresetFiles.KIND,
                            Files.readString(source.resolve(file)),
                            Files.readString(written.resolve(file)))
                    .forEach(difference -> out.add(file + " " + difference.path()));
        }
        return out;
    }

    private static Map<String, byte[]> presetBytes(Path pack) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(pack.resolve("presets"))) {
            for (Path file : files.toList()) {
                out.put("presets/" + file.getFileName(), Files.readAllBytes(file));
            }
        }
        return out;
    }

    private static Toolguide toolguide(PackIdentifier pack) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier("ripgrep"))
                .packId(pack)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guidePath("guides/ripgrep.md")
                .summary("How to search.")
                .build();
    }

    /** A writer that loses the must-have mark: the positive control that the comparison sees it. */
    private record MustHaveDroppingWriter(SerializationStrategy delegate) implements SerializationStrategy {

        @Override
        public <D> String write(KindBinding<D, ?> kind, D dso) {
            return delegate.write(kind, dso);
        }

        @Override
        public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
            return delegate.read(kind, text, file);
        }

        @Override
        public String writeDescriptor(PackDescriptorDso descriptor) {
            return delegate.writeDescriptor(descriptor);
        }

        @Override
        public FileRead<PackDescriptorDso> readDescriptor(String text) {
            return delegate.readDescriptor(text);
        }

        @Override
        public String writeGraph(GraphDocument document) {
            return delegate.writeGraph(document);
        }

        @Override
        public FileRead<GraphDocument> readGraph(String text, Shape shape, String file) {
            return delegate.readGraph(text, shape, file);
        }

        @Override
        public String writePreset(PresetDso preset) {
            return delegate.writePreset(preset).replace("  must_have: true\n", "");
        }

        @Override
        public FileRead<PresetDso> readPreset(String text, String file) {
            return delegate.readPreset(text, file);
        }

        @Override
        public String writeManifest(PackManifestDso manifest) {
            return delegate.writeManifest(manifest);
        }

        @Override
        public FileRead<PackManifestDso> readManifest(String text, String file) {
            return delegate.readManifest(text, file);
        }
    }
}
