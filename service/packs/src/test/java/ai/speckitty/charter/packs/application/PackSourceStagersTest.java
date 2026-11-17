package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What stages a source for the job (IC-05): the fetcher of the kind, and a kind nobody built is unavailable. The fetched
 * tree reaches the job unchanged, with the target as the name it must declare, which the registration checks once it
 * has read and judged the tree (#554, F8).
 */
class PackSourceStagersTest {

    private static final PackSourceRequest HTTPS = new PackSourceRequest(
            PackSourceKind.HTTPS_ARCHIVE, "https://packs.example.org/a.tgz", null, null, null, null);

    @TempDir
    Path directory;

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

    /** Step 2 of the registration the job runs over the staged tree: the one read, judged, then assembled. */
    private static PackRegistrationService.ReadUpload registered(StagedPack staged) {
        return PackRegistrationService.readUpload(
                new FileSystemPackInspector(), new WholePackStore(new YamlSerializationStrategy()), "acme", staged);
    }

    @Test
    void theFetchedTreeReachesTheJobWithTheTargetAsTheNameItMustDeclare() throws Exception {
        StagedPack pack = new StagedPack(directory.resolve("pack"));
        Staged fetched = new Staged(pack, Map.of("kind", "https_archive", "url", HTTPS.url()));
        PackSourceStagers stagers = new PackSourceStagers(List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, fetched)));

        Staged staged = stagers.stagerFor(HTTPS, "acme").stage(directory);

        assertThat(staged.pack()).isEqualTo(new StagedPack(pack.directory(), Optional.of("acme")));
        assertThat(staged.source()).isEqualTo(fetched.source());
    }

    @Test
    void aNameThatIsNotTheTargetIsRefusedAsANameMismatchOnceTheTreeIsJudged() {
        Path tree = ExamplePacks.copyOf(ExamplePacks.ORG, directory.resolve("fetched"));

        assertThat(registered(new StagedPack(tree).declaring("org-base"))
                        .contents()
                        .pack()
                        .descriptor()
                        .name())
                .isEqualTo("org-base");
        assertThatThrownBy(() -> registered(new StagedPack(tree).declaring("other")))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
                    assertThat(refusal.getMessage()).startsWith("name_mismatch: ");
                    assertThat(refusal.getMessage()).doesNotContain("org-base", "other");
                });
    }

    @Test
    void aTreeThatDoesNotLoadIsRefusedByTheJudgementAndNeverReachesTheNameCheck() throws Exception {
        Path tree = ExamplePacks.copyOf(ExamplePacks.ORG, directory.resolve("fetched"));
        java.nio.file.Files.delete(tree.resolve("pack.yaml"));

        assertThatThrownBy(() -> registered(new StagedPack(tree).declaring("other")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
    }

    @Test
    void aKindNobodyBuiltHasNoFetcherAndIsUnavailable() {
        PackSourceStagers stagers = new PackSourceStagers(
                List.of(fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(new StagedPack(directory), Map.of()))));
        PackSourceRequest git = new PackSourceRequest(
                PackSourceKind.GIT, "https://git.example.org/a.git", "refs/tags/v1", null, null, null);

        assertThat(stagers.fetcherFor(PackSourceKind.HTTPS_ARCHIVE)).isPresent();
        assertThat(stagers.fetcherFor(PackSourceKind.GIT)).isEmpty();
        assertThatThrownBy(() -> stagers.stagerFor(git, "acme"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_UNAVAILABLE);
                    assertThat(refusal.getMessage()).doesNotContain("git.example.org");
                });
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
                List.of(noGit, fetcher(PackSourceKind.HTTPS_ARCHIVE, new Staged(new StagedPack(directory), Map.of()))));
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

        assertThatThrownBy(() -> new PackSourceStagers(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
