package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.application.SourceUpstreamException;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The git source from the request to the staged tree (T091, T152, FR-004, NFR-005, NFR-011, NFR-012, IC-05): what it
 * validates before a process starts, the host check and the pin of the checked address, the commands it runs and in what
 * order, how it reads the objects, and how each failure of git is answered. The binary is the fake git of {@link GitShim}
 * replaying canned {@code rev-parse}, {@code ls-tree} and {@code cat-file} output, and the writer of the staged tree is
 * the real bounded stager; no socket is opened. The real git is exercised in {@code GitHostTest}.
 */
class GitSourceFetcherTest {

    private static final String URL = "https://git.example.org/acme/packs.git";
    private static final String REF = "refs/tags/v1.0.0";
    private static final String COMMIT = "a".repeat(40);
    private static final InetAddress GLOBAL = literal("93.184.215.14");
    private static final InetAddress PRIVATE = literal("10.0.0.1");
    private static final int PIN_ARGUMENTS = GitCommand.PINS.size() * 2;

    @TempDir
    Path temp;

    private Path directory;
    private GitShim shim;
    private List<String> lookups;
    private List<InetAddress> answer;
    private final List<String> remoteCalls = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** A file of the repository as the canned {@code ls-tree} and {@code cat-file} output show it. */
    private record Blob(String path, String content, String mode) {
        static Blob file(String path, String content) {
            return new Blob(path, content, "100644");
        }
    }

    @BeforeEach
    void arm() throws IOException {
        directory = Files.createDirectories(temp.resolve("job"));
        shim = GitShim.in(temp);
        lookups = new ArrayList<>();
        answer = List.of(GLOBAL);
        shim.out("rev-parse", COMMIT + "\n");
        tree(Blob.file("pack.yaml", "name: acme\n"), Blob.file("docs/readme.md", "# acme\n"));
    }

    @AfterEach
    void stop() {
        executor.shutdownNow();
    }

    private static InetAddress literal(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String oid(int index) {
        return String.format("%040x", index + 1);
    }

    /** Canned output of {@code ls-tree -r -t -z} and {@code cat-file --batch} for these files, directories first. */
    private void tree(Blob... blobs) throws IOException {
        StringBuilder lsTree = new StringBuilder();
        ByteArrayOutputStream catFile = new ByteArrayOutputStream();
        Set<String> directories = new java.util.LinkedHashSet<>();
        for (Blob blob : blobs) {
            String[] parts = blob.path().split("/", -1);
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                prefix.append(i == 0 ? "" : "/").append(parts[i]);
                if (directories.add(prefix.toString())) {
                    lsTree.append("040000 tree ").append("f".repeat(40)).append('\t').append(prefix).append('\0');
                }
            }
            int index = catFile.size() + lsTree.length();
            lsTree.append(blob.mode())
                    .append(blob.mode().equals("160000") ? " commit " : " blob ")
                    .append(oid(index))
                    .append('\t')
                    .append(blob.path())
                    .append('\0');
            byte[] content = blob.content().getBytes(StandardCharsets.UTF_8);
            catFile.write((oid(index) + " blob " + content.length + "\n").getBytes(StandardCharsets.US_ASCII));
            catFile.write(content);
            catFile.write('\n');
        }
        shim.out("ls-tree", lsTree.toString());
        shim.out("cat-file", catFile.toByteArray());
    }

    private GitSourceFetcher fetcher() {
        return fetcher(Duration.ofSeconds(60), GitSourceFetcher.MAX_BLOB_BYTES + GitSourceFetcher.OBJECT_ALLOWANCE_BYTES, 2);
    }

    private GitSourceFetcher fetcher(Duration timeout, long maxFetchBytes, int concurrent) {
        return fetcher(timeout, maxFetchBytes, concurrent, (repository, address) -> {
            remoteCalls.add(repository.url() + " @ " + address.getHostAddress());
            return GitRemote.https().target(repository, address);
        });
    }

