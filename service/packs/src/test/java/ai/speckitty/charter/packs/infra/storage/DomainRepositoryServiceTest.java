package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The domain ports realized by {@link DomainRepositoryService}, driven at entity level over a fixture pack. The pack
 * directory is {@code pack-a} but its real {@code pack_id} is a ULID, so every test also proves that identifiers are
 * resolved through the configured locations rather than taken as directory names.
 */
class DomainRepositoryServiceTest {

    private static final PackIdentifier BY_ID = new PackIdentifier("01M5DEH8ROUNDTRIPFIXTURE0A");
    private static final PackIdentifier BY_NAME = new PackIdentifier("pack-a");
    private static final TacticIdentifier ALPHA = new TacticIdentifier("alpha-tactic");

    private DomainRepositoryService service;

    @BeforeEach
    void configuredOverTheFixturePack(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        PackLocation location = new PackLocation("pack-a", PackLevel.ORG, pack);
        service = new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    /** FR-003 positive control: the present id on the same fixture returns a populated entity. */
    @Test
    void aPresentTacticLoadsAsAPopulatedEntity() {
        Tactic alpha = service.load(BY_ID, ALPHA).orElseThrow();

        assertThat(alpha.id()).isEqualTo(ALPHA);
        assertThat(alpha.packId()).isEqualTo(BY_ID);
        assertThat(alpha.name()).isEqualTo("Alpha tactic");
        assertThat(alpha.steps()).hasSize(2);
        assertThat(alpha.steps().get(0).references()).hasSize(1);
        assertThat(alpha.references()).extracting("name").containsExactly("Gamma", "Delta", "Gamma again");
        assertThat(alpha.purpose()).contains("Prove that every field survives.");
    }

    /** FR-003: an absent id is empty, never thrown; paired with the present control above on the same fixture. */
    @Test
    void anAbsentTacticIsEmptyNotThrown() {
        assertThat(service.load(BY_ID, new TacticIdentifier("no-such-tactic"))).isEmpty();
        assertThat(service.load(BY_ID, ALPHA)).isPresent();
    }

    /** The configured name and the declared pack_id both resolve to the same directory. */
    @Test
    void theConfiguredNameAndTheDeclaredIdResolveTheSamePack() {
        assertThat(service.load(BY_NAME, ALPHA)).isPresent();
        assertThat(service.load(BY_ID, ALPHA)).isPresent();
        assertThat(service.get(BY_ID)).isEqualTo(service.get(BY_NAME));
    }

    /** Resolution refuses an identifier no configured location answers to (control for the two above). */
    @Test
    void anUnconfiguredPackIsRefused() {
        assertThatThrownBy(() -> service.load(new PackIdentifier("unconfigured"), ALPHA))
                .isInstanceOf(CharterRefusal.class);
    }

    /** Entity-level round-trip: save then load returns an equal entity, down to every field. */
    @Test
    void aSavedTacticReloadsEqualToTheEntitySaved() {
        Tactic original = service.load(BY_ID, ALPHA).orElseThrow();

        service.save(original);
        Tactic reloaded = service.load(BY_ID, ALPHA).orElseThrow();

        assertThat(reloaded).isEqualTo(original);
        assertThat(reloaded.packId()).isEqualTo(original.packId());
        assertThat(reloaded.schemaVersion()).isEqualTo(original.schemaVersion());
        assertThat(reloaded.name()).isEqualTo(original.name());
        assertThat(reloaded.steps()).isEqualTo(original.steps());
        assertThat(reloaded.purpose()).isEqualTo(original.purpose());
        assertThat(reloaded.notes()).isEqualTo(original.notes());
        assertThat(reloaded.failureModes()).isEqualTo(original.failureModes());
        assertThat(reloaded.appliesToLanguages()).isEqualTo(original.appliesToLanguages());
        assertThat(reloaded.references()).isEqualTo(original.references());
    }

    /** Mutation control: a change written through the port is visible on reload, so the round-trip is not vacuous. */
    @Test
    void aMutationWrittenThroughThePortIsVisibleOnReload() {
        Tactic original = service.load(BY_ID, ALPHA).orElseThrow();
        Tactic renamed = Tactic.builder()
                .id(original.id())
                .packId(original.packId())
                .schemaVersion(original.schemaVersion().orElseThrow())
                .name("Alpha tactic, renamed")
                .step(original.steps().get(0))
                .build();

        service.save(renamed);
        Tactic reloaded = service.load(BY_ID, ALPHA).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("Alpha tactic, renamed");
        assertThat(reloaded.steps()).hasSize(1);
        assertThat(reloaded.name()).isNotEqualTo(original.name());
    }

    /** The descriptor port: get maps the declared fields, and a changed level written back is read back changed. */
    @Test
    void aDescriptorMutationWrittenThroughThePortIsVisibleOnReload() {
        PackDescriptor before = service.get(BY_ID);
        assertThat(before.declaredLevel()).contains(PackLevel.ORG);
        assertThat(before.paths().activeCharter()).contains("org-charter.yaml");

        PackDescriptor changed = new PackDescriptor(
                before.packId(),
                before.packVersion(),
                before.parentPack(),
                before.name(),
                java.util.Optional.of(PackLevel.PROJECT),
                before.requiresCore(),
                before.paths());
        service.save(BY_ID, changed);

        PackDescriptor after = service.get(BY_ID);
        assertThat(after).isEqualTo(changed);
        assertThat(after.declaredLevel()).contains(PackLevel.PROJECT);
        assertThat(after.paths()).isEqualTo(before.paths());
    }

    /** The aggregating load assembles descriptor and readable tactics; the broken file is left out, not thrown. */
    @Test
    void theAggregatingLoadAssemblesThePackAndLeavesTheBrokenFileOut() {
        Pack pack = service.load(BY_ID);

        assertThat(pack.id()).isEqualTo(BY_ID);
        assertThat(pack.descriptor().name()).isEqualTo("roundtrip-a");
        assertThat(pack.tactics()).extracting(t -> t.id().value()).containsExactly("alpha-tactic", "beta-tactic");
        assertThat(pack.tactics()).allMatch(t -> t.packId().equals(BY_ID));
    }

    /** FR-004: the broken file is reported by Pack.violations(), and it is empty on a clean pack. */
    @Test
    void aBrokenFileSurfacesThroughTheInspectionQuery(@TempDir Path cleanHost) throws IOException {
        List<PackElementViolation> violations = service.load(BY_ID).violations();

        assertThat(violations).isNotEmpty();
        assertThat(violations).allMatch(v -> v.file().equals(BROKEN_FILE));

        Path clean = PackFixtures.copyOf("pack-descriptor-only", cleanHost);
        DomainRepositoryService cleanService = new DomainRepositoryService(
                () -> List.of(new PackLocation("pack-descriptor-only", PackLevel.ORG, clean)),
                new YamlSerializationStrategy());
        assertThat(cleanService.load(new PackIdentifier("pack-descriptor-only")).violations())
                .isEmpty();
    }

    /**
     * NFR-001: resolving a pack by its declared id reads descriptors only, and a point-read opens only the target
     * pack's tactic file. The decoy pack (three tactic files) is scanned for its id but none of its tactics is opened.
     */
    @Test
    void aPointReadNeverOpensAnotherPacksTacticFiles(@TempDir Path host) throws IOException {
        Path decoy = PackFixtures.copyOf("pack-a", host);
        Path target = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(
                target.resolve("only.tactic.yaml"),
                "id: only-tactic\nschema_version: '1.0'\nname: Only\nsteps:\n  - title: s\n");
        List<String> tacticFilesOpened = new ArrayList<>();
        SerializationStrategy yaml = new YamlSerializationStrategy();
        SerializationStrategy counting = new SerializationStrategy() {
            @Override
            public <D> String write(KindBinding<D, ?> kind, D dso) {
                return yaml.write(kind, dso);
            }

            @Override
            public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
                if (kind.kind() == ArtifactKind.TACTIC) {
                    tacticFilesOpened.add(file);
                }
                return yaml.read(kind, text, file);
            }

            @Override
            public String writeDescriptor(PackDescriptorDso descriptor) {
                return yaml.writeDescriptor(descriptor);
            }

            @Override
            public FileRead<PackDescriptorDso> readDescriptor(String text) {
                return yaml.readDescriptor(text);
            }

            @Override
            public String writeGraph(GraphDocument document) {
                return yaml.writeGraph(document);
            }

            @Override
            public FileRead<GraphDocument> readGraph(String text, GraphDocumentReader.Shape shape, String file) {
                return yaml.readGraph(text, shape, file);
            }

            @Override
            public String writePreset(PresetDso preset) {
                return yaml.writePreset(preset);
            }

            @Override
            public FileRead<PresetDso> readPreset(String text, String file) {
                return yaml.readPreset(text, file);
            }

            @Override
            public String writeManifest(PackManifestDso manifest) {
                return yaml.writeManifest(manifest);
            }

            @Override
            public FileRead<PackManifestDso> readManifest(String text, String file) {
                return yaml.readManifest(text, file);
            }
        };
        DomainRepositoryService counted = new DomainRepositoryService(
                () -> List.of(
                        new PackLocation("pack-a", PackLevel.ORG, decoy),
                        new PackLocation("pack-descriptor-only", PackLevel.PROJECT, target)),
                counting);
        PackIdentifier targetId = new PackIdentifier("01M5DEH8ROUNDTRIPFIXTURE0B");

        assertThat(counted.get(targetId).name()).isEqualTo("roundtrip-descriptor-only");
        assertThat(tacticFilesOpened)
                .as("descriptor resolution opens no tactic file")
                .isEmpty();

        assertThat(counted.load(targetId, new TacticIdentifier("only-tactic"))).isPresent();
        assertThat(tacticFilesOpened).containsExactly("only.tactic.yaml");
    }

