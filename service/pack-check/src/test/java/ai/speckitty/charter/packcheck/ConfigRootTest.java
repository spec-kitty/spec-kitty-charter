package ai.speckitty.charter.packcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link ConfigRoot#packs}: the packs of a config root, by the service's entry rule (#478, T015, review MEDIUM-2). */
class ConfigRootTest {

    @TempDir
    Path temp;

    private Path packs() throws IOException {
        return Files.createDirectories(temp.resolve(ConfigRoot.PACKS));
    }

    private static void link(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }
    }

    @Test
    void theFiveExamplePacksAreFiveRootsInNameOrderWithNoConfiguredLevel() throws IOException {
        ExamplePacks.names().forEach(name -> ExamplePacks.copyOf(name, temp.resolve(ConfigRoot.PACKS)));

        ConfigRoot.Listing listing = ConfigRoot.packs(temp);

        assertThat(listing.packs())
                .extracting(PackRoot::name)
                .containsExactlyElementsOf(
                        ExamplePacks.names().stream().sorted().toList())
                .hasSize(5);
        assertThat(listing.packs()).allSatisfy(root -> {
            assertThat(root.configuredLevel()).isEmpty();
            assertThat(root.locator())
                    .isEqualTo(
                            temp.resolve(ConfigRoot.PACKS).resolve(root.name()).toString());
        });
        assertThat(listing.strayLinks()).isEmpty();
    }

    @Test
    void aLinkToAPackDirectoryIsAPack() throws IOException {
        Path elsewhere = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("elsewhere"));
        link(packs().resolve("linked"), elsewhere);

        assertThat(ConfigRoot.packs(temp).packs()).extracting(PackRoot::name).containsExactly("linked");
    }

    @Test
    void aFileUnderPacksIsIgnored() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        Files.writeString(packs().resolve("notes.md"), "not a pack\n");

        assertThat(ConfigRoot.packs(temp).packs()).extracting(PackRoot::name).containsExactly("a-pack");
    }

    @Test
    void aHiddenDirectoryIsNoPack() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        Files.createDirectories(packs().resolve(".staging"));

        assertThat(ConfigRoot.packs(temp).packs()).extracting(PackRoot::name).containsExactly("a-pack");
    }

    @Test
    void anAsideCopyIsNoPack() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        Files.createDirectories(packs().resolve("a-pack.pack-old-123"));

        assertThat(ConfigRoot.packs(temp).packs()).extracting(PackRoot::name).containsExactly("a-pack");
    }

    @Test
    void aDanglingLinkIsAStrayLinkNotDropped() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        link(packs().resolve("gone"), temp.resolve("nowhere"));

        ConfigRoot.Listing listing = ConfigRoot.packs(temp);

        assertThat(listing.packs()).extracting(PackRoot::name).containsExactly("a-pack");
        assertThat(listing.strayLinks()).containsExactly(packs().resolve("gone"));
    }

    @Test
    void aLinkToAFileIsAStrayLinkNotDropped() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        Path file = Files.writeString(temp.resolve("notes.md"), "not a pack\n");
        link(packs().resolve("filed"), file);

        ConfigRoot.Listing listing = ConfigRoot.packs(temp);

        assertThat(listing.packs()).extracting(PackRoot::name).containsExactly("a-pack");
        assertThat(listing.strayLinks()).containsExactly(packs().resolve("filed"));
    }

    @Test
    void aHiddenDanglingLinkIsNoPackEither() throws IOException {
        Files.createDirectories(packs().resolve("a-pack"));
        link(packs().resolve(".gone"), temp.resolve("nowhere"));

        assertThat(ConfigRoot.packs(temp).strayLinks()).isEmpty();
    }

    @Test
    void noPacksDirectoryIsNoPack() throws IOException {
        ConfigRoot.Listing listing = ConfigRoot.packs(temp);

        assertThat(listing.packs()).isEmpty();
        assertThat(listing.strayLinks()).isEmpty();
    }
}
