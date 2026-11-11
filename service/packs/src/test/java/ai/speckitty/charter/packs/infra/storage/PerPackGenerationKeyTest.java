package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRemovalStore.Removal;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-pack write generation is keyed by the pack's configured name on every write path (issue 418, R5): the facts
 * cache of the effective reads keys on it, so a write that named the pack by its declared {@code pack_id} would move a
 * counter nobody reads.
 */
class PerPackGenerationKeyTest {

    private static final String CONFIGURED = "pack-a";
    /** The {@code pack_id} the fixture's {@code pack.yaml} declares: not the configured name. */
    private static final String DECLARED = "01M5DEH8R0VNDTR1PF1XTVRE0A";

    private static Locations locations(Path packs) {
        return new StorageRootLocations(packs);
    }

    private static Toolguide toolguide() {
        return Toolguide.builder()
                .id(new ToolguideIdentifier("ripgrep"))
                .packId(new PackIdentifier(CONFIGURED))
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(Guide.of("How to use the tool."))
                .summary("How to search.")
                .build();
    }

    private static Pack withToolguide(Pack baseline) {
        Pack.Builder builder = Pack.builder().id(baseline.id()).descriptor(baseline.descriptor());
        baseline.tactics().forEach(builder::tactic);
        baseline.directives().forEach(builder::directive);
        baseline.styleguides().forEach(builder::styleguide);
        baseline.toolguides().forEach(builder::toolguide);
        baseline.paradigms().forEach(builder::paradigm);
        baseline.violations().forEach(builder::violation);
        builder.toolguide(toolguide());
        return builder.build();
    }

    private static final class Rig {
        final PackWriteGeneration generation = new PackWriteGeneration();
        final PackIdentifier declared = new PackIdentifier(DECLARED);
        final DomainRepositoryService service;
        final PackCommitStore commits;
        final CommitStorePackWriteStore writeStore;

        Rig(Path packs) {
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            RegistryWriteLock lock = new RegistryWriteLock(Duration.ofSeconds(5));
            service = new DomainRepositoryService(locations(packs), strategy);
            commits = new PackCommitStore(locations(packs), strategy, lock, generation);
            writeStore = new CommitStorePackWriteStore(locations(packs), strategy, commits, lock, generation);
        }
    }

    /** The packs root holding {@code pack-a} without its deliberately unreadable tactic. */
    private static Path unbrokenPacks(Path host) throws IOException {
        Path packs = host.resolve("packs");
        Path packDir = PackFixtures.copyOf("pack-a", packs);
        Files.delete(packDir.resolve("broken-tactic.tactic.yaml"));
        return packs;
    }

    @Test
    @DisplayName("a per-artifact write addressed by the declared pack_id moves the generation of the configured name")
    void aPerArtifactWriteMovesTheConfiguredNamesGeneration(@TempDir Path host) throws IOException {
        Rig rig = new Rig(unbrokenPacks(host));
        long before = rig.generation.ofPack(CONFIGURED);

        rig.writeStore.commit(
                rig.declared,
                null,
                Change.artifact(withToolguide(rig.service.load(new PackIdentifier(CONFIGURED))), toolguide()));

        assertThat(rig.generation.ofPack(CONFIGURED)).isEqualTo(before + 2);
        assertThat(rig.generation.ofPack(DECLARED))
                .as("nothing is keyed by the declared id")
                .isZero();
    }

    @Test
    @DisplayName("a DELETE addressed by the declared pack_id moves the generation of the configured name")
    void aRemovalMovesTheConfiguredNamesGeneration(@TempDir Path host) throws IOException {
        Rig rig = new Rig(unbrokenPacks(host));
        rig.writeStore.commit(
                rig.declared,
                null,
                Change.artifact(withToolguide(rig.service.load(new PackIdentifier(CONFIGURED))), toolguide()));
        long before = rig.generation.ofPack(CONFIGURED);

        rig.writeStore.remove(
                rig.declared, Removal.ofArtifact(new Urn(NodeKind.TOOLGUIDE, "ripgrep"), List.of(), List.of()));

        assertThat(rig.generation.ofPack(CONFIGURED)).isEqualTo(before + 2);
        assertThat(rig.generation.ofPack(DECLARED))
                .as("nothing is keyed by the declared id")
                .isZero();
    }

    @Test
    @DisplayName("a whole-pack replace addressed by the declared pack_id moves the generation of the configured name")
    void aWholePackReplaceMovesTheConfiguredNamesGeneration(@TempDir Path host) throws IOException {
        Rig rig = new Rig(unbrokenPacks(host));
        Path upload = PackFixtures.copyOf("pack-a", host.resolve("stage"));
        Files.delete(upload.resolve(PackFixtures.BROKEN_FILE));
        Files.writeString(
                upload.resolve(PackFixtures.ALPHA_FILE),
                Files.readString(upload.resolve(PackFixtures.ALPHA_FILE), StandardCharsets.UTF_8)
                        .replace("Alpha tactic", "Alpha, replaced"),
                StandardCharsets.UTF_8);
        long before = rig.generation.ofPack(CONFIGURED);

        rig.commits.replaceWhole(rig.declared, new StagedPack(upload));

        assertThat(rig.generation.ofPack(CONFIGURED)).isEqualTo(before + 2);
        assertThat(rig.generation.ofPack(DECLARED))
                .as("nothing is keyed by the declared id")
                .isZero();
    }
}
