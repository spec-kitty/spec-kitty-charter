package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * The bounded, path-safe stager of a walked tree (IC-05, NFR-004, NFR-005): it gives the entries a source walked the
 * writer, the path rules, the case-folding collision check and the limits of the archive extractor, so a directory
 * meets exactly the rules an archive does. It writes only regular files ({@code 0600}) and directories ({@code 0700})
 * under {@code <staging>/pack}, and refuses with the violations it found and leaves nothing behind.
 */
public final class BoundedTreeStager implements PackTreeReader {

    private final ArchiveLimits limits;

    /** A stager with the contract's limits. */
    public BoundedTreeStager() {
        this(ArchiveLimits.DEFAULTS);
    }

    /**
     * A stager with the given limits.
     *
     * @param limits the bounds
     */
    public BoundedTreeStager(ArchiveLimits limits) {
        this.limits = limits;
    }

    @Override
    public StagedPack stage(TreeWalk walk, Path stagingRoot) throws ArchiveRefusal, IOException {
        PackTree tree = PackTree.create(stagingRoot, limits);
        try {
            read(walk, tree);
            return tree.finish();
        } catch (ArchiveRefusal | IOException | RuntimeException e) {
            tree.discard();
            throw e;
        }
    }

    private void read(TreeWalk walk, PackTree tree) throws IOException {
        try {
            walk.walk(new TreeSink(tree, limits.maxEntries()));
        } catch (ArchiveLimitException e) {
            tree.violation(e.code(), e.path());
        }
    }

    /** Admits each entry against the rules, and writes it while the tree is clean. */
    private static final class TreeSink implements Sink {

        private final PackTree tree;
        private final int maxEntries;
        private int count;

        TreeSink(PackTree tree, int maxEntries) {
            this.tree = tree;
            this.maxEntries = maxEntries;
        }

        @Override
        public void directory(String path) throws IOException {
            countEntry();
            List<String> segments = tree.admit(path, true);
            if (segments == null || segments.isEmpty() || !tree.clean()) {
                return;
            }
            tree.makeDirectory(segments);
        }

        @Override
        public void file(String path, long size, InputStream data) throws IOException {
            countEntry();
            List<String> segments = tree.admit(path, false);
            if (segments == null || segments.isEmpty() || !tree.clean()) {
                return;
            }
            tree.reserve(path, size);
            try {
                tree.writeFile(segments, new LimitedInputStream(data, size, Code.EXPANDED_TOO_LARGE, true));
            } catch (ArchiveLimitException grown) {
                throw grown.at(path);
            }
        }

        private void countEntry() throws ArchiveLimitException {
            if (++count > maxEntries) {
                throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
            }
        }
    }
}
