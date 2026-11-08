package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * The bounded, path-safe archive extractor (IC-03, NFR-004, NFR-005). It turns a {@code tar.gz} or a {@code zip} into a
 * pack directory inside the staging root, or refuses it with the violations it found and leaves nothing behind. It
 * reads at most the body limit, at most 32 MiB + 64 KiB of decompressed tar bytes, and at most the entry limit; it
 * writes only regular files ({@code 0600}) and directories ({@code 0700}) under {@code <staging>/pack}.
 */
public final class BoundedArchiveExtractor implements PackArchiveReader {

    private final ArchiveLimits limits;
    private final ZipReader.ZipOpener opener;

    /** An extractor with the contract's limits. */
    public BoundedArchiveExtractor() {
        this(ArchiveLimits.DEFAULTS);
    }

    /**
     * An extractor with the given limits.
     *
     * @param limits the bounds
     */
    public BoundedArchiveExtractor(ArchiveLimits limits) {
        this(limits, ZipReader::openLibrary);
    }

    /** An extractor whose zip opener is replaced, so a test can see when the library is opened and make it fail. */
    BoundedArchiveExtractor(ArchiveLimits limits, ZipReader.ZipOpener opener) {
        this.limits = limits;
        this.opener = opener;
    }

    @Override
    public StagedPack extract(InputStream body, ArchiveFormat format, Path stagingRoot) throws ArchiveRefusal {
        return extractInto(stagingRoot, tree -> {
            if (format == ArchiveFormat.TAR_GZ) {
                new TarGzReader(limits).read(body, tree);
            } else {
                new ZipReader(limits, opener).read(body, tree, stagingRoot);
            }
        });
    }

    /**
     * Reads a zip file where it lies, so the archive is on disk once (#418, A6); a {@code tar.gz} is a stream either way
     * and takes the default.
     */
    @Override
    public StagedPack extract(Path archive, ArchiveFormat format, Path stagingRoot) throws ArchiveRefusal {
        if (format != ArchiveFormat.ZIP) {
            return PackArchiveReader.super.extract(archive, format, stagingRoot);
        }
        return extractInto(stagingRoot, tree -> new ZipReader(limits, opener).readFile(archive, tree));
    }

    /** What reads an archive into a tree. */
    @FunctionalInterface
    private interface Reading {
        void into(PackTree tree) throws IOException;
    }

    private StagedPack extractInto(Path stagingRoot, Reading reading) throws ArchiveRefusal {
        PackTree tree = PackTree.create(stagingRoot, limits);
        try {
            read(reading, tree);
            return tree.finish();
        } catch (ArchiveRefusal | RuntimeException e) {
            tree.discard();
            throw e;
        }
    }

    private void read(Reading reading, PackTree tree) {
        try {
            reading.into(tree);
        } catch (ArchiveLimitException e) {
            tree.violation(e.code(), e.path());
        } catch (IOException e) {
            tree.violation(Code.ARCHIVE_UNREADABLE, "");
        } catch (UncheckedIOException e) {
            throw e; // the host failed (a write), not the archive
        } catch (RuntimeException e) {
            // A library's runtime failure on malformed input (an unchecked parse error) is a refusal, never a 500.
            tree.violation(Code.ARCHIVE_UNREADABLE, "");
        }
    }
}
