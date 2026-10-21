package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
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
                .schemaVersion(original.schemaVersion())
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
                before.undeclaredKeys(),
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
            public String writeTactic(TacticDso tactic) {
                return yaml.writeTactic(tactic);
            }

            @Override
            public FileRead<TacticDso> readTactic(String text, String file) {
                tacticFilesOpened.add(file);
                return yaml.readTactic(text, file);
            }

            @Override
            public String writeDescriptor(PackDescriptorDso descriptor) {
                return yaml.writeDescriptor(descriptor);
            }

            @Override
            public FileRead<PackDescriptorDso> readDescriptor(String text) {
                return yaml.readDescriptor(text);
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
}
