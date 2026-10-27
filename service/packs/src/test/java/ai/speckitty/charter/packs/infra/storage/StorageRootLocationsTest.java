package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The packs of the pack domain: which directories are packs, at which level, in which order. */
class StorageRootLocationsTest {

    @TempDir
    Path packs;

    private void pack(String name, String level) throws IOException {
        Files.createDirectories(packs.resolve(name));
        Files.writeString(
                packs.resolve(name + "/pack.yaml"),
                "pack_id: " + name + "\npack_version: 1.0.0\nname: " + name + "\npack_level: " + level + "\n");
    }

    @Test
    void everyDirectoryIsAPackAtTheLevelItDeclaresTheBuiltInOneFirst() throws IOException {
        pack("qa", "org");
        pack("built-in", "built-in");
        pack("my-project", "project");

        assertThat(new StorageRootLocations(packs).all())
                .containsExactly(
                        new PackLocation("built-in", PackLevel.BUILT_IN, packs.resolve("built-in")),
                        new PackLocation("my-project", PackLevel.PROJECT, packs.resolve("my-project")),
                        new PackLocation("qa", PackLevel.ORG, packs.resolve("qa")));
    }

    @Test
    void aPackWithoutAReadableDeclarationIsAnOrgPack() throws IOException {
        Files.createDirectories(packs.resolve("bare"));
        Files.createDirectories(packs.resolve("garbled"));
        Files.writeString(packs.resolve("garbled/pack.yaml"), "pack_level: [unclosed\n");
        Files.createDirectories(packs.resolve("galaxy"));
        Files.writeString(packs.resolve("galaxy/pack.yaml"), "pack_level: galaxy\n");

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::level)
                .containsOnly(PackLevel.ORG);
    }

    @Test
    void filesHiddenDirectoriesAndCommitLeftoversAreNotPacks() throws IOException {
        pack("qa", "org");
        Files.writeString(packs.resolve("README.md"), "x");
        Files.createDirectories(packs.resolve(".pack-commit-123"));
        Files.createDirectories(packs.resolve(".git"));
        Files.createDirectories(packs.resolve("qa.pack-old-abc"));

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
    }

    @Test
    void aSymbolicLinkIsNeverAPackBecauseACommitWouldReplaceIt(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        Files.createDirectories(elsewhere.resolve("external"));
        try {
            Files.createSymbolicLink(packs.resolve("external"), elsewhere.resolve("external"));
        } catch (UnsupportedOperationException | IOException noLinks) {
            abort("the filesystem cannot create symbolic links");
        }

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
    }

    @Test
    void theDomainIsListedOnEveryCallSoAPackCopiedInIsSeen() throws IOException {
        StorageRootLocations locations = new StorageRootLocations(packs);
        assertThat(locations.all()).isEmpty();

        pack("qa", "org");

        assertThat(locations.all()).extracting(PackLocation::name).containsExactly("qa");
    }

    @Test
    void aDomainDirectoryThatDoesNotExistYetHoldsNoPacks() {
        assertThat(new StorageRootLocations(packs.resolve("not-yet")).all()).isEmpty();
        assertThat(packs.resolve("not-yet")).doesNotExist();
    }

    @Test
    void aPackUnderTheDomainIsReadAndWrittenThroughTheStore() throws IOException {
        pack("qa", "org");
        DomainRepositoryService store =
                new DomainRepositoryService(new StorageRootLocations(packs), new YamlSerializationStrategy());
        PackIdentifier qa = new PackIdentifier("qa");

        store.save(qa, store.get(qa));

        assertThat(store.get(qa).packId()).isEqualTo("qa");
        assertThat(packs.resolve("qa/pack.yaml")).exists();
    }

    @Test
    void theConfigRootKeepsEachDomainInItsOwnDirectoryAndIsAbsolute() {
        ConfigRoot root = new ConfigRoot(Path.of("relative/root"));

        assertThat(root.root()).isAbsolute();
        assertThat(root.packs()).isEqualTo(root.root().resolve("packs"));
        assertThat(root.activations()).isEqualTo(root.root().resolve("activations"));
    }
}
