package ai.speckitty.charter.packs.infra.storage;

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
}
