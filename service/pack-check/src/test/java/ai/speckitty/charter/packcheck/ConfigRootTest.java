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

/** {@link ConfigRoot#packs}: the packs of a config root (#478, T015). */
class ConfigRootTest {

    @TempDir
    Path temp;

    @Test
    void theFiveExamplePacksAreFiveRootsInNameOrderWithNoConfiguredLevel() throws IOException {
        ExamplePacks.names().forEach(name -> ExamplePacks.copyOf(name, temp.resolve(ConfigRoot.PACKS)));

        assertThat(ConfigRoot.packs(temp))
                .extracting(PackRoot::name)
                .containsExactlyElementsOf(
                        ExamplePacks.names().stream().sorted().toList())
                .hasSize(5);
        assertThat(ConfigRoot.packs(temp)).allSatisfy(root -> {
            assertThat(root.configuredLevel()).isEmpty();
            assertThat(root.locator())
                    .isEqualTo(
                            temp.resolve(ConfigRoot.PACKS).resolve(root.name()).toString());
        });
    }

    @Test
    void aLinkToAPackDirectoryIsAPack() throws IOException {
        Path elsewhere = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("elsewhere"));
        Path packs = Files.createDirectories(temp.resolve("root").resolve(ConfigRoot.PACKS));
        try {
            Files.createSymbolicLink(packs.resolve("linked"), elsewhere);
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }

        assertThat(ConfigRoot.packs(temp.resolve("root")))
                .extracting(PackRoot::name)
                .containsExactly("linked");
    }

    @Test
    void aFileUnderPacksIsIgnored() throws IOException {
        Path packs = Files.createDirectories(temp.resolve(ConfigRoot.PACKS));
        Files.createDirectories(packs.resolve("a-pack"));
        Files.writeString(packs.resolve("notes.md"), "not a pack\n");

        assertThat(ConfigRoot.packs(temp)).extracting(PackRoot::name).containsExactly("a-pack");
    }

    @Test
    void noPacksDirectoryIsNoPack() throws IOException {
        assertThat(ConfigRoot.packs(temp)).isEmpty();
    }
}
