package ai.speckitty.charter.packs.infra.fetch.git;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveStaging;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.PackTreeReader.Sink;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.application.SourceUpstreamException;
import ai.speckitty.charter.packs.application.SourceUrls;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.infra.fetch.AddressPolicy;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The git source (FR-004, IC-05, NFR-005, NFR-011, C-008, plan.md "The system git binary"): it fetches one ref of a public
 * repository over https with the host git and reads its objects, and never checks out a work tree. It runs in the job,
 * outside every lock.
 *
 * <ol>
 *   <li>The URL, the ref and the commit are validated ({@link GitSourceRules}) before anything else: a value outside the
 *       grammar is a {@code pack_source_refused} with a fixed reason, and no lookup or process has happened.
 *   <li>The host is resolved by the service under the deadline and every address must be global unicast
 *       ({@link AddressPolicy}); the first is handed to the {@link GitRemote}, which pins it for git's own connection, so
 *       git connects only where the service checked.
 *   <li>Up to {@value #MAX_CONCURRENT} fetches run at once; one that waits for a slot past the deadline is a timeout.
 *   <li>In an owner-only directory of its own, removed in every outcome, {@link GitCommand} runs {@code init --bare
 *       --template=}, {@code fetch --depth 1 --no-tags} into {@code refs/charter/fetched}, {@code rev-parse
 *       --end-of-options}, {@code ls-tree -r -t -z} and {@code cat-file --batch}, under one deadline and a size watch of
 *       {@value #MAX_BLOB_BYTES} bytes of blobs plus an allowance for the objects.
 *   <li>The tree is read before any blob: only the modes {@code 100644}, {@code 100755} and {@code 040000} are accepted,
 *       and a link, a submodule or any other mode is refused naming the entry. The blobs are written into staging through
 *       the bounded tree writer, so they meet the path rules and limits of an archive (IC-03).
 *   <li>A named commit must be the one the ref resolved to ({@code commit_mismatch}); the resolved commit is recorded as
 *       {@code source.commit}.
 * </ol>
 *
 * <p>Every refusal is of the fixed set and names nothing of the URL, the ref or what git printed (NFR-012); an upstream
 * that failed or did not answer in time is a {@link SourceUpstreamException}. What git wrote to stderr is classified (a ref
 * that does not exist, a redirect) and logged at DEBUG with the URL's path cut.
 */
public final class GitSourceFetcher implements PackSourceFetcher {

    /** The most git fetches that run at once. */
    public static final int MAX_CONCURRENT = 2;

    /** The bytes of blobs a fetch may hold: 32 MiB. */
    public static final long MAX_BLOB_BYTES = 32L * 1024 * 1024;

    /** What git may add to the blobs in the directory of one fetch: its pack, its trees and its commit. */
    public static final long OBJECT_ALLOWANCE_BYTES = 8L * 1024 * 1024;

    private static final System.Logger LOG = System.getLogger(GitSourceFetcher.class.getName());

    private static final int LOOKUP_SLOTS = 8;
    private static final int SMALL_OUTPUT_BYTES = 4096;
    private static final int TREE_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final int HEADER_BYTES = 256;
    private static final long READ_SLACK_BYTES = 64L * 1024;
    private static final int LOGGED_STDERR_CHARS = 1024;
    private static final String FETCHED_REF = "refs/charter/fetched";
    private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final Pattern MISSING_REF =
            Pattern.compile("couldn't find remote ref|unadvertised object|not our ref|does not allow request");
    private static final Pattern REDIRECTED = Pattern.compile("(?i)error: 30[0-9]|HTTP 30[0-9]|redirect");

    private final GitVersionCheck check;
    private final GuideAllowlist allowlist;
    private final Duration timeout;
    private final PackTreeReader reader;
    private final GitHostResolver resolver;
    private final GitRemote remote;
    private final long maxFetchBytes;
    private final Semaphore slots;
    private final Semaphore lookupSlots = new Semaphore(LOOKUP_SLOTS);

    /**
     * A fetcher over the host git.
     *
     * @param check the resolved git; the source is available only when it is enabled
     * @param allowlist the hosts a repository may be fetched from
     * @param timeout the deadline over the whole fetch
     * @param reader the bounded writer of the staged tree
     * @param resolver the name resolution of the host check
     * @param remote where git is sent
     */
    public GitSourceFetcher(
            GitVersionCheck check,
            GuideAllowlist allowlist,
            Duration timeout,
            PackTreeReader reader,
            GitHostResolver resolver,
            GitRemote remote) {
        this(
                check,
                allowlist,
                timeout,
                reader,
                resolver,
                remote,
                MAX_BLOB_BYTES + OBJECT_ALLOWANCE_BYTES,
                MAX_CONCURRENT);
    }

    GitSourceFetcher(
            GitVersionCheck check,
            GuideAllowlist allowlist,
            Duration timeout,
            PackTreeReader reader,
            GitHostResolver resolver,
            GitRemote remote,
            long maxFetchBytes,
            int maxConcurrent) {
        this.check = Objects.requireNonNull(check, "check");
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.remote = Objects.requireNonNull(remote, "remote");
        this.maxFetchBytes = maxFetchBytes;
        this.slots = new Semaphore(maxConcurrent, true);
    }

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.GIT;
    }

    @Override
    public boolean available() {
        return check.enabled();
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        if (!available()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_SOURCE_UNAVAILABLE, "The git source is not available in this service.");
        }
        Named named = requireWellFormed(request);
        long deadline = System.nanoTime() + timeout.toNanos();
        InetAddress address = checkedAddress(named.repository().host(), deadline);
        acquireSlot(deadline);
        try {
            return fetchInOwnDirectory(named, address, directory, deadline);
        } finally {
            slots.release();
        }
    }

    /** What a request that names a git source holds, after the checks that need no process and no lookup. */
    private record Named(
            GitRepository repository, GitRef ref, @Nullable String commit, String url) {}

    private Named requireWellFormed(PackSourceRequest request) {
        if (request.kind() != PackSourceKind.GIT) {
            throw new IllegalArgumentException("not a git source");
        }
        String url = request.url();
        String ref = request.ref();
        if (url == null || ref == null || request.path() != null || request.sha256() != null) {
            throw SourceRefusal.SOURCE_MALFORMED.refusal();
        }
        GitRepository repository = GitSourceRules.repository(url, allowlist);
        return new Named(repository, GitSourceRules.ref(ref), GitSourceRules.commit(request.commit()), url);
    }

    // ---- the host check ----

    /** Resolves the host under the deadline; every address must be global unicast, and the first is the one pinned. */
    private InetAddress checkedAddress(String host, long deadline) {
        if (!lookupSlots.tryAcquire()) {
            throw SourceUpstreamException.failed();
        }
        CompletableFuture<List<InetAddress>> lookup = new CompletableFuture<>();
        Thread thread = Thread.ofVirtual().name("git-fetch-resolve").start(() -> {
            try {
                lookup.complete(resolver.resolve(host));
            } catch (UnknownHostException | RuntimeException failed) {
                lookup.completeExceptionally(failed);
            } finally {
                lookupSlots.release();
            }
        });
        List<InetAddress> addresses = await(lookup, thread, deadline);
        if (addresses.isEmpty()) {
            throw SourceUpstreamException.failed();
        }
        if (!addresses.stream().allMatch(AddressPolicy::isGlobalUnicast)) {
            throw SourceRefusal.ADDRESS_NOT_GLOBAL.refusal();
        }
        return addresses.getFirst();
    }

    private static List<InetAddress> await(CompletableFuture<List<InetAddress>> lookup, Thread thread, long deadline) {
        try {
            return lookup.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException late) {
            thread.interrupt();
            throw SourceUpstreamException.deadline();
        } catch (ExecutionException failed) {
            throw SourceUpstreamException.failed();
        } catch (InterruptedException interrupted) {
            thread.interrupt();
            Thread.currentThread().interrupt();
            throw SourceUpstreamException.failed();
        }
    }

    private void acquireSlot(long deadline) {
        try {
            if (!slots.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw SourceUpstreamException.deadline();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw SourceUpstreamException.failed();
        }
    }

    // ---- the fetch ----

    /** Which command of the fetch a failure belongs to; the stderr of the fetch alone says why a ref is refused. */
    private enum Phase {
        INIT,
        FETCH,
        REV_PARSE,
        LS_TREE,
        CAT_FILE
    }

    private Staged fetchInOwnDirectory(Named named, InetAddress address, Path directory, long deadline) {
        Path work = createWorkDirectory(directory);
        try {
            return fetchInto(named, address, work, directory, deadline);
        } finally {
            removeTree(work);
        }
    }

    private Staged fetchInto(Named named, InetAddress address, Path work, Path directory, long deadline) {
        GitRemote.Target target = remote.target(named.repository(), address);
        Duration left = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
        GitCommand git = new GitCommand(check.executable(), work, left, target.transport())
                .watching(() -> sizeOf(work) > maxFetchBytes);
        Path repository = work.resolve("repo.git");
        call(
                git,
                Phase.INIT,
                named,
                List.of("init", "--bare", "--template=", repository.toString()),
                SMALL_OUTPUT_BYTES);
        call(
                git,
                Phase.FETCH,
                named,
                List.of(
                        gitDir(repository),
                        "fetch",
                        "--depth",
                        "1",
                        "--no-tags",
                        "--",
                        target.location(),
                        "+" + named.ref().value() + ":" + FETCHED_REF),
                SMALL_OUTPUT_BYTES);
        String commit = resolveCommit(git, repository, named);
        StagedPack pack = readTree(git, repository, commit, named, work, directory);
        return new Staged(
                pack,
                Map.of(
                        "kind", kind().wire(),
                        "url", SourceUrls.withoutSecrets(named.url()),
                        "ref", named.ref().value(),
                        "commit", commit));
    }

    private static String gitDir(Path repository) {
        return "--git-dir=" + repository;
    }

    private String resolveCommit(GitCommand git, Path repository, Named named) {
        byte[] output = call(
                git,
                Phase.REV_PARSE,
                named,
                List.of(gitDir(repository), "rev-parse", "--verify", "--end-of-options", FETCHED_REF + "^{commit}"),
                SMALL_OUTPUT_BYTES);
        String commit = new String(output, StandardCharsets.UTF_8).strip();
        if (!COMMIT_ID.matcher(commit).matches()) {
            throw SourceUpstreamException.failed();
        }
        if (named.commit() != null && !named.commit().equals(commit)) {
            throw SourceRefusal.COMMIT_MISMATCH.refusal();
        }
        return commit;
    }

    private StagedPack readTree(
            GitCommand git, Path repository, String commit, Named named, Path work, Path directory) {
        byte[] listing = call(
                git,
                Phase.LS_TREE,
                named,
                List.of(gitDir(repository), "ls-tree", "-r", "-t", "-z", "--end-of-options", commit),
                TREE_OUTPUT_BYTES);
        List<Entry> entries = acceptedEntries(listing);
        Path wanted = work.resolve("oids");
        try {
            Files.write(wanted, blobIds(entries), java.nio.file.StandardOpenOption.CREATE_NEW);
            Path into = OwnerOnly.directories(directory.resolve("extract"));
            return stageBlobs(git, repository, wanted, entries, into, named);
        } catch (IOException unwritable) {
            throw new UncheckedIOException("cannot stage the objects of a git source", unwritable);
        }
    }

    private static byte[] blobIds(List<Entry> entries) {
        StringBuilder ids = new StringBuilder();
        for (Entry entry : entries) {
            if (!entry.directory()) {
                ids.append(entry.oid()).append('\n');
            }
        }
        return ids.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // ---- the tree ----

    /** One entry of {@code ls-tree -r -t -z}. */
    private record Entry(String path, String oid, boolean directory) {}

    /** The entries of the listing in order, or the refusal naming each entry of a mode, a type or a name not accepted. */
    private static List<Entry> acceptedEntries(byte[] listing) {
        List<Entry> entries = new ArrayList<>();
        List<ArchiveViolation> violations = new ArrayList<>();
        int start = 0;
        while (start < listing.length) {
            int end = start;
            while (end < listing.length && listing[end] != 0) {
                end++;
            }
            classify(listing, start, end, entries, violations);
            start = end + 1;
        }
        if (!violations.isEmpty()) {
            throw ArchiveStaging.refusalOf(new ArchiveRefusal(violations, 0, 0));
        }
        return entries;
    }

    private static void classify(
            byte[] listing, int start, int end, List<Entry> entries, List<ArchiveViolation> violations) {
        int firstSpace = indexOf(listing, (byte) ' ', start, end);
        int secondSpace = firstSpace < 0 ? -1 : indexOf(listing, (byte) ' ', firstSpace + 1, end);
        int tab = secondSpace < 0 ? -1 : indexOf(listing, (byte) '\t', secondSpace + 1, end);
        if (tab < 0) {
            throw SourceUpstreamException.failed();
        }
        String mode = ascii(listing, start, firstSpace);
        String type = ascii(listing, firstSpace + 1, secondSpace);
        String oid = ascii(listing, secondSpace + 1, tab);
        byte[] raw = java.util.Arrays.copyOfRange(listing, tab + 1, end);
        String path = decode(raw);
        boolean directory = mode.equals("040000") && type.equals("tree");
        boolean file = (mode.equals("100644") || mode.equals("100755")) && type.equals("blob");
        if ((!directory && !file) || !COMMIT_ID.matcher(oid).matches()) {
            violations.add(new ArchiveViolation(Code.ENTRY_TYPE, path));
        } else if (path.indexOf('\uFFFD') >= 0) {
            violations.add(new ArchiveViolation(Code.PATH_CHARACTER, path));
        } else {
            entries.add(new Entry(path, oid, directory));
        }
    }

    private static int indexOf(byte[] bytes, byte wanted, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] == wanted) {
                return i;
            }
        }
        return -1;
    }

    private static String ascii(byte[] bytes, int from, int to) {
        return new String(bytes, from, to - from, StandardCharsets.US_ASCII);
    }

    /** The path as UTF-8; a name that is not valid UTF-8 has the replacement character where it is not. */
    private static String decode(byte[] raw) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException malformed) {
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    // ---- the blobs ----

    private StagedPack stageBlobs(
            GitCommand git, Path repository, Path wanted, List<Entry> entries, Path into, Named named) {
        try (GitCommand.Running cat = git.stream(List.of(gitDir(repository), "cat-file", "--batch"), wanted)) {
            return readBlobs(cat, entries, into);
        } catch (GitFailure failure) {
            throw translate(failure, Phase.CAT_FILE, named);
        }
    }

    private StagedPack readBlobs(GitCommand.Running cat, List<Entry> entries, Path into) {
        try {
            InputStream blobs = new BufferedInputStream(cat.stdout());
            StagedPack pack = reader.stage(sink -> walk(entries, blobs, sink), into);
            cat.finish();
            return pack;
        } catch (ArchiveRefusal refused) {
            throw ArchiveStaging.refusalOf(refused);
        } catch (BlobsTooLarge tooLarge) {
            throw ArchiveStaging.refusalOf(
                    new ArchiveRefusal(List.of(new ArchiveViolation(Code.EXPANDED_TOO_LARGE, "")), 0, 0));
        } catch (IOException unreadable) {
            // A cut stream is the deadline's or the size watch's when the watch destroyed git; else git's own failure.
            cat.rethrowIfDestroyed();
            throw SourceUpstreamException.failed();
        }
    }

    /** The blobs of a tree held more than a pack may. */
    private static final class BlobsTooLarge extends IOException {
        private static final long serialVersionUID = 1L;

        BlobsTooLarge() {
            super("the blobs of the tree are over the limit");
        }
    }

    /** Hands the tree to the writer in order; each file's bytes are the next block of {@code cat-file --batch}. */
    private static void walk(List<Entry> entries, InputStream blobs, Sink sink) throws IOException {
        long total = 0;
        for (Entry entry : entries) {
            if (entry.directory()) {
                sink.directory(entry.path());
            } else {
                total = file(entry, blobs, sink, total);
            }
        }
    }

    private static long file(Entry entry, InputStream blobs, Sink sink, long total) throws IOException {
        long size = readHeader(blobs, entry.oid());
        long sum = total + size;
        if (sum > MAX_BLOB_BYTES + READ_SLACK_BYTES) {
            throw new BlobsTooLarge();
        }
        BoundedBlob data = new BoundedBlob(blobs, size);
        sink.file(entry.path(), size, data);
        data.skipRest();
        if (blobs.read() != '\n') {
            throw new IOException("a blob is not followed by a newline");
        }
        return sum;
    }

    /** Reads {@code <oid> blob <size>} and returns the size; anything else, a missing object included, is an error. */
    private static long readHeader(InputStream blobs, String oid) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int next = blobs.read(); next != '\n'; next = blobs.read()) {
            if (next < 0 || line.length() >= HEADER_BYTES) {
                throw new EOFException("the blob header is cut or too long");
            }
            line.append((char) next);
        }
        String[] parts = line.toString().split(" ", -1);
        if (parts.length != 3 || !parts[0].equals(oid) || !parts[1].equals("blob")) {
            throw new IOException("git did not answer with the blob asked for");
        }
        try {
            long size = Long.parseLong(parts[2]);
            if (size < 0) {
                throw new IOException("a blob has a negative size");
            }
            return size;
        } catch (NumberFormatException malformed) {
            throw new IOException("a blob has no size", malformed);
        }
    }

    /** Exactly the bytes of one blob; the bytes after it belong to the next block. Never closes the stream under it. */
    private static final class BoundedBlob extends InputStream {

        private final InputStream in;
        private long remaining;

        BoundedBlob(InputStream in, long size) {
            this.in = in;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int next = in.read();
            if (next < 0) {
                throw new EOFException("a blob is cut short");
            }
            remaining--;
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int read = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (read < 0) {
                throw new EOFException("a blob is cut short");
            }
            remaining -= read;
            return read;
        }

        void skipRest() throws IOException {
            in.skipNBytes(remaining);
            remaining = 0;
        }
    }

    // ---- running git, and what its failures are ----

    private byte[] call(GitCommand git, Phase phase, Named named, List<String> arguments, int maxOutput) {
        try {
            return git.run(arguments, maxOutput);
        } catch (GitFailure failure) {
            throw translate(failure, phase, named);
        }
    }

    private RuntimeException translate(GitFailure failure, Phase phase, Named named) {
        logFailure(failure, phase, named);
        return switch (failure.kind()) {
            case DEADLINE -> SourceUpstreamException.deadline();
            case TOO_LARGE, OUTPUT_TOO_LARGE -> SourceRefusal.TOO_LARGE.refusal();
            case EXIT -> exited(failure, phase);
            case START, INTERRUPTED -> SourceUpstreamException.failed();
        };
    }

    /** A ref that does not exist or cannot be fetched is a bad ref and a redirect is refused; any other failure is the upstream's. */
    private static RuntimeException exited(GitFailure failure, Phase phase) {
        if (phase == Phase.REV_PARSE) {
            return SourceRefusal.BAD_REF.refusal();
        }
        if (phase == Phase.FETCH) {
            if (MISSING_REF.matcher(failure.stderr()).find()) {
                return SourceRefusal.BAD_REF.refusal();
            }
            if (REDIRECTED.matcher(failure.stderr()).find()) {
                return SourceRefusal.REDIRECT_REFUSED.refusal();
            }
        }
        return SourceUpstreamException.failed();
    }

    /** The operator's log: by host and command, with the URL's path cut from what git wrote and a cap on its length. */
    private static void logFailure(GitFailure failure, Phase phase, Named named) {
        String host = named.repository().host();
        String text = failure.stderr().replace(named.repository().url(), "https://" + host + "/...");
        String cut = text.length() > LOGGED_STDERR_CHARS ? text.substring(0, LOGGED_STDERR_CHARS) : text;
        LOG.log(
                System.Logger.Level.DEBUG,
                "git {0} of a source on {1} failed ({2}, status {3}): {4}",
                phase.name().toLowerCase(java.util.Locale.ROOT),
                host,
                failure.kind(),
                failure.exitStatus(),
                cut.replace('\n', ' '));
    }

    // ---- the work directory ----

    private static Path createWorkDirectory(Path directory) {
        try {
            return OwnerOnly.temporaryDirectory(directory, "git-");
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot create the work directory of a git source", failure);
        }
    }

    private static long sizeOf(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException vanished) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException | UncheckedIOException unreadable) {
            return 0;
        }
    }

    private static void removeTree(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException | UncheckedIOException stuck) {
            LOG.log(System.Logger.Level.WARNING, "The work directory of a git source could not be removed.");
        }
    }
}
