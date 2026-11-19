package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;

/**
 * The one read of a pack tree for a test outside this package (#554, WP07): {@link FileSystemPackInspector#readTree}
 * is package-private, as a port's implementation may not return a storage type, so the rule tests reach it here.
 */
public final class TreeReads {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();

    private TreeReads() {}

    /**
     * Reads one pack root once.
     *
     * @param root the pack to read
     * @return the one read
     */
    public static TreeRead of(PackRoot root) {
        return READER.readTree(root);
    }

    /**
     * One read of a pack tree and the readout the domain port gives for that same read (#478, S1): what a test that
     * judges both the storage rules and the readout's composition facts needs, with no second read.
     *
     * @param tree the one read
     * @param readout the readout of {@code tree}'s read, its composition facts included
     */
    public record WithReadout(TreeRead tree, PackReadout readout) {}

    /**
     * Reads one pack root once, keeping the tree read and its readout.
     *
     * @param root the pack to read
     * @return the read and its readout
     */
    public static WithReadout withReadout(PackRoot root) {
        FileSystemPackInspector.OneRead one = READER.readWithStore(root);
        return new WithReadout(one.tree(), FileSystemPackInspector.readout(root, one));
    }
}
