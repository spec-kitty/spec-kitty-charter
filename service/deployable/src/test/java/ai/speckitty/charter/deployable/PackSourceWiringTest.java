package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.util.unit.DataSize;

/**
 * The composition of the pack sources (FR-004, IC-05, IC-18, NFR-011): what a start refuses, and which kinds are built.
 */
class PackSourceWiringTest {

    private static final ObjectProvider<ConfigRoot> NO_CONFIG_ROOT =
            new StaticListableBeanFactory().getBeanProvider(ConfigRoot.class);

    @TempDir
    Path root;

    private final PackSourceWiring wiring = new PackSourceWiring();
    private final PackTreeReader tree = new BoundedTreeStager();

    private static PackSourceProperties properties(boolean filesystemOn, Path filesystemRoot) {
        return new PackSourceProperties(
                new PackSourceProperties.Https(false, List.of(), Duration.ofSeconds(60), DataSize.ofMegabytes(32), 3),
                new PackSourceProperties.Git(false, List.of()),
                new PackSourceProperties.Filesystem(filesystemOn, filesystemRoot));
    }

    @Test
    void aFilesystemSourceSwitchedOnWithNoRootFailsTheStart() {
        PackSourceProperties on = properties(true, null);

        assertThatThrownBy(() -> wiring.filesystemSourceFetcher(on, tree, NO_CONFIG_ROOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charter.pack-sources.filesystem.root");
    }

    @Test
    void aFilesystemSourceSwitchedOffNeedsNoRoot() {
        PackSourceFetcher fetcher = wiring.filesystemSourceFetcher(properties(false, null), tree, NO_CONFIG_ROOT);

        assertThat(fetcher).isNotNull();
    }

    @Test
    void aFilesystemSourceSwitchedOnWithARootStarts() {
        assertThatCode(() -> wiring.filesystemSourceFetcher(properties(true, root), tree, NO_CONFIG_ROOT))
                .doesNotThrowAnyException();
    }
}
