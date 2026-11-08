package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceUpstreamException;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The git source against real git over real https (T093, T152, NFR-011, decision DM-01M6RN677S4XN4WR8ZVMAW777G): bare
 * repositories served by {@code git http-backend} behind a test HTTPS server with a certificate of its own, fetched by the
 * production fetcher with the production argument list, environment, pins and checks. Only where git is sent differs from
 * production ({@link GitHttpsFixture#remote}): the port of the test server, the pin of the loopback address for a host that
 * resolves nowhere, and the test certificate as the one trusted.
 *
 * <p>These tests need git 2.50.1 or later and run in the {@code git-source} job of CI ({@code :packs:gitHostTest}), not in
 * {@code check}. On a host with an older git or none they are skipped, and a skip is reported as one, never as a pass; the
 * CI job sets {@code charter.requireGitHost} so that there a git that is too old fails them instead.
 */
@Tag("git-host")
class GitHostTest {

    private static final String URL = "https://" + GitHttpsFixture.HOST + "/acme/packs.git";
    private static final InetAddress GLOBAL = InetAddress.ofLiteral("93.184.215.14");
    private static final Map<String, String> PACK =
            Map.of("pack.yaml", "name: acme\n", "docs/readme.md", "# acme\n", "artifacts/a.txt", "a\n");

    private static GitVersionCheck check;
    private static GitHttpsFixture fixture;

    @TempDir
    Path temp;

    @BeforeAll
    static void startServer(@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path parent) throws IOException {
        check = GitVersionCheck.resolve(null, System.getenv("PATH"));
        if (!check.enabled() && Boolean.getBoolean("charter.requireGitHost")) {
            throw new AssertionError("the git-source job needs git 2.50.1 or later: " + check.describe());
        }
        assumeTrue(check.enabled(), "not run: " + check.describe());
        fixture = GitHttpsFixture.start(parent);
    }

    @AfterAll
    static void stopServer() {
        if (fixture != null) {
            fixture.close();
        }
    }

    private GitSourceFetcher fetcher(GitRemote remote) {
        return new GitSourceFetcher(
                check,
                new GuideAllowlist(Set.of(GitHttpsFixture.HOST)),
                Duration.ofSeconds(60),
                new BoundedTreeStager(),
                host -> List.of(GLOBAL),
                remote);
    }

    private Staged fetch(String url, String ref, String commit) {
        return fetch(fixture.remote(true, true), url, ref, commit);
    }

    private Staged fetch(GitRemote remote, String url, String ref, String commit) {
        try {
            Path job = Files.createTempDirectory(temp, "job-");
            return fetcher(remote).fetch(new PackSourceRequest(PackSourceKind.GIT, url, ref, commit, null, null), job);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private String publish(String repository, Map<String, String> files, String ref) throws IOException {
        Path bare = fixture.repository(repository);
        return fixture.publish(bare, Files.createTempDirectory(temp, "work-"), files, ref);
    }

    private static String reasonOf(Throwable refusal) {
        assertThat(refusal).isInstanceOf(CharterRefusal.class);
        assertThat(((CharterRefusal) refusal).code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
        return refusal.getMessage().substring(0, refusal.getMessage().indexOf(':'));
    }

    @Test
    void aTagBranchAndCommitOfARepositoryAreFetchedOverHttpsAndTheCommitIsRecorded() throws IOException {
        String commit = publish("acme/packs.git", PACK, "refs/tags/v1.0.0");
        Path bare = fixture.repository("acme/packs.git");
        GitHttpsFixture.git(bare, "update-ref", "refs/heads/main", commit);

        for (String ref : List.of("refs/tags/v1.0.0", "refs/heads/main", commit)) {
            Staged staged = fetch(URL, ref, null);

            assertThat(staged.source())
                    .containsExactlyInAnyOrderEntriesOf(
                            Map.of("kind", "git", "url", URL, "ref", ref, "commit", commit));
            assertThat(staged.pack().directory().resolve("pack.yaml")).hasContent("name: acme\n");
            assertThat(staged.pack().directory().resolve("docs/readme.md")).hasContent("# acme\n");
            assertThat(staged.pack().directory().resolve("artifacts/a.txt")).hasContent("a\n");
        }
    }

    @Test
    void aCommitThatIsTheRefsIsAcceptedAndOneThatIsNotIsRefusedAfterTheFetch() throws IOException {
        String commit = publish("acme/pinned.git", PACK, "refs/heads/main");

        assertThat(fetch("https://git.example.org/acme/pinned.git", "refs/heads/main", commit).source())
                .containsEntry("commit", commit);
        assertThatThrownBy(() -> fetch("https://git.example.org/acme/pinned.git", "refs/heads/main", "0".repeat(40)))
                .satisfies(refusal -> assertThat(reasonOf(refusal)).isEqualTo("commit_mismatch"));
    }

    @Test
    void aRefTheRepositoryDoesNotHaveIsABadRef() throws IOException {
        publish("acme/refs.git", PACK, "refs/heads/main");

        assertThatThrownBy(() -> fetch("https://git.example.org/acme/refs.git", "refs/heads/nope", null))
                .satisfies(refusal -> assertThat(reasonOf(refusal)).isEqualTo("bad_ref"));
    }

    @Test
    void aSymlinkInTheTreeIsRefusedNamingTheEntryAndNothingIsStaged() throws IOException {
        Path bare = fixture.repository("acme/link.git");
        Path work = Files.createTempDirectory(temp, "work-");
        Files.writeString(work.resolve("pack.yaml"), "name: acme\n");
        Files.createSymbolicLink(work.resolve("escape"), Path.of("/etc/passwd"));
        fixture.commit(bare, work, "refs/heads/main");

        assertThatThrownBy(() -> fetch("https://git.example.org/acme/link.git", "refs/heads/main", null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).extracting(v -> v.code()).containsExactly("entry_type");
                    assertThat(refusal.getMessage()).doesNotContain("passwd");
                });
    }

    @Test
    void aSubmoduleInTheTreeIsRefusedAndNeverFetched() throws IOException {
        Path bare = fixture.repository("acme/submodule.git");
        Path work = Files.createTempDirectory(temp, "work-");
        Files.writeString(work.resolve("pack.yaml"), "name: acme\n");
        GitHttpsFixture.git(work, "init", "-q");
        GitHttpsFixture.git(work, "add", "pack.yaml");
        GitHttpsFixture.git(
                work, "update-index", "--add", "--cacheinfo", "160000,0123456789abcdef0123456789abcdef01234567,vendored");
        fixture.commitIndex(bare, work, "refs/heads/main");

        assertThatThrownBy(() -> fetch("https://git.example.org/acme/submodule.git", "refs/heads/main", null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).extracting(v -> v.code()).containsExactly("entry_type");
                });
    }

    @Test
    void aRedirectIsRefusedEvenToTheSameHost() throws IOException {
        publish("acme/moved.git", PACK, "refs/heads/main");
        fixture.redirect("/old/moved.git", "/acme/moved.git");

        assertThatThrownBy(() -> fetch("https://git.example.org/old/moved.git", "refs/heads/main", null))
                .satisfies(refusal -> assertThat(reasonOf(refusal)).isEqualTo("redirect_refused"));
    }

    @Test
    void hostileRefsAreRefusedBeforeAnyRequestReachesTheServer() throws IOException {
        publish("acme/hostile.git", PACK, "refs/heads/main");
        int before = fixture.requests();

        for (String ref : List.of("--upload-pack=evil", "refs/heads/../secret", "refs/heads/a@{1}", "refs/heads/topic.lock")) {
            assertThatThrownBy(() -> fetch("https://git.example.org/acme/hostile.git", ref, null))
                    .satisfies(refusal -> assertThat(reasonOf(refusal)).isEqualTo("bad_ref"));
        }

        assertThat(fixture.requests()).isEqualTo(before);
    }

    @Test
    void gitReachesTheServerOnlyThroughThePinnedAddress() throws IOException {
        publish("acme/pin.git", PACK, "refs/heads/main");

        // The host resolves nowhere: without the pin git cannot connect, so the pin is what lets it, and only to its address.
        assertThatThrownBy(() -> fetch(fixture.remote(false, true), "https://git.example.org/acme/pin.git", "refs/heads/main", null))
                .isInstanceOf(SourceUpstreamException.class);
        assertThat(fetch(fixture.remote(true, true), "https://git.example.org/acme/pin.git", "refs/heads/main", null))
                .isNotNull();
    }

    @Test
    void aServerWhoseCertificateIsNotTrustedIsNotFetchedFrom() throws IOException {
        publish("acme/tls.git", PACK, "refs/heads/main");

        assertThatThrownBy(() -> fetch(fixture.remote(true, false), "https://git.example.org/acme/tls.git", "refs/heads/main", null))
                .isInstanceOf(SourceUpstreamException.class);
    }

    @Test
    void theProductionPolicyRefusesEveryTransportButHttps() throws IOException {
        Path home = Files.createDirectories(temp.resolve("policy-home"));
        Path bare = fixture.repository("acme/policy.git");
        Path marker = temp.resolve("ext-ran");
        List<String> locations = List.of(
                "file://" + bare,
                bare.toString(),
                "ext::sh -c touch% " + marker,
                "git://127.0.0.1/acme/policy.git",
                "http://127.0.0.1:1/acme/policy.git",
                "ssh://127.0.0.1/acme/policy.git");

        Path repository = home.resolve("repo.git");
        for (String location : locations) {
            GitCommand git = new GitCommand(check.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);
            git.run(List.of("init", "--bare", "--template=", repository.toString()), 4096);
            assertThatThrownBy(() -> git.run(
                            List.of(
                                    "--git-dir=" + repository,
                                    "fetch",
                                    "--depth",
                                    "1",
                                    "--no-tags",
                                    "--",
                                    location,
                                    "+refs/heads/main:refs/charter/fetched"),
                            4096))
                    .as(location)
                    .isInstanceOfSatisfying(GitFailure.class, failure -> assertThat(failure.stderr().toLowerCase(java.util.Locale.ROOT))
                            .contains("not allowed"));
        }
        assertThat(marker).doesNotExist();
    }
}
