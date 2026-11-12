package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.StagingArea;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import ai.speckitty.charter.packs.infra.fetch.git.GitHostResolver;
import ai.speckitty.charter.packs.infra.fetch.git.GitRemote;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * The composition of the pack sources (FR-004, IC-05, IC-18, NFR-011): what a start refuses, and which kinds are built.
 */
class PackSourceWiringTest {

    private static final ObjectProvider<ConfigRoot> NO_CONFIG_ROOT =
            new StaticListableBeanFactory().getBeanProvider(ConfigRoot.class);

    private static final ObjectProvider<GitRemote> NO_REMOTE =
            new StaticListableBeanFactory().getBeanProvider(GitRemote.class);
    private static final ObjectProvider<GitHostResolver> NO_RESOLVER =
            new StaticListableBeanFactory().getBeanProvider(GitHostResolver.class);

    @TempDir
    Path root;

    private final PackSourceWiring wiring = new PackSourceWiring();
    private final PackTreeReader tree = new BoundedTreeStager();

    private static PackSourceProperties properties(boolean filesystemOn, Path filesystemRoot) {
        return properties(filesystemOn, filesystemRoot, false, null);
    }

    private static PackSourceProperties properties(
            boolean filesystemOn, Path filesystemRoot, boolean gitOn, Path gitExecutable) {
        return new PackSourceProperties(
                new PackSourceProperties.Https(false, List.of(), Duration.ofSeconds(60), DataSize.ofMegabytes(32), 3),
                new PackSourceProperties.Git(gitOn, List.of("git.example.org"), Duration.ofSeconds(60), gitExecutable),
                new PackSourceProperties.Filesystem(filesystemOn, filesystemRoot));
    }

    @Test
    void aFilesystemSourceSwitchedOnWithNoRootFailsTheStart() {
        PackSourceProperties on = properties(true, null);

        assertThatThrownBy(() -> wiring.filesystemSourceFetcher(on, tree, NO_CONFIG_ROOT))
                .isInstanceOf(StartupRefusal.class)
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

    private Path git(String name, String versionLine) throws IOException {
        Path script = root.resolve(name);
        Files.writeString(script, "#!/bin/sh\necho '" + versionLine + "'\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private PackSourceStagers stagers(PackSourceProperties properties) {
        PackArchiveReader archives = mock(PackArchiveReader.class);
        HttpsExchange exchange = (uri, policy) -> {
            throw new AssertionError("no exchange in this test");
        };
        List<PackSourceFetcher> fetchers = List.of(
                wiring.httpsArchiveFetcher(exchange, archives, properties, mock(StagingArea.class)),
                wiring.filesystemSourceFetcher(properties, tree, NO_CONFIG_ROOT),
                wiring.gitSourceFetcher(properties, wiring.gitVersionCheck(properties), tree, NO_REMOTE, NO_RESOLVER));
        return wiring.packSourceStagers(fetchers, mock(WholePackWriteStore.Loader.class));
    }

    @Test
    void aHostWithNoGitOfTheMinimumVersionServesTheOtherKindsAndRefusesGitAsUnavailable() throws IOException {
        PackSourceStagers stagers = stagers(properties(true, root, true, git("old-git", "git version 2.43.0")));

        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isEmpty();
        assertThat(stagers.fetcherFor(PackSourceKind.HTTPS_ARCHIVE)).isPresent();
        assertThat(stagers.fetcherFor(PackSourceKind.FILESYSTEM)).isPresent();
        PackSourceRequest git = new PackSourceRequest(
                PackSourceKind.GIT, "https://git.example.org/a.git", "refs/tags/v1", null, null, null);
        assertThatThrownBy(() -> stagers.stagerFor(git, "acme"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_UNAVAILABLE));
        PackSourceRequest filesystem =
                new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, null, "packs/acme");
        assertThat(stagers.stagerFor(filesystem, "acme")).isNotNull();
    }

    @Test
    void aHostWithNoGitAtAllRefusesGitTheSameWay() {
        PackSourceStagers stagers = stagers(properties(true, root, true, root.resolve("no-such-git")));

        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isEmpty();
        assertThat(stagers.fetcherFor(PackSourceKind.HTTPS_ARCHIVE)).isPresent();
    }

    @Test
    void aHostWithGitOfTheMinimumVersionServesGit() throws IOException {
        PackSourceStagers stagers = stagers(properties(true, root, true, git("new-git", "git version 2.50.1")));

        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isPresent();
    }

    @Test
    void theGitExecutableAndTimeoutAreBoundFromTheConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "charter.pack-sources.git.enabled", "true",
                "charter.pack-sources.git.allowed-hosts", "git.example.org",
                "charter.pack-sources.git.executable", "/opt/git/bin/git",
                "charter.pack-sources.git.timeout", "30s")));

        PackSourceProperties.Git git = binder.bind("charter.pack-sources", PackSourceProperties.class)
                .get()
                .git();

        assertThat(git.executable()).isEqualTo(Path.of("/opt/git/bin/git"));
        assertThat(git.timeout()).isEqualTo(Duration.ofSeconds(30));
        PackSourceProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("charter.pack-sources", PackSourceProperties.class);
        assertThat(defaults.git().executable()).isNull();
        assertThat(defaults.git().timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(defaults.git().enabled()).isFalse();
    }
}
