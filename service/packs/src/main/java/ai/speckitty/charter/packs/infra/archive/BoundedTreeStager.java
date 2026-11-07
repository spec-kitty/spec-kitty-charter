package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.nio.file.Path;

/** The bounded, path-safe stager of a walked tree (skeleton: signatures only, built next). */
public final class BoundedTreeStager implements PackTreeReader {

    /** A stager with the contract's limits. */
    public BoundedTreeStager() {
        this(ArchiveLimits.DEFAULTS);
    }

    /**
     * A stager with the given limits.
     *
     * @param limits the bounds
     */
    public BoundedTreeStager(ArchiveLimits limits) {}

    @Override
    public StagedPack stage(TreeWalk walk, Path stagingRoot) throws ArchiveRefusal, IOException {
        throw new UnsupportedOperationException("the tree stager is not built yet");
    }
}