    /** The storage-level {@code tacticsOf} override returns the same readable tactics as the aggregate, no more. */
    @Test
    void tacticsOfIsThePointWiseEquivalentOfTheAggregatesTactics() {
        assertThat(service.tacticsOf(BY_ID)).isEqualTo(service.load(BY_ID).tactics());
        assertThat(service.tacticsOf(BY_ID)).hasSize(2);
    }

    // ---- asset files and templates (FR-002 to FR-007) -----------------------------------------------------------

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, (byte) 0xff};

    private static Path withAssetsAndTemplates(Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host.resolve("assets-and-templates"));
        Files.delete(pack.resolve(BROKEN_FILE));
        Files.createDirectories(pack.resolve("assets/debrief"));
        Files.writeString(
                pack.resolve("assets/debrief/logo.png.asset.yaml"),
                "id: debrief-logo\nmime: image/png\npath: debrief/logo.png\ntitle: Logo\n");
        Files.write(pack.resolve("assets/debrief/logo.png"), PNG);
        Files.writeString(pack.resolve("assets/lint.config.yaml"), "rules: []\n");
        Files.createDirectories(pack.resolve("templates/writing"));
        Files.write(pack.resolve("templates/writing/meeting-minutes.md"), new byte[] {'#', '\r', '\n', 0});
        return pack;
    }

    private static DomainRepositoryService serviceOver(Path pack) {
        return new DomainRepositoryService(
                () -> List.of(new PackLocation("pack-a", PackLevel.ORG, pack)), new YamlSerializationStrategy());
    }

    /** The live load path assembles the sidecar, both asset files and the template into one valid aggregate. */
    @Test
    void theLoadHoldsAssetsAssetFilesAndTemplates(@TempDir Path host) throws IOException {
        Pack pack = serviceOver(withAssetsAndTemplates(host)).load(BY_ID);

        assertThat(pack.violations()).isEmpty();
        assertThat(pack.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.id().value()).isEqualTo("debrief-logo");
            assertThat(asset.path()).isEqualTo("debrief/logo.png");
            assertThat(asset.title()).contains("Logo");
        });
        assertThat(pack.assetFiles())
                .extracting(AssetFile::path)
                .containsExactly("debrief/logo.png", "lint.config.yaml");
        assertThat(pack.assetFiles().getFirst().payload()).isEqualTo(Payload.of(PNG));
        assertThat(pack.templates()).singleElement().satisfies(template -> {
            assertThat(template.id().value()).isEqualTo("writing/meeting-minutes.md");
            assertThat(template.content()).isEqualTo(Payload.of(new byte[] {'#', '\r', '\n', 0}));
        });
        assertThat(pack.tactics()).hasSize(2);
    }

    /** A sidecar whose payload is missing is a violation of the loaded pack, never a breach of its invariant. */
    @Test
    void aMissingPayloadIsAViolationOfTheLoadedPack(@TempDir Path host) throws IOException {
        Path source = withAssetsAndTemplates(host);
        Files.delete(source.resolve("assets/debrief/logo.png"));

        Pack pack = serviceOver(source).load(BY_ID);

        assertThat(pack.assets()).isEmpty();
        assertThat(pack.violations())
                .containsExactly(new PackElementViolation(
                        "assets/debrief/logo.png.asset.yaml",
                        "asset",
                        "payload assets/debrief/logo.png does not exist"));
        assertThat(pack.templates()).hasSize(1);
    }

    /** The gate adapter's entry points: a template and the asset files are written byte for byte, and reload equal. */
    @Test
    void saveArtifactAndWriteAssetFileWriteByteIdenticalFiles(@TempDir Path host) throws IOException {
        Path source = withAssetsAndTemplates(host);
        Pack loaded = serviceOver(source).load(BY_ID);
        Path target = PackFixtures.copyOf("pack-descriptor-only", host.resolve("target"));
        DomainRepositoryService into = new DomainRepositoryService(
                () -> List.of(new PackLocation("target", PackLevel.ORG, target)), new YamlSerializationStrategy());
        PackIdentifier targetId = new PackIdentifier("target");

        for (Template template : loaded.templates()) {
            into.saveArtifact(targetId, template);
        }
        for (Asset asset : loaded.assets()) {
            into.saveArtifact(targetId, asset);
        }
        for (AssetFile file : loaded.assetFiles()) {
            into.writeAssetFile(targetId, file);
        }

        for (String file :
                List.of("templates/writing/meeting-minutes.md", "assets/debrief/logo.png", "assets/lint.config.yaml")) {
            assertThat(Files.readAllBytes(target.resolve(file)))
                    .as(file)
                    .isEqualTo(Files.readAllBytes(source.resolve(file)));
        }
        Pack reloaded = into.load(targetId);
        assertThat(reloaded.violations()).isEmpty();
        assertThat(reloaded.assetFiles()).isEqualTo(loaded.assetFiles());
        assertThat(reloaded.templates())
                .extracting(Template::content)
                .containsExactlyElementsOf(
                        loaded.templates().stream().map(Template::content).toList());
        assertThat(reloaded.assets())
                .extracting(AssetBinding.INSTANCE::toDso)
                .containsExactlyElementsOf(loaded.assets().stream()
                        .map(AssetBinding.INSTANCE::toDso)
                        .toList());
    }

    // ---- PackCatalogRepository and the descriptor lookup ----------------------------------------------------------

    /** The catalog lists every configured pack at the level it is configured at, whatever its pack.yaml declares. */
    @Test
    void theCatalogListsEachConfiguredPackAtItsConfiguredLevel(@TempDir Path host) throws IOException {
        Path declaresOrg = Files.createDirectories(host.resolve("project/local"));
        Files.writeString(
                declaresOrg.resolve("pack.yaml"),
                "pack_id: local\npack_version: 1.0.0\nname: local\npack_level: org\n");
        Path bare = Files.createDirectories(host.resolve("built-in/bare"));
        DomainRepositoryService catalog = new DomainRepositoryService(
                () -> List.of(
                        new PackLocation("local", PackLevel.PROJECT, declaresOrg),
                        new PackLocation("bare", PackLevel.BUILT_IN, bare)),
                new YamlSerializationStrategy());

        assertThat(catalog.configured())
                .containsExactly(
                        new ConfiguredPack(new PackIdentifier("local"), PackLevel.PROJECT),
                        new ConfiguredPack(new PackIdentifier("bare"), PackLevel.BUILT_IN));
    }

    /** A pack with a readable pack.yaml is found, as the descriptor get answers it (the positive control). */
    @Test
    void aReadableDescriptorIsFound() {
        assertThat(service.find(BY_NAME)).contains(service.get(BY_NAME));
        assertThat(service.find(BY_ID)).contains(service.get(BY_NAME));
    }

    /** A pack without pack.yaml has no descriptor to find: empty, where get refuses. */
    @Test
    void aPackWithoutPackYamlFindsNoDescriptor(@TempDir Path host) throws IOException {
        Path bare = Files.createDirectories(host.resolve("bare"));
        Files.writeString(bare.resolve("notes.md"), "Nothing to see.\n");
        DomainRepositoryService over = overOne("bare", bare);

        assertThat(over.find(new PackIdentifier("bare"))).isEmpty();
        assertThatThrownBy(() -> over.get(new PackIdentifier("bare")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /** A pack.yaml that is there but is no pack descriptor is refused, not taken for an absent one. */
    @Test
    void anInvalidPackYamlIsRefusedUnreadable(@TempDir Path host) throws IOException {
        Path broken = Files.createDirectories(host.resolve("broken"));
        Files.writeString(broken.resolve("pack.yaml"), "pack_id: broken\npack_version: [unclosed\n");

        assertUnreadable(overOne("broken", broken), "broken");
    }

    /** A pack.yaml that is a directory cannot be read: refused, not absent. */
    @Test
    void aPackYamlThatIsADirectoryIsRefusedUnreadable(@TempDir Path host) throws IOException {
        Path odd = Files.createDirectories(host.resolve("odd/pack.yaml")).getParent();

        assertUnreadable(overOne("odd", odd), "odd");
    }

    /** A configured pack whose directory is missing is refused, as every read of it is. */
    @Test
    void aMissingPackDirectoryIsRefusedUnreadable(@TempDir Path host) {
        assertUnreadable(overOne("gone", host.resolve("gone")), "gone");
    }

    private static DomainRepositoryService overOne(String name, Path directory) {
        PackLocation location = new PackLocation(name, PackLevel.ORG, directory);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static void assertUnreadable(DomainRepositoryService over, String pack) {
        assertThatThrownBy(() -> over.find(new PackIdentifier(pack)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
