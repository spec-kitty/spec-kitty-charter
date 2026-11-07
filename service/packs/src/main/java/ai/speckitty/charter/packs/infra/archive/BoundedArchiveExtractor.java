package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.InputStream;
import java.nio.file.Path;

/** Red stub: the extractor does not exist yet. */
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
        throw new UnsupportedOperationException("not implemented " + limits);
    }
}
