package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The pack directory one extraction builds inside the staging root, and the account of what it holds: the violations
 * found, the bytes written, the paths claimed. Readers call {@link #admit} for every entry, then write only while
 * {@link #clean()}; {@link #finish()} yields the staged pack or the refusal.
 */
final class PackTree {

    static final String DIRECTORY_NAME = "pack";

    private final Path root;
    private final ArchiveLimits limits;
    private final PathLedger ledger = new PathLedger();
    private final List<ArchiveViolation> violations = new ArrayList<>();
    private final Set<String> topLevel = new TreeSet<>();
    private boolean topLevelFile;
    private int omitted;
    private long reserved;
    private long written;
    private long decompressed;

    private PackTree(Path root, ArchiveLimits limits) {
        this.root = root;
        this.limits = limits;
    }

    /** Creates the pack directory inside an existing staging root. */
    static PackTree create(Path stagingRoot, ArchiveLimits limits) {
        if (!ArchiveIo.isDirectory(stagingRoot)) {
            throw new IllegalArgumentException("the staging root is not an existing directory");
        }
        Path root = stagingRoot.resolve(DIRECTORY_NAME);
        ArchiveIo.createDirectory(root);
        return new PackTree(root, limits);
    }

    /** Records a violation; only the first {@value ArchiveRefusal#MAX_LISTED} are kept, the rest are counted. */
    void violation(Code code, String path) {
        if (violations.size() < ArchiveRefusal.MAX_LISTED) {
            violations.add(new ArchiveViolation(code, path));
        } else {
            omitted++;
        }
    }

    /** Whether no violation has been found yet. */
    boolean clean() {
        return violations.isEmpty() && omitted == 0;
    }

    /** Adds to the count of decompressed bytes read. */
    void recordDecompressed(long bytes) {
        decompressed += bytes;
    }

    /** Bytes the archive's files may still add before the expanded limit. */
    long remainingBudget() {
        return limits.maxExpandedBytes() - written;
    }

    /**
     * Checks an entry's name against the path rules and claims it.
     *
     * @return its segments (empty for the archive's own root), or null when a violation was recorded
     */
    @Nullable List<String> admit(String rawName, boolean directory) {
        PathRules.Parsed parsed = PathRules.parse(rawName, directory, limits.maxPathBytes());
        if (parsed.violation() != null) {
            violation(parsed.violation(), rawName);
            return null;
        }
        List<String> segments = parsed.segments();
        if (segments.isEmpty()) {
            return segments;
        }
        if (!ledger.claim(segments, directory)) {
            violation(Code.PATH_COLLISION, rawName);
            return null;
        }
        noteTopLevel(segments, directory);
        return segments;
    }

    private void noteTopLevel(List<String> segments, boolean directory) {
        topLevel.add(segments.get(0));
        topLevelFile |= segments.size() == 1 && !directory;
    }

    /** Reserves a file's declared size against the expanded limit, before any of it is read. */
    void reserve(String entryPath, long declaredSize) throws ArchiveLimitException {
        reserved += Math.max(declaredSize, 0);
        if (reserved > limits.maxExpandedBytes()) {
            throw new ArchiveLimitException(Code.EXPANDED_TOO_LARGE, entryPath);
        }
    }

    /** Creates the directory and its parents, each {@code 0700}. */
    void makeDirectory(List<String> segments) {
        Path current = root;
        for (String segment : segments) {
            current = current.resolve(segment);
            if (!ArchiveIo.isDirectory(current)) {
                ArchiveIo.createDirectory(current);
            }
        }
    }

    /** Writes a file, {@code 0600}, from the stream; returns the bytes written. */
    long writeFile(List<String> segments, InputStream data) throws IOException {
        makeDirectory(segments.subList(0, segments.size() - 1));
        Path target = root;
        for (String segment : segments) {
            target = target.resolve(segment);
        }
        OutputStream out = ArchiveIo.createFile(target);
        try {
            long n = ArchiveIo.copy(data, out);
            written += n;
            return n;
        } finally {
            ArchiveIo.close(out);
        }
    }

    /** The staged pack, or the refusal when any violation was found; the staged files are removed then. */
    StagedPack finish() throws ArchiveRefusal {
        if (!clean()) {
            ArchiveRefusal refusal = new ArchiveRefusal(violations, omitted, decompressed);
            discard();
            throw refusal;
        }
        boolean strip = !topLevelFile && topLevel.size() == 1;
        return new StagedPack(strip ? root.resolve(topLevel.iterator().next()) : root);
    }

    /** Removes everything this extraction wrote. */
    void discard() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
