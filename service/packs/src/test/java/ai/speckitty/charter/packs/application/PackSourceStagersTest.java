package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import ai.speckitty.charter.packs.domain.Pack;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What stages a source for the job (IC-05): the fetcher of the kind, then the check that the name the pack declares is
 * the target, and a kind nobody built is unavailable. The fetched tree reaches the job unchanged.
 */
class PackSourceStagersTest {

    private static final PackSourceRequest HTTPS = new PackSourceRequest(
            PackSourceKind.HTTPS_ARCHIVE, "https://packs.example.org/a.tgz", null, null, null, null);

    @TempDir
    Path directory;

    private final WholePackWriteStore.Loader loader = mock(WholePackWriteStore.Loader.class);

    private PackSourceFetcher fetcher(PackSourceKind kind, Staged staged) {
        return new PackSourceFetcher() {
            @Override
            public PackSourceKind kind() {
                return kind;
            }

            @Override
            public Staged fetch(PackSourceRequest request, Path into) {
                return staged;
            }
        };
    }

    private void declaresName(StagedPack pack, String name) {
        WholePackWriteStore.Contents contents = mock(WholePackWriteStore.Contents.class);
        Pack aggregate = mock(Pack.class);
        when(loader.loadStaged(pack)).thenReturn(contents);
        when(contents.pack()).thenReturn(aggregate);
        when(aggregate.descriptor())
                .thenReturn(new PackDescriptor(
                        "01M4P01D5TWB5FKZGNNQG3943P",
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty()));
    }

    @Test
    void theFetchedTreeReachesTheJobUnchangedWhenItsNameIsTheTarget() throws Exception {
        StagedPack pack = new StagedPack(directory.resolve("pack"));
        Staged fetched = new Staged(pack, Map.of("kind", "https_archive", "url", HTTPS.url()));
        declaresName(pack, "acme");
        PackSourceStagers stagers =
                new PackSourceStagers(List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, fetched)), loader);

        Stager stager = stagers.stagerFor(HTTPS, "acme");

        assertThat(stager.stage(directory)).isSameAs(fetched);
    }

    @Test
    void aNameThatIsNotTheTargetIsRefusedAsANameMismatchWhateverTheTreeHolds() {
        StagedPack pack = new StagedPack(directory.resolve("pack"));
        declaresName(pack, "acme");
        PackSourceStagers stagers = new PackSourceStagers(
                List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(pack, Map.of()))), loader);

        assertThatThrownBy(() -> stagers.stagerFor(HTTPS, "other").stage(directory))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
                    assertThat(refusal.getMessage()).startsWith("name_mismatch: ");
                    assertThat(refusal.getMessage()).doesNotContain("acme", "other");
                });
    }

    @Test
    void aTreeThatDoesNotLoadIsRefusedByTheLoadAndNeverReachesTheNameCheck() {
        StagedPack pack = new StagedPack(directory.resolve("pack"));
        when(loader.loadStaged(pack))
                .thenThrow(new CharterRefusal(RefusalCode.VALIDATION_FAILED, "the descriptor is unreadable"));
        PackSourceStagers stagers = new PackSourceStagers(
                List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(pack, Map.of()))), loader);

        assertThatThrownBy(() -> stagers.stagerFor(HTTPS, "acme").stage(directory))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
    }

    @Test
    void aKindNobodyBuiltHasNoFetcherAndIsUnavailable() {
        PackSourceStagers stagers = new PackSourceStagers(
                List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(new StagedPack(directory), Map.of()))),
                loader);
        PackSourceRequest git = new PackSourceRequest(
                PackSourceKind.GIT, "https://git.example.org/a.git", "refs/tags/v1", null, null, null);

        assertThat(stagers.fetcherFor(PackSourceKind.HTTPS_ARCHIVE)).isPresent();
        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isEmpty();
        assertThatThrownBy(() -> stagers.stagerFor(git, "acme"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_UNAVAILABLE);
                    assertThat(refusal.getMessage()).doesNotContain("git.example.org");
                });
        verify(loader, never()).loadStaged(any());
    }

    @Test
    void aFetcherThatIsNotAvailableIsAKindThatIsNotBuiltAndIsUnavailable() {
        PackSourceFetcher noGit = new PackSourceFetcher() {
            @Override
            public PackSourceKind kind() {
                return PackSourceKind.GIT;
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Staged fetch(PackSourceRequest request, Path into) {
                throw new AssertionError("an unavailable fetcher is never asked to fetch");
            }
        };
        PackSourceStagers stagers = new PackSourceStagers(
                List.of(noGit, fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(new StagedPack(directory), Map.of()))),
                loader);
        PackSourceRequest git = new PackSourceRequest(
                PackSourceKind.GIT, "https://git.example.org/a.git", "refs/tags/v1", null, null, null);

        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isEmpty();
        assertThat(stagers.fetcherFor(PackSourceKind.HTTPS_ARCHIVE)).isPresent();
        assertThatThrownBy(() -> stagers.stagerFor(git, "acme"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_UNAVAILABLE));
    }

    @Test
    void twoFetchersOfOneKindAreAMisconfiguration() {
        PackSourceFetcher first = fetcher(PackSourceKind.FILESYSTEM, new Staged(new StagedPack(directory), Map.of()));
        PackSourceFetcher second = fetcher(PackSourceKind.FILESYSTEM, new Staged(new StagedPack(directory), Map.of()));

        assertThatThrownBy(() -> new PackSourceStagers(List.of(first, second), loader))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
