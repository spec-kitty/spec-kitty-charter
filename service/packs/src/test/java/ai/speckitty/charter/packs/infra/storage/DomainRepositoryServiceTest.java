package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackMutabilityPolicy;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.domain.WritePolicyGate.Operation;
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

    private static final PackIdentifier BY_ID = new PackIdentifier("01M5DEH8R0VNDTR1PF1XTVRE0A");
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
        assertThat(alpha.steps().get(0).examples()).containsExactly("first example", "second example");
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

    /** Resolution refuses an identifier no configured location answers to as not found, like the write side. */
    @Test
    void anUnconfiguredPackIsRefused() {
        assertThatThrownBy(() -> service.load(new PackIdentifier("unconfigured"), ALPHA))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(r.getMessage()).isEqualTo("Pack unconfigured was not found.");
                });
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
        assertThat(before.paths().packDrg()).contains("drg/fragment.yaml");

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
        SerializationStrategy counting = yamlRecordingTheTacticFilesItReads(tacticFilesOpened);
        DomainRepositoryService counted = new DomainRepositoryService(
                () -> List.of(
                        new PackLocation("pack-a", PackLevel.ORG, decoy),
                        new PackLocation("pack-descriptor-only", PackLevel.PROJECT, target)),
                counting);
        PackIdentifier targetId = new PackIdentifier("01M5DEH8R0VNDTR1PF1XTVRE0B");

        assertThat(counted.get(targetId).name()).isEqualTo("roundtrip-descriptor-only");
        assertThat(tacticFilesOpened)
                .as("descriptor resolution opens no tactic file")
                .isEmpty();

        assertThat(counted.load(targetId, new TacticIdentifier("only-tactic"))).isPresent();
        assertThat(tacticFilesOpened).containsExactly("only.tactic.yaml");
    }

    private static SerializationStrategy yamlRecordingTheTacticFilesItReads(List<String> tacticFilesOpened) {
        SerializationStrategy yaml = new YamlSerializationStrategy();
        return new SerializationStrategy() {
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
                "pack_id: 01ARWG13C00000000000010CA1\npack_version: 1.0.0\nname: local\npack_level: org\n");
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
        Files.writeString(
                broken.resolve("pack.yaml"), "pack_id: 01ARWG13C00000000000BR0KEN\npack_version: [unclosed\n");

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

    // ---- PackArtifactRepository ---------------------------------------------------------------------

    /**
     * FR-012, A14 (FR-035, DM-01M6RA92BS6GGCYTRCFQDYYY5S, rule C14): the changed artifacts a sibling contest counts are
     * the distinct targets of a pack's {@code overrides} relations, in first-seen order; an {@code enhances} relation is
     * additive and changes nothing, so two sibling enhancers of one target are no conflict, and a {@code requires}
     * relation changes nothing either.
     */
    @Test
    void a14ChangedArtifactsAreTheDistinctTargetsOfOverridesEdgesAndAnEnhancesEdgeChangesNothing(@TempDir Path host)
            throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("refinements.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: tactic:alpha-tactic, target: directive:DIRECTIVE_010, relation: enhances}
                - {source: tactic:alpha-tactic, target: directive:DIRECTIVE_009, relation: enhances}
                - {source: tactic:alpha-tactic, target: directive:DIRECTIVE_009, relation: overrides}
                - {source: tactic:alpha-tactic, target: tactic:beta-tactic, relation: requires}
                """);

        assertThat(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))
                        .changed())
                .containsExactly("directive:DIRECTIVE_009");
    }

    /** A pack with no graph documents refines nothing: the empty answer, not a refusal. */
    @Test
    void aPackWithoutGraphDocumentsRefinesNothing(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);

        assertThat(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))
                        .changed())
                .isEmpty();
    }

    /** The offering judges a partial graph never: a graph document that cannot be read refuses the refinements. */
    @Test
    void aPackWithAnUnreadableGraphDocumentRefusesItsRefinements(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Files.writeString(pack.resolve("refinements.graph.yaml"), "edges: [\n");

        assertThatThrownBy(
                        () -> overOne("pack-a", pack).compositionFacts(BY_NAME).changed())
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /**
     * The references a pack writes are both endpoints of its graph edges (ADR-0064): a qualified edge source is checked
     * beside its target, and no artifact field writes one.
     */
    @Test
    void theReferencesAPackWritesAreBothEndpointsOfItsGraphEdgesOnly(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("D1.directive.yaml"), """
                id: D1
                schema_version: '1.0'
                title: One
                intent: one
                enforcement: required
                """);
        Files.writeString(pack.resolve("p1.paradigm.yaml"), """
                id: p1
                schema_version: '1.0'
                name: One
                summary: one
                """);
        Files.writeString(pack.resolve("edges.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: other::tactic:t, target: directive:D1, relation: requires}
                """);

        assertThat(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))
                        .references())
                .containsExactly("other::tactic:t", "directive:D1");
    }

    /**
     * An edge that joins an artifact to itself through its own pack's qualifier makes the file of the edge unreadable
     * as the bare edge does; the same edge qualified with another pack joins two artifacts and stands.
     */
    @Test
    void anEdgeQualifiedWithThePacksOwnNameOntoTheSameIdMakesItsFileUnreadable(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);
        PackIdentifier id = new PackIdentifier("pack-descriptor-only");
        String graph = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: %s, target: directive:D1, relation: requires}
                """;

        Files.writeString(pack.resolve("edges.graph.yaml"), graph.formatted("other::directive:D1"));
        assertThat(over.compositionFacts(id).references()).contains("other::directive:D1");

        Files.writeString(pack.resolve("edges.graph.yaml"), graph.formatted("roundtrip-descriptor-only::directive:D1"));
        assertThatThrownBy(() -> over.compositionFacts(id)).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage()).contains("edges.graph.yaml");
        });
    }

    /** A reference list that skipped an unreadable file would pass a reference it never saw: it fails closed. */
    @Test
    void aPackWithAnUnreadableArtifactRefusesItsReferencesAndHeldArtifactsWhileItsLoadStillAnswers() {
        assertThat(service.load(BY_ID).violations()).isNotEmpty();

        assertThatThrownBy(() -> service.compositionFacts(BY_NAME))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refused.getMessage()).contains(BROKEN_FILE);
                });
    }

    /**
     * The held list answers for every kind a reference can name, so a kind outside the served five is not failed open.
     * An anti_pattern is never a file: the pack holds it as a node of its graph document, so the qualified form
     * {@code <pack>::anti_pattern:x} and the bare-form refusal both find it. A node of any other kind is no holding
     * without its file.
     */
    @Test
    void definedArtifactsNameEveryKindThePackHolds(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("D1.directive.yaml"), """
                id: D1
                schema_version: '1.0'
                title: One
                intent: one
                enforcement: required
                """);
        Files.writeString(pack.resolve("release.procedure.yaml"), """
                id: release
                schema_version: '1.0'
                title: Release
                description: ship it
                entry_condition: ready
                exit_condition: shipped
                steps:
                - title: Ship
                  description: ship it
                """);
        Files.writeString(pack.resolve("anti_pattern.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - {urn: 'anti_pattern:big-ball-of-mud', kind: anti_pattern, label: Big Ball of Mud}
                - {urn: 'directive:GHOST', kind: directive, label: Declared without a file}
                edges: []
                """);

        assertThat(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))
                        .defined())
                .containsExactlyInAnyOrder("directive:D1", "procedure:release", "anti_pattern:big-ball-of-mud");
    }

    // ---- the typed edges -----------------------------------------------------------------------------

    private static List<String> typed(PackCompositionFacts facts) {
        return facts.edges().stream()
                .map(edge -> edge.fromReference() + " " + edge.type().token() + " " + edge.toReference())
                .toList();
    }

    /** A graph document's edges are held as written: the relation type and any {@code <pack>::} qualifier. */
    @Test
    void theEdgesHoldEveryGraphDocumentEdgeWithItsRelationAndQualifiers(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("edges.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: directive:D, target: tactic:T, relation: requires}
                - {source: tactic:E, target: tactic:T, relation: enhances}
                - {source: agent_profile:P, target: 'other::agent_profile:Q', relation: specializes_from}
                """);

        assertThat(typed(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))))
                .containsExactly(
                        "directive:D requires tactic:T",
                        "tactic:E enhances tactic:T",
                        "agent_profile:P specializes_from other::agent_profile:Q");
    }

    /**
     * The edges come from every source in a stable order: graph documents in file order, then each component's derived
     * relationships (ADR-0064: no artifact field authors one), then the registered relationship shard; a repeat of one
     * (from, relation, to, qualifiers) is kept once, where it was first seen.
     */
    @Test
    void theEdgesHoldGraphAndRegisteredShardEdgesInOrderWithoutRepeats(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("edges.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: directive:D, target: tactic:t1, relation: requires}
                - {source: tactic:t1, target: styleguide:s1, relation: suggests}
                """);
        Files.writeString(
                pack.resolve(RelationshipShard.SHARD_PATH),
                RelationshipShard.write(List.of(
                        Relationship.of(
                                new Urn(NodeKind.DIRECTIVE, "D9"), new Urn(NodeKind.TACTIC, "t1"), RelationType.SCOPE),
                        Relationship.of(
                                new Urn(NodeKind.DIRECTIVE, "D"),
                                new Urn(NodeKind.TACTIC, "t1"),
                                RelationType.REQUIRES))));

        assertThat(typed(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))))
                .containsExactly(
                        "directive:D requires tactic:t1",
                        "tactic:t1 suggests styleguide:s1",
                        "directive:D9 scope tactic:t1");
    }

    /**
     * The dedup key is (from, relation, to, qualifiers), not the kernel {@link Relationship#equals}, which ignores the
     * relation and the qualifiers: one (from, to) written with two relations, or once bare and once {@code <pack>::}
     * qualified, is two edges; only a true repeat is dropped.
     */
    @Test
    void edgesThatDifferOnlyInTheirRelationOrAQualifierAreAllKept(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("edges.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: directive:D, target: tactic:t1, relation: requires}
                - {source: directive:D, target: tactic:t1, relation: suggests}
                - {source: directive:D, target: 'other::tactic:t1', relation: requires}
                - {source: 'other::directive:D', target: tactic:t1, relation: requires}
                """);
        Files.writeString(
                pack.resolve(RelationshipShard.SHARD_PATH),
                RelationshipShard.write(List.of(
                        Relationship.of(
                                new Urn(NodeKind.DIRECTIVE, "D"), new Urn(NodeKind.TACTIC, "t1"), RelationType.SCOPE),
                        Relationship.of(
                                new Urn(NodeKind.DIRECTIVE, "D"),
                                new Urn(NodeKind.TACTIC, "t1"),
                                RelationType.SUGGESTS))));

        assertThat(typed(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))))
                .containsExactly(
                        "directive:D requires tactic:t1",
                        "directive:D suggests tactic:t1",
                        "directive:D requires other::tactic:t1",
                        "other::directive:D requires tactic:t1",
                        "directive:D scope tactic:t1");
    }

    /** A registered relationship shard alone gives its edges. */
    @Test
    void aRegisteredRelationshipShardIsAnEdgeSource(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(
                pack.resolve(RelationshipShard.SHARD_PATH),
                RelationshipShard.write(List.of(Relationship.of(
                        new Urn(NodeKind.DIRECTIVE, "D1"), new Urn(NodeKind.TACTIC, "t9"), RelationType.REQUIRES))));

        assertThat(typed(overOne("pack-descriptor-only", pack)
                        .compositionFacts(new PackIdentifier("pack-descriptor-only"))))
                .containsExactly("directive:D1 requires tactic:t9");
    }

    // ---- the pack's mutability policy file (ADR-0063, FR-022) ----------------------------------------------------

    private static final String NAMES_POLICY = "mutability_policy: policy.yaml\n";

    /** A pack whose descriptor names {@code policy.yaml} and ships it: the facts and the port hold its levels. */
    @Test
    void aNamedPolicyFileIsReadIntoTheCompositionFactsAndThePort(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("pack.yaml"), Files.readString(pack.resolve("pack.yaml")) + NAMES_POLICY);
        Files.writeString(pack.resolve("policy.yaml"), """
                schema_version: 1
                kind_defaults: {tactic: extendable}
                artifacts: {directive:D1: locked}
                """);
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);
        PackIdentifier id = new PackIdentifier("pack-descriptor-only");

        MutabilityPolicySource policy = over.compositionFacts(id).policy();

        assertThat(policy.levelOf(new Urn(NodeKind.DIRECTIVE, "D1"))).isEqualTo(MutabilityLevel.LOCKED);
        assertThat(policy.levelOf(new Urn(NodeKind.TACTIC, "t"))).isEqualTo(MutabilityLevel.EXTENDABLE);
        assertThat(policy.levelOf(new Urn(NodeKind.DIRECTIVE, "D2"))).isEqualTo(MutabilityLevel.OPEN);
        assertThat(over.policyOf(id)).isEqualTo(policy);
    }

    /** A named file that is missing makes the pack unreadable, naming the key and the file. */
    @Test
    void aNamedPolicyFileThatIsMissingMakesThePackUnreadable(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("pack.yaml"), Files.readString(pack.resolve("pack.yaml")) + NAMES_POLICY);
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);
        PackIdentifier id = new PackIdentifier("pack-descriptor-only");

        assertThatThrownBy(() -> over.compositionFacts(id)).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage())
                    .contains("pack-descriptor-only names mutability_policy policy.yaml, which")
                    .contains("missing");
        });
        assertThatThrownBy(() -> over.policyOf(id))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /** A named file that breaks its schema makes the pack unreadable, naming the file and the breach. */
    @Test
    void aNamedPolicyFileThatBreaksItsSchemaMakesThePackUnreadable(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("pack.yaml"), Files.readString(pack.resolve("pack.yaml")) + NAMES_POLICY);
        Files.writeString(pack.resolve("policy.yaml"), "schema_version: 1\npins: [directive:D1]\n");
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);

        assertThatThrownBy(() -> over.compositionFacts(new PackIdentifier("pack-descriptor-only")))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refused.getMessage())
                            .contains("pack-descriptor-only names mutability_policy policy.yaml, which")
                            .contains("pins");
                });
    }

    /**
     * The presets ride on the facts from the assembly the facts already load (FR-023): a governing preset is
     * there with its must-have items; a pack without presets has none; a broken preset makes the pack unreadable.
     */
    @Test
    void theCompositionFactsCarryThePacksPresetsFromTheSameAssembly(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);
        PackIdentifier id = new PackIdentifier("pack-descriptor-only");
        assertThat(over.compositionFacts(id).presets()).isEmpty();

        Files.createDirectories(pack.resolve("presets"));
        Files.writeString(pack.resolve("presets/governing.yaml"), """
                schema_version: '1.0'
                id: governing
                description: The directive is required.
                activated_directives:
                  - id: D1
                    must_have: true
                  - id: D2
                """);

        assertThat(over.compositionFacts(id).presets()).singleElement().satisfies(preset -> {
            assertThat(preset.id()).isEqualTo("governing");
            assertThat(preset.governing()).isTrue();
            assertThat(preset.required()).containsEntry(ArtifactKind.DIRECTIVE, List.of("D1"));
        });

        Files.writeString(pack.resolve("presets/broken.yaml"), """
                schema_version: '1.0'
                id: broken
                description: '   '
                """);
        assertThatThrownBy(() -> over.compositionFacts(id))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /** No key: the pack has no policy, so every artifact of it is open, whatever lies at the pack root. */
    @Test
    void aPackWhoseDescriptorNamesNoPolicyHasTheEmptyPolicy(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Files.writeString(pack.resolve("policy.yaml"), "schema_version: 1\nartifacts: {directive:D1: locked}\n");
        DomainRepositoryService over = overOne("pack-descriptor-only", pack);
        PackIdentifier id = new PackIdentifier("pack-descriptor-only");

        assertThat(over.compositionFacts(id).policy()).isEqualTo(MutabilityPolicySource.empty());
        assertThat(over.policyOf(id)).isEqualTo(MutabilityPolicySource.empty());
    }

    /**
     * The real stack (ADR-0063 D3, D4): pack {@code p} depends on {@code q}, and {@code q}'s policy file locks
     * {@code directive:X}, which {@code q} holds. The production gate composes {@code p}'s closure through the offering and refuses the write,
     * naming {@code q}, whether {@code p} is addressed by its configured name or its {@code pack_id}. With {@code q}'s
     * named file gone, the offering and the gate both refuse {@code pack_unreadable} naming {@code q}.
     */
    @Test
    void aDependencysLockRefusesARegistrationThroughTheRealComposeAndAMissingFileMakesTheOfferingUnreadable(
            @TempDir Path host) throws IOException {
        String qId = "01M5DEH8R0VNDTR1PF1XTVRE0Q";
        String pId = "01M5DEH8R0VNDTR1PF1XTVRE0P";
        Path q = Files.createDirectories(host.resolve("q"));
        Files.writeString(q.resolve("pack.yaml"), """
                pack_id: %s
                pack_version: 1.0.0
                name: q
                pack_level: built-in
                mutability_policy: policy.yaml
                """.formatted(qId));
        Files.writeString(q.resolve("policy.yaml"), "schema_version: 1\nartifacts: {directive:X: locked}\n");
        // q holds directive:X: a pack's lock reaches only what it or a pack it builds on holds (A15, rule C22).
        Files.writeString(q.resolve("X.directive.yaml"), """
                id: X
                schema_version: '1.0'
                title: X
                intent: x
                enforcement: required
                """);
        Path p = Files.createDirectories(host.resolve("p"));
        Files.writeString(p.resolve("pack.yaml"), """
                pack_id: %s
                pack_version: 1.0.0
                name: p
                pack_level: org
                depends_on:
                  - pack_id: %s
                    range: '>=1.0'
                """.formatted(pId, qId));
        List<PackLocation> locations =
                List.of(new PackLocation("q", PackLevel.BUILT_IN, q), new PackLocation("p", PackLevel.ORG, p));
        DomainRepositoryService over = new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
        OfferingReadService offerings = new OfferingReadService(
                over, over, over, new ConsistentRead(new FileSystemPackSources(() -> locations)));
        WritePolicyGate gate = new PackMutabilityPolicy(
                offerings, over, over, new ConsistentRead(new FileSystemPackSources(() -> locations)));
        Urn locked = new Urn(NodeKind.DIRECTIVE, "X");

        for (PackIdentifier addressed : List.of(new PackIdentifier("p"), new PackIdentifier(pId))) {
            Pack target = over.load(addressed);
            assertThatThrownBy(() -> gate.authorize(target, Operation.REGISTER, locked))
                    .as("addressed as %s", addressed.value())
                    .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                        assertThat(refused.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                        assertThat(refused.getMessage()).contains(qId).contains("locked");
                    });
            assertThatCode(() -> gate.authorize(target, Operation.REGISTER, new Urn(NodeKind.DIRECTIVE, "Y")))
                    .doesNotThrowAnyException();
        }

        Files.delete(q.resolve("policy.yaml"));
        assertThatThrownBy(offerings::offering).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage()).contains("q names mutability_policy policy.yaml, which is missing");
        });
        assertThatThrownBy(() -> gate.authorize(over.load(new PackIdentifier("p")), Operation.REGISTER, locked))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
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