    private GitSourceFetcher fetcher(Duration timeout, long maxFetchBytes, int concurrent, GitRemote remote) {
        GitVersionCheck check = GitVersionCheck.resolve(shim.executable(), null);
        GitHostResolver resolver = host -> {
            lookups.add(host);
            if (answer == null) {
                throw new UnknownHostException(host);
            }
            return answer;
        };
        return new GitSourceFetcher(
                check,
                new GuideAllowlist(Set.of("git.example.org")),
                timeout,
                new BoundedTreeStager(),
                resolver,
                remote,
                maxFetchBytes,
                concurrent);
    }

    private static PackSourceRequest request(String url, String ref, String commit) {
        return new PackSourceRequest(PackSourceKind.GIT, url, ref, commit, null, null);
    }

    private Staged fetch() {
        return fetcher().fetch(request(URL, REF, null), directory);
    }

    private SourceRefusal refusedWith(PackSourceRequest request) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> fetcher().fetch(request, directory));
        assertThat(thrown).isInstanceOf(CharterRefusal.class);
        assertThat(((CharterRefusal) thrown).code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
        for (SourceRefusal reason : SourceRefusal.values()) {
            if (thrown.getMessage().startsWith(reason.wire() + ": ")) {
                return reason;
            }
        }
        throw new AssertionError("not a refusal of the fixed set: " + thrown.getMessage());
    }

    /** The arguments of a call after the fixed pins and the pins a remote added. */
    private static List<String> after(GitShim.Call call, int remotePins) {
        return call.arguments().subList(PIN_ARGUMENTS + remotePins * 2, call.arguments().size());
    }

    /** No git command ran, the version check of building the fetcher aside, and nothing was looked up or left behind. */
    private void assertNothingStarted() {
        assertThat(shim.calls()).allSatisfy(call -> assertThat(call.arguments()).containsExactly("--version"));
        assertThat(lookups).isEmpty();
        assertThat(leftovers()).isEmpty();
    }

    private List<Path> leftovers() {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(path -> path.getFileName().toString().startsWith("git-")).toList();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    @Test
    void aTagIsFetchedResolvedListedAndReadWithTheCommandsOfThePlanInOrder() throws IOException {
        Staged staged = fetch();

        List<GitShim.Call> calls = shim.calls();
        assertThat(calls).hasSize(6);
        // calls.get(0) is the version check; the five that follow are the fetch, each with the remote's pin.
        Path repository = Path.of(after(calls.get(1), 1).get(3));
        assertThat(repository.getFileName().toString()).isEqualTo("repo.git");
        assertThat(repository.getParent().getParent()).isEqualTo(directory);
        assertThat(after(calls.get(1), 1)).containsExactly("init", "--bare", "--template=", repository.toString());
        assertThat(after(calls.get(2), 1))
                .containsExactly(
                        "--git-dir=" + repository,
                        "fetch",
                        "--depth",
                        "1",
                        "--no-tags",
                        "--",
                        URL,
                        "+" + REF + ":refs/charter/fetched");
        assertThat(after(calls.get(3), 1))
                .containsExactly(
                        "--git-dir=" + repository,
                        "rev-parse",
                        "--verify",
                        "--end-of-options",
                        "refs/charter/fetched^{commit}");
        assertThat(after(calls.get(4), 1))
                .containsExactly("--git-dir=" + repository, "ls-tree", "-r", "-t", "-z", "--end-of-options", COMMIT);
        assertThat(after(calls.get(5), 1)).containsExactly("--git-dir=" + repository, "cat-file", "--batch");
        assertThat(staged.pack().directory().resolve("pack.yaml")).hasContent("name: acme\n");
        assertThat(staged.pack().directory().resolve("docs/readme.md")).hasContent("# acme\n");
    }

    @Test
    void theSourceIsRecordedWithTheCommitTheRefResolvedTo() {
        Staged staged = fetch();

        assertThat(staged.source())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("kind", "git", "url", URL, "ref", REF, "commit", COMMIT));
    }

    @Test
    void theAddressIsPinnedFromTheCanonicalUrlNotFromTheUrlAsSent() {
        fetcher().fetch(request("HTTPS://GIT.Example.ORG:443/acme/packs.git", REF, null), directory);

        GitShim.Call fetchCall = shim.calls().get(2);
        assertThat(fetchCall.arguments())
                .containsSequence("-c", "http.curloptResolve=git.example.org:443:93.184.215.14");
        assertThat(after(fetchCall, 1)).containsSequence("--", URL);
        assertThat(remoteCalls).containsExactly(URL + " @ 93.184.215.14");
    }

    @Test
    void theLookupIsOfTheCanonicalHostAndHappensOnceBeforeTheFetch() {
        fetcher().fetch(request("https://GIT.example.org./acme/packs.git", REF, null), directory);

        assertThat(lookups).containsExactly("git.example.org");
    }

    @Test
    void aBranchAndACommitAreFetchedByTheirNameAndTheirId() throws IOException {
        fetcher().fetch(request(URL, "refs/heads/main", null), directory);
        assertThat(after(shim.calls().get(2), 1)).endsWith("+refs/heads/main:refs/charter/fetched");

        fetcher().fetch(request(URL, COMMIT, null), Files.createDirectories(temp.resolve("job-2")));
        List<GitShim.Call> calls = shim.calls();
        assertThat(after(calls.get(calls.size() - 4), 1)).endsWith("+" + COMMIT + ":refs/charter/fetched");
    }

    @Test
    void aHostThatResolvesToAPrivateAddressIsRefusedBeforeAnyProcess() {
        answer = List.of(PRIVATE);

        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.ADDRESS_NOT_GLOBAL);
        assertThat(shim.calls()).hasSize(1);
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void oneNonGlobalAddressAmongGlobalOnesRefusesTheHost() {
        answer = List.of(GLOBAL, literal("169.254.169.254"));

        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.ADDRESS_NOT_GLOBAL);
        assertThat(shim.calls()).hasSize(1);
    }

    @Test
    void aHostThatDoesNotResolveIsAnUpstreamFailureNotARefusalOfTheSource() {
        answer = null;

        assertThatThrownBy(() -> fetcher().fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> {
                    assertThat(failure.timedOut()).isFalse();
                    assertThat(failure.getMessage()).doesNotContain("example");
                });
        assertThat(shim.calls()).hasSize(1);
    }

    @Test
    void aHostThatAnswersNoAddressIsAnUpstreamFailure() {
        answer = List.of();

        assertThatThrownBy(() -> fetcher().fetch(request(URL, REF, null), directory))
                .isInstanceOf(SourceUpstreamException.class);
    }

    @Test
    void aLookupThatOutrunsTheDeadlineIsATimeout() {
        CountDownLatch never = new CountDownLatch(1);
        GitSourceFetcher slow = new GitSourceFetcher(
                GitVersionCheck.resolve(shim.executable(), null),
                new GuideAllowlist(Set.of("git.example.org")),
                Duration.ofMillis(300),
                new BoundedTreeStager(),
                host -> {
                    try {
                        never.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of(GLOBAL);
                },
                GitRemote.https(),
                GitSourceFetcher.MAX_BLOB_BYTES,
                2);

        assertThatThrownBy(() -> slow.fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> assertThat(failure.timedOut()).isTrue());
        never.countDown();
    }

    @Test
    void aHostileUrlOrRefIsRefusedBeforeAnyLookupOrProcess() {
        for (PackSourceRequest hostile : List.of(
                request("https://git.example.org/acme/packs.git?x=1", REF, null),
                request("https://user:s3cr3t@git.example.org/acme/packs.git", REF, null),
                request("http://git.example.org/acme/packs.git", REF, null),
                request("https://elsewhere.example.net/acme/packs.git", REF, null),
                request(URL, "--upload-pack=evil", null),
                request(URL, "refs/heads/../secret", null),
                request(URL, "refs/heads/topic.lock", null),
                request(URL, "refs/heads/a@{1}", null),
                request(URL, REF, "main"))) {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> fetcher().fetch(hostile, directory));
            assertThat(thrown).isInstanceOf(CharterRefusal.class);
            assertThat(thrown.getMessage()).doesNotContain("s3cr3t").doesNotContain("upload-pack");
        }
        assertNothingStarted();
    }

    @Test
    void aRequestThatNamesAMemberAForeignKindOrLacksOneItNeedsIsMalformed() {
        PackSourceRequest[] malformed = {
            new PackSourceRequest(PackSourceKind.GIT, URL, null, null, null, null),
            new PackSourceRequest(PackSourceKind.GIT, null, REF, null, null, null),
            new PackSourceRequest(PackSourceKind.GIT, URL, REF, null, "0".repeat(64), null),
            new PackSourceRequest(PackSourceKind.GIT, URL, REF, null, null, "packs/acme"),
        };

        for (PackSourceRequest request : malformed) {
            assertThat(refusedWith(request)).isEqualTo(SourceRefusal.SOURCE_MALFORMED);
        }
        assertNothingStarted();
    }

    @Test
    void aRequestOfAnotherKindIsNotThisFetchersToServe() {
        PackSourceRequest filesystem =
                new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, null, "packs/acme");

        assertThatThrownBy(() -> fetcher().fetch(filesystem, directory)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCommitThatTheRefResolvedToIsAcceptedAndOneThatItDidNotIsRefused() {
        Staged staged = fetcher().fetch(request(URL, "refs/heads/main", COMMIT), directory);
        assertThat(staged.source()).containsEntry("commit", COMMIT);
        long callsBefore = shim.calls().size();

        assertThat(refusedWith(request(URL, "refs/heads/main", "b".repeat(40)))).isEqualTo(SourceRefusal.COMMIT_MISMATCH);

        // The mismatch is found after rev-parse and before the tree is listed: no ls-tree, no cat-file.
        assertThat(shim.calls().size() - callsBefore).isEqualTo(4);
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void aLinkAModeOrASubmoduleInTheTreeIsRefusedNamingTheEntryAndNothingIsRead() throws IOException {
        for (String mode : List.of("120000", "160000", "100664", "100600", "040755")) {
            tree(Blob.file("pack.yaml", "name: acme\n"), new Blob("evil-entry", "target", mode));
            long callsBefore = shim.calls().size();

            assertThatThrownBy(() -> fetch())
                    .as(mode)
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations()).extracting(v -> v.code()).containsExactly("entry_type");
                        assertThat(refusal.getMessage()).doesNotContain("target");
                    });
            // init, fetch, rev-parse and ls-tree ran; the blobs were never asked for.
            assertThat(shim.calls().size() - callsBefore).isEqualTo(5);
            assertThat(leftovers()).isEmpty();
        }
    }

    @Test
    void aSubmoduleAndALinkAreBothNamedWhenBothArePresent() throws IOException {
        tree(new Blob("link", "x", "120000"), new Blob("sub", "x", "160000"), Blob.file("pack.yaml", "name: a\n"));

        assertThatThrownBy(() -> fetch())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> assertThat(refusal.violations())
                        .extracting(v -> v.code())
                        .containsExactly("entry_type", "entry_type"));
    }

    @Test
    void aPathTheBoundedWriterRefusesIsRefusedWithTheArchiveRulesAndLeavesNothing() throws IOException {
        tree(Blob.file("pack.yaml", "name: acme\n"), Blob.file("../evil.txt", "x"));

        assertThatThrownBy(() -> fetch())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).extracting(v -> v.code()).contains("path_traversal");
                });
        assertThat(leftovers()).isEmpty();
        assertThat(directory.resolve("extract")).isEmptyDirectory();
    }

    @Test
    void aBlobOverTheExpandedLimitIsRefusedWithoutReadingItsBytes() throws IOException {
        tree(Blob.file("pack.yaml", "name: acme\n"));
        shim.out("cat-file", (oid(0) + " blob 99999999999\n").getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> fetch())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> assertThat(refusal.violations())
                        .extracting(v -> v.code())
                        .contains("expanded_too_large"));
    }

    @Test
    void blobsPastTheLimitAfterAnEarlierViolationAreStillStoppedAtTheLimit() throws IOException {
        shim.out(
                "ls-tree",
                "100644 blob " + oid(0) + "\t../evil.txt" + "\0" + "100644 blob " + oid(1) + "\tpack.yaml" + "\0");
        ByteArrayOutputStream cat = new ByteArrayOutputStream();
        cat.write((oid(0) + " blob 1\nx\n").getBytes(StandardCharsets.US_ASCII));
        cat.write((oid(1) + " blob 99999999999\n").getBytes(StandardCharsets.US_ASCII));
        shim.out("cat-file", cat.toByteArray());

        assertThatThrownBy(() -> fetch())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> assertThat(refusal.violations())
                        .extracting(v -> v.code())
                        .contains("expanded_too_large"));
    }

    @Test
    void aMissingObjectOrOneOfAnotherIdIsAnUpstreamFailure() throws IOException {
        shim.out("cat-file", (oid(0) + " missing\n").getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> fetch()).isInstanceOf(SourceUpstreamException.class);

        shim.out("cat-file", (oid(7) + " blob 1\nx\n").getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> fetch()).isInstanceOf(SourceUpstreamException.class);

        shim.out("cat-file", (oid(0) + " blob 5\nab").getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> fetch()).isInstanceOf(SourceUpstreamException.class);
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void aFetchGitFailsAtIsClassifiedByItsStderrAndNeverEchoedBack() {
        shim.exit("fetch", 128).err("fetch", "fatal: couldn't find remote ref refs/tags/v1.0.0\n");
        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.BAD_REF);

        shim.err("fetch", "error: Server does not allow request for unadvertised object " + COMMIT + "\n");
        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.BAD_REF);

        shim.err("fetch", "fatal: unable to access 'https://git.example.org/acme/packs.git/': The requested URL returned error: 301\n");
        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.REDIRECT_REFUSED);

        shim.err("fetch", "fatal: unable to access 'https://git.example.org/acme/packs.git/': Could not resolve host: s3cr3t\n");
        assertThatThrownBy(() -> fetcher().fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> {
                    assertThat(failure.timedOut()).isFalse();
                    assertThat(failure.getMessage()).doesNotContain("s3cr3t").doesNotContain("example");
                });
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void aRefThatDoesNotPeelToACommitIsABadRef() {
        shim.exit("rev-parse", 128).err("rev-parse", "fatal: Needed a single revision\n");

        assertThat(refusedWith(request(URL, REF, null))).isEqualTo(SourceRefusal.BAD_REF);
    }

    @Test
    void anAnswerOfRevParseThatIsNotACommitIdIsAnUpstreamFailure() {
        shim.out("rev-parse", "not-a-commit\n");

        assertThatThrownBy(() -> fetch()).isInstanceOf(SourceUpstreamException.class);
    }

    @Test
    void aFetchThatOutrunsTheDeadlineIsATimeoutAndLeavesNothing() {
        shim.sleep("fetch", 30);
        GitSourceFetcher slow = fetcher(Duration.ofMillis(1500), 1L << 30, 2);

        assertThatThrownBy(() -> slow.fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> assertThat(failure.timedOut()).isTrue());
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void aBlobReadThatOutrunsTheDeadlineIsATimeoutNotAnUpstreamFailure() {
        shim.sleep("cat-file", 30);
        GitSourceFetcher slow = fetcher(Duration.ofMillis(2500), 1L << 30, 2);
        long started = System.nanoTime();

        assertThatThrownBy(() -> slow.fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> assertThat(failure.timedOut()).isTrue());

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void aFetchThatFillsTheDiskPastTheBoundIsKilledAndRefusedAsTooLarge() {
        shim.grow("fetch", 3_000_000);
        GitSourceFetcher small = fetcher(Duration.ofSeconds(60), 1_000_000, 2);

        assertThat(refusedWith(small, request(URL, REF, null))).isEqualTo(SourceRefusal.TOO_LARGE);
        assertThat(leftovers()).isEmpty();
    }

    private SourceRefusal refusedWith(GitSourceFetcher fetcher, PackSourceRequest request) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> fetcher.fetch(request, directory));
        assertThat(thrown).isInstanceOf(CharterRefusal.class);
        return SourceRefusal.valueOf(thrown.getMessage().substring(0, thrown.getMessage().indexOf(':')).toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    void theWorkDirectoryIsOwnerOnlyAndGoneAfterEveryOutcome() throws IOException {
        fetch();
        assertThat(leftovers()).isEmpty();

        // git ran in the fetch's own directory, which was created owner-only.
        assertThat(shim.calls().subList(1, shim.calls().size()))
                .allSatisfy(call -> assertThat(call.cwdMode()).isEqualTo("drwx------"));
        assertThat(shim.calls().get(1).cwd()).startsWith(directory.toRealPath().toString());

        shim.exit("ls-tree", 2);
        assertThatThrownBy(() -> fetch()).isInstanceOf(SourceUpstreamException.class);
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void theEnvironmentGitRunsInHasHomeInTheWorkDirectoryAndNoProxy() {
        fetch();

        GitShim.Call fetchCall = shim.calls().get(2);
        assertThat(fetchCall.environment()).containsEntry("GIT_ALLOW_PROTOCOL", "https");
        assertThat(fetchCall.environment().get("HOME")).isEqualTo(fetchCall.cwd());
        assertThat(fetchCall.environment().keySet()).noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("proxy"));
    }

    @Test
    void atMostTheConfiguredNumberOfFetchesRunAtOnce() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highest = new AtomicInteger();
        CountDownLatch twoArrived = new CountDownLatch(2);
        GitRemote counting = (repository, address) -> {
            int now = inFlight.incrementAndGet();
            highest.accumulateAndGet(now, Math::max);
            twoArrived.countDown();
            try {
                twoArrived.await(5, TimeUnit.SECONDS);
                Thread.sleep(150);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return GitRemote.https().target(repository, address);
        };
        GitSourceFetcher fetcher = fetcher(Duration.ofSeconds(60), 1L << 30, 2, counting);
        List<Future<Staged>> done = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Path job = Files.createDirectories(temp.resolve("job-" + i));
            done.add(executor.submit(() -> fetcher.fetch(request(URL, REF, null), job)));
        }

        for (Future<Staged> one : done) {
            assertThat(one.get(60, TimeUnit.SECONDS).source()).containsEntry("commit", COMMIT);
        }
        assertThat(highest.get()).isEqualTo(2);
    }

    @Test
    void aFetchThatCannotGetASlotBeforeTheDeadlineIsATimeout() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GitRemote blocking = (repository, address) -> {
            holding.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return GitRemote.https().target(repository, address);
        };
        GitSourceFetcher only = fetcher(Duration.ofMillis(800), 1L << 30, 1, blocking);
        Path first = Files.createDirectories(temp.resolve("first"));
        Future<Staged> running = executor.submit(() -> only.fetch(request(URL, REF, null), first));
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> only.fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> assertThat(failure.timedOut()).isTrue());
        release.countDown();
        assertThatThrownBy(() -> running.get(30, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void theSourceIsAvailableOnlyWithAGitOfTheMinimumVersion() {
        assertThat(fetcher().available()).isTrue();
        assertThat(fetcher().kind()).isEqualTo(PackSourceKind.GIT);

        GitShim old = GitShim.in(temp.resolve("old")).version("git version 2.43.0\n");
        GitSourceFetcher unavailable = new GitSourceFetcher(
                GitVersionCheck.resolve(old.executable(), null),
                new GuideAllowlist(Set.of("git.example.org")),
                Duration.ofSeconds(60),
                new BoundedTreeStager(),
                host -> List.of(GLOBAL),
                GitRemote.https());

        assertThat(unavailable.available()).isFalse();
        assertThatThrownBy(() -> unavailable.fetch(request(URL, REF, null), directory))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_UNAVAILABLE));
        assertThat(old.calls()).hasSize(1);
    }
}
