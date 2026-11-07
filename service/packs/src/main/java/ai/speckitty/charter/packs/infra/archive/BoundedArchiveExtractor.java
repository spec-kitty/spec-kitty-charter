package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * The bounded, path-safe archive extractor (IC-03, NFR-004, NFR-005). It turns a {@code tar.gz} or a {@code zip} into a
 * pack directory inside the staging root, or refuses it with the violations it found and leaves nothing behind. It
 * reads at most the body limit, at most 32 MiB + 64 KiB of decompressed tar bytes, and at most the entry limit; it
 * writes only regular files ({@code 0600}) and directories ({@code 0700}) under {@code <staging>/pack}.
 */
public final class BoundedArchiveExtractor implements PackArchiveReader {

    private final ArchiveLimits limits;

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
        this.limits = limits;
    }

    @Override
    public StagedPack extract(InputStream body, ArchiveFormat format, Path stagingRoot) throws ArchiveRefusal {
        PackTree tree = PackTree.create(stagingRoot, limits);
        try {
            read(body, format, tree, stagingRoot);
            return tree.finish();
        } catch (ArchiveRefusal | RuntimeException e) {
            tree.discard();
            throw e;
        }
    }

    private void read(InputStream body, ArchiveFormat format, PackTree tree, Path stagingRoot) {
        try {
            switch (format) {
                case TAR_GZ -> new TarGzReader(limits).read(body, tree);
                case ZIP -> new ZipReader(limits).read(body, tree, stagingRoot);
            }
        } catch (ArchiveLimitException e) {
            tree.violation(e.code(), e.path());
        } catch (IOException e) {
            tree.violation(Code.ARCHIVE_UNREADABLE, "");
        }
    }
}
