package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Refused;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pack manifest through the whole-pack seam and the gate (#195, ADR-0042). The fixture's manifest was generated
 * with Python over the source bytes, so a fresh read here also shows the two recipes agree. Its tactic, directive and
 * one preset carry comments the writer drops, so every written file's bytes differ from its source and every
 * {@code content_hash} must be recomputed, not copied.
 */
@DisplayName("Pack manifest round trip (#195, ADR-0042)")
class PackManifestRoundTripTest {

    private static final String FIXTURE = "manifest-pack";
    static final String TACTIC = "tactics/refactoring/refactoring-inline-temp.tactic.yaml";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    // ---- the whole-pack store ------------------------------------------------------------------------------------

    @Test
    @DisplayName("the written manifest is restamped: every content_hash differs from the source's and verifies")
    void theWrittenManifestIsRestampedOverTheWrittenBytes(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path target = host.resolve("written").resolve(FIXTURE);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.unmodelled()).doesNotContain(PackFileSet.MANIFEST);
        PackManifestDso source = loaded.manifest().orElseThrow();
        assertThat(PackManifests.stale(source, path -> bytesOf(pack.resolve(path))))
                .as("the Python-generated manifest verifies under the Java recipes")
                .isEmpty();

        store.write(loaded, target);
        PackManifestDso written = store.load(target).manifest().orElseThrow();

        assertThat(PackManifests.stale(written, path -> bytesOf(target.resolve(path))))
                .isEmpty();
        assertThat(written.manifestHash()).isNotEqualTo(source.manifestHash());
        for (int i = 0; i < source.constituents().size(); i++) {
            assertThat(written.constituents().get(i).contentHash())
                    .as(source.constituents().get(i).path())
                    .isNotEqualTo(source.constituents().get(i).contentHash());
        }
        for (int i = 0; i < source.presets().size(); i++) {
            assertThat(written.presets().get(i).contentHash())
                    .as(source.presets().get(i).path())
                    .isNotEqualTo(source.presets().get(i).contentHash());
        }
        assertThat(MeaningComparator.adr0036()
                        .compareTexts(
                                MeaningComparator.MANIFEST,
                                Files.readString(pack.resolve(PackFileSet.MANIFEST)),
                                Files.readString(target.resolve(PackFileSet.MANIFEST))))
                .as("constituents and presets equal by meaning")
                .isEmpty();
    }

    @Test
    @DisplayName("a manifest that lists a file the write does not hold refuses the write and leaves no target")
    void aListedPathTheWriteDoesNotHoldRefusesTheWrite(@TempDir Path host) throws IOException, URISyntaxException {
        WholePackStore.LoadedPack loaded = store.load(fixture(host));
        PackManifestDso manifest = loaded.manifest().orElseThrow();
        List<PackManifestDso.Constituent> constituents = new ArrayList<>(manifest.constituents());
        constituents.add(new PackManifestDso.Constituent("tactic", "ghost", "ghost.tactic.yaml", "0".repeat(64)));
        WholePackStore.LoadedPack ghostly = new WholePackStore.LoadedPack(
                loaded.pack(),
                loaded.layout(),
                loaded.graphs(),
                loaded.unmodelled(),
                Optional.of(new PackManifestDso(
                        manifest.schemaVersion(), manifest.manifestHash(), constituents, manifest.presets())));
        Path clean = host.resolve("clean").resolve(FIXTURE);
        Path refused = host.resolve("refused").resolve(FIXTURE);

        store.write(loaded, clean);
        assertThatThrownBy(() -> store.write(ghostly, refused))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage())
                            .isEqualTo("pack-manifest.yaml of 01M60H1AMANFESTPACK0000000 lists ghost.tactic.yaml,"
                                    + " which the write does not hold");
                });

        assertThat(clean.resolve(PackFileSet.MANIFEST)).isRegularFile();
        assertThat(refused).doesNotExist();
        try (Stream<Path> left = Files.list(refused.getParent())) {
            assertThat(left).as("no staging residue").isEmpty();
        }
    }

    @Test
    @DisplayName("a stale source content_hash is reported at its constituent; the reader still loads the pack")
    void aStaleSourceHashIsReportedAndLoaded(@TempDir Path host) throws IOException, URISyntaxException {
        Path fresh = fixture(host.resolve("fresh"));
        Path edited = fixture(host.resolve("edited"));
        Files.writeString(edited.resolve(TACTIC), Files.readString(edited.resolve(TACTIC)) + "# edited\n");

        WholePackStore.LoadedPack freshPack = store.load(fresh);
        WholePackStore.LoadedPack editedPack = store.load(edited);

        assertThat(PackManifests.stale(freshPack.manifest().orElseThrow(), path -> bytesOf(fresh.resolve(path))))
                .isEmpty();
        assertThat(editedPack.pack().violations())
                .as("the reader never checks the derived fields")
                .isEmpty();
        assertThat(PackManifests.stale(editedPack.manifest().orElseThrow(), path -> bytesOf(edited.resolve(path))))
                .containsExactly("constituents{" + TACTIC + "}.content_hash");
    }

    @Test
    @DisplayName("an unreadable manifest is a violation on the aggregate with the store's exact reasons")
    void anUnreadableManifestIsAViolation(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        Path manifest = pack.resolve(PackFileSet.MANIFEST);
        Files.writeString(manifest, Files.readString(manifest) + "charter: null\n");

        WholePackStore.LoadedPack loaded = store.load(pack);

        assertThat(loaded.manifest()).isEmpty();
        assertThat(loaded.unmodelled()).doesNotContain(PackFileSet.MANIFEST);
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(PackFileSet.MANIFEST);
            assertThat(violation.reason())
                    .isEqualTo("additionalProperties violated at the document: undeclared key 'charter'");
        });
    }

    @Test
    @DisplayName("a registration leaves the manifest byte-identical: membership is carried until #110 restamps it")
    void aRegistrationLeavesTheManifestAsItWas(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        byte[] before = Files.readAllBytes(pack.resolve(PackFileSet.MANIFEST));
        DomainRepositoryService.Locations locations = () -> List.of(new PackLocation(FIXTURE, PackLevel.PROJECT, pack));
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations, strategy);
        CommitStorePackWriteStore writeStore =
                new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
        PackIdentifier id = new PackIdentifier(FIXTURE);

        new ToolguideRegistrationService(
                        service,
                        writeStore,
                        new PermissiveWritePolicyGate(),
                        uri -> {
                            throw new AssertionError("no guide is fetched: the guide is sent as content");
                        },
                        new MarkdownGuideFormat())
                .registerToolguide(
                        id,
                        new ToolguideIdentifier("ripgrep"),
                        "Use ripgrep",
                        new ToolguideRegistrationService.GuideSource.Content("## Overview\n\nHow to search."),
                        guide -> Toolguide.builder()
                                .id(new ToolguideIdentifier("ripgrep"))
                                .packId(id)
                                .schemaVersion("1.0")
                                .tool("ripgrep")
                                .title("Use ripgrep")
                                .guide(guide)
                                .summary("How to search.")
                                .build(),
                        true,
                        null);

        assertThat(service.load(id).toolguides())
                .extracting(t -> t.id().value())
                .containsExactly("ripgrep");
        assertThat(Files.readAllBytes(pack.resolve(PackFileSet.MANIFEST)))
                .as("the known gap: the new toolguide is not listed until #110")
                .isEqualTo(before);
        assertThat(store.load(pack).manifest().orElseThrow().constituents())
                .extracting(PackManifestDso.Constituent::id)
                .doesNotContain("ripgrep");
    }

    // ---- the gate ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the gate writes the restamped manifest, fresh over the paired written files and equal by meaning")
    void theGateWritesARestampedManifest(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        Path target = host.resolve("out").resolve(FIXTURE);

        Map<String, Outcome> outcomes = gate(new YamlSerializationStrategy(), pack, target);

        assertThat(outcomes.get(PackFileSet.MANIFEST)).isEqualTo(new Written(PackFileSet.MANIFEST));
        assertThat(writtenStale(target, outcomes)).isEmpty();
        assertThat(MeaningComparator.adr0036()
                        .compareTexts(
                                MeaningComparator.MANIFEST,
                                Files.readString(pack.resolve(PackFileSet.MANIFEST)),
                                Files.readString(target.resolve(PackFileSet.MANIFEST))))
                .isEmpty();
    }

    @Test
    @DisplayName("a writer that keeps the source's hashes goes red at every content_hash; the faithful writer does not")
    void aWriterThatKeepsStaleHashesGoesRed(@TempDir Path host) throws IOException, URISyntaxException {
        Path pack = fixture(host);
        Path faithful = host.resolve("faithful").resolve(FIXTURE);
        Path stale = host.resolve("stale").resolve(FIXTURE);

        Map<String, Outcome> faithfulOutcomes = gate(new YamlSerializationStrategy(), pack, faithful);
        Map<String, Outcome> staleOutcomes =
                gate(new StaleManifestWriter(new YamlSerializationStrategy(), pack), pack, stale);

        assertThat(writtenStale(faithful, faithfulOutcomes)).isEmpty();
        // The copied manifest_hash still verifies against the copied fields: each stale file is named, once.
        assertThat(writtenStale(stale, staleOutcomes))
                .containsExactly(
                        "constituents{directives/018-doctrine-versioning.directive.yaml}.content_hash",
                        "constituents{" + TACTIC + "}.content_hash",
                        "presets{presets/convenience.yaml}.content_hash",
                        "presets{presets/governing.yaml}.content_hash");
    }

    @Test
    @DisplayName("the gate refuses an unreadable manifest with the store's reasons, and one listing an unwritten file")
    void theGateRefusesAnUnreadableManifestAndAnUnwrittenListedFile(@TempDir Path host)
            throws IOException, URISyntaxException {
        Path unreadable = fixture(host.resolve("unreadable"));
        Path manifest = unreadable.resolve(PackFileSet.MANIFEST);
        Files.writeString(manifest, Files.readString(manifest) + "charter: null\ngenerated_by: x\n");
        Path unwritten = fixture(host.resolve("unwritten"));
        Files.createDirectories(unwritten.resolve("procedures"));
        Files.writeString(unwritten.resolve("procedures/p.procedure.yaml"), "id: p\n");
        Path listed = unwritten.resolve(PackFileSet.MANIFEST);
        Files.writeString(
                listed,
                Files.readString(listed)
                        .replace(
                                "presets:\n",
                                "  - kind: procedure\n    id: p\n    path: procedures/p.procedure.yaml\n"
                                        + "    content_hash: " + "0".repeat(64) + "\npresets:\n"));

        Map<String, Outcome> refusedUnreadable = gate(
                new YamlSerializationStrategy(),
                unreadable,
                host.resolve("out1").resolve(FIXTURE));
        Map<String, Outcome> refusedUnwritten = gate(
                new YamlSerializationStrategy(), unwritten, host.resolve("out2").resolve(FIXTURE));

        Path binary = fixture(host.resolve("binary"));
        Files.write(binary.resolve(PackFileSet.MANIFEST), new byte[] {(byte) 0xff, (byte) 0xfe, 'x', '\n'});
        assertThat(gate(
                                new YamlSerializationStrategy(),
                                binary,
                                host.resolve("out3").resolve(FIXTURE))
                        .get(PackFileSet.MANIFEST))
                .isEqualTo(new Refused("the file is not valid UTF-8"));
        assertThat(refusedUnreadable.get(PackFileSet.MANIFEST))
                .isEqualTo(new Refused("additionalProperties violated at the document: undeclared key 'charter'; "
                        + "additionalProperties violated at the document: undeclared key 'generated_by'"));
        assertThat(refusedUnwritten.get(PackFileSet.MANIFEST))
                .isEqualTo(new Refused(
                        "pack-manifest.yaml lists procedures/p.procedure.yaml, which the round trip did not write"));
    }

    @Test
    @DisplayName(
            "the gate pairs a constituent by kind token and id, underscores included; a template and a preset by path")
    void theGatePairsByKindTokenAndIdAndPresetsByPath(@TempDir Path target) throws IOException {
        Files.createDirectories(target.resolve("presets"));
        Files.writeString(target.resolve("presets/p.yaml"), "x");
        Files.createDirectories(target.resolve("templates"));
        Files.writeString(target.resolve("templates/t.md"), "x");
        PackManifestDso manifest = new PackManifestDso(
                "1.0",
                "0".repeat(64),
                List.of(
                        new PackManifestDso.Constituent("agent_profile", "a", "agents/a.agent.yaml", "0".repeat(64)),
                        new PackManifestDso.Constituent("glossary_pack", "g", "g/g.glossary.yaml", "0".repeat(64)),
                        new PackManifestDso.Constituent(
                                "mission_step_contract", "m", "m/m.step-contract.yaml", "0".repeat(64)),
                        new PackManifestDso.Constituent("procedure", "p", "p.procedure.yaml", "0".repeat(64)),
                        new PackManifestDso.Constituent("template", "t.md", "templates/t.md", "0".repeat(64)),
                        new PackManifestDso.Constituent("template", "gone.md", "templates/gone.md", "0".repeat(64)),
                        new PackManifestDso.Constituent("tactic", "unwritten", "u.tactic.yaml", "0".repeat(64))),
                List.of(
                        new PackManifestDso.PresetEntry("p", "presets/p.yaml", "0".repeat(64)),
                        new PackManifestDso.PresetEntry("q", "presets/q.yaml", "0".repeat(64))));
        Map<String, String> written = Map.of(
                "agent-profile/a", "a.agent.yaml",
                "glossary-pack/g", "g.glossary.yaml",
                "mission-step-contract/m", "m.step-contract.yaml");

        assertThat(DomainRoundTrip.pairing(manifest, target, written))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "agents/a.agent.yaml", "a.agent.yaml",
                        "g/g.glossary.yaml", "g.glossary.yaml",
                        "m/m.step-contract.yaml", "m.step-contract.yaml",
                        "templates/t.md", "templates/t.md",
                        "presets/p.yaml", "presets/p.yaml"));
    }

    @Test
    @DisplayName(
            "a preset entry that does not name presets/<id>.yaml is refused by the load, the gate and validate mode")
    void aMisplacedPresetEntryIsRefusedOnEverySide(@TempDir Path host) throws IOException, URISyntaxException {
        Path clean = fixture(host.resolve("clean"));
        Path misplaced = fixture(host.resolve("misplaced"));
        Path manifest = misplaced.resolve(PackFileSet.MANIFEST);
        Files.writeString(
                manifest,
                Files.readString(manifest)
                        .replace(
                                "  - id: convenience\n    path: presets/convenience.yaml",
                                "  - id: convenience\n    path: presets/governing.yaml"));
        String reason = "preset entry 'convenience' names presets/governing.yaml, not presets/convenience.yaml";

        assertThat(store.load(clean).pack().violations()).isEmpty();
        WholePackStore.LoadedPack loaded = store.load(misplaced);
        assertThat(loaded.manifest()).isEmpty();
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(PackFileSet.MANIFEST);
            assertThat(violation.reason()).isEqualTo(reason);
        });

        assertThat(gate(
                                new YamlSerializationStrategy(),
                                clean,
                                host.resolve("out/clean").resolve(FIXTURE))
                        .get(PackFileSet.MANIFEST))
                .isEqualTo(new Written(PackFileSet.MANIFEST));
        assertThat(gate(
                                new YamlSerializationStrategy(),
                                misplaced,
                                host.resolve("out/misplaced").resolve(FIXTURE))
                        .get(PackFileSet.MANIFEST))
                .isEqualTo(new Refused(reason));

        assertThat(validateFindings(clean)).isEmpty();
        assertThat(validateFindings(misplaced)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.detail()).endsWith(": 'path' has an unsupported value 'presets/governing.yaml'");
        });
    }

    /** Validate mode's findings on the manifest of a pack directory. */
    private static List<PackFinding> validateFindings(Path pack) {
        PackRoot root = new PackRoot(FIXTURE, Optional.of(PackLevel.PROJECT), pack.toString());
        return PackPolicy.judge(new FileSystemPackInspector().inspect(root, PackInspector.Scope.FULL))
                .findings()
                .stream()
                .filter(finding -> finding.file().equals(PackFileSet.MANIFEST))
                .toList();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static Map<String, Outcome> gate(SerializationStrategy strategy, Path pack, Path target)
            throws IOException {
        return new DomainRoundTrip(strategy).roundTrip(pack, target, WholePackCensus.of(pack));
    }

    /**
     * The written manifest's derived fields that do not verify against the written file each listed path paired
     * with.
     */
    private static List<String> writtenStale(Path target, Map<String, Outcome> outcomes) {
        FileRead<PackManifestDso> read = new YamlSerializationStrategy()
                .readManifest(readString(target.resolve(PackFileSet.MANIFEST)), PackFileSet.MANIFEST);
        assertThat(read).isInstanceOf(FileRead.Read.class);
        return PackManifests.stale(
                ((FileRead.Read<PackManifestDso>) read).value(),
                path -> outcomes.get(path) instanceof Written w
                        ? bytesOf(target.resolve(w.writtenPath()))
                        : Optional.empty());
    }

    static Path fixture(Path host) throws IOException, URISyntaxException {
        Path source = Path.of(PackManifestRoundTripTest.class
                .getResource("/pack-level/" + FIXTURE)
                .toURI());
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

    private static Optional<byte[]> bytesOf(Path file) {
        return Files.isRegularFile(file) ? FilesystemPackStore.readBytes(file) : Optional.empty();
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * A writer that keeps the source's derived fields: it writes the manifest as read from {@code source}, not the
     * stamped one it is given. The positive control that the gate's stale check sees a copy.
     */
    record StaleManifestWriter(SerializationStrategy delegate, Path source) implements SerializationStrategy {

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
            return delegate.writePreset(preset);
        }

        @Override
        public FileRead<PresetDso> readPreset(String text, String file) {
            return delegate.readPreset(text, file);
        }

        @Override
        public String writeManifest(PackManifestDso manifest) {
            return readString(source.resolve(PackFileSet.MANIFEST));
        }

        @Override
        public FileRead<PackManifestDso> readManifest(String text, String file) {
            return delegate.readManifest(text, file);
        }
    }
}
