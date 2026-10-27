package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The packs of one storage root: which directories are packs, at which level, in which order. */
class StorageRootLocationsTest {

    @TempDir
    Path root;

    @Test
    void everyDirectoryUnderTheRootIsAPackTheBuiltInOneFirst() throws IOException {
        Files.createDirectories(root.resolve("qa"));
        Files.createDirectories(root.resolve("built-in"));
        Files.createDirectories(root.resolve("audit"));

        assertThat(new StorageRootLocations(root).all())
                .containsExactly(
                        new PackLocation("built-in", PackLevel.BUILT_IN, root.resolve("built-in")),
                        new PackLocation("audit", PackLevel.ORG, root.resolve("audit")),
                        new PackLocation("qa", PackLevel.ORG, root.resolve("qa")));
    }

    @Test
    void filesHiddenDirectoriesAndCommitLeftoversAreNotPacks() throws IOException {
        Files.createDirectories(root.resolve("qa"));
        Files.writeString(root.resolve("README.md"), "x");
        Files.createDirectories(root.resolve(".pack-commit-123"));
        Files.createDirectories(root.resolve(".git"));
        Files.createDirectories(root.resolve("qa.pack-old-abc"));

        assertThat(new StorageRootLocations(root).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
    }

    @Test
    void theRootIsListedOnEveryCallSoANewPackIsSeen() throws IOException {
        StorageRootLocations locations = new StorageRootLocations(root);
        assertThat(locations.all()).isEmpty();

        Files.createDirectories(root.resolve("qa"));

        assertThat(locations.all()).extracting(PackLocation::name).containsExactly("qa");
    }

    @Test
    void aRootThatIsNotADirectoryIsRefused() throws IOException {
        Path file = Files.writeString(root.resolve("a-file"), "x");

        assertThatThrownBy(() -> new StorageRootLocations(root.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageRootLocations(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPackUnderTheRootIsReadAndWrittenThroughTheStore() throws IOException {
        Files.createDirectories(root.resolve("qa"));
        Files.writeString(
                root.resolve("qa/pack.yaml"), "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        DomainRepositoryService store =
                new DomainRepositoryService(new StorageRootLocations(root), new YamlSerializationStrategy());
        PackIdentifier qa = new PackIdentifier("qa");

        store.save(qa, store.get(qa));

        assertThat(store.get(qa).packId()).isEqualTo("01ABC");
        assertThat(root.resolve("qa/pack.yaml")).exists();
    }
}
