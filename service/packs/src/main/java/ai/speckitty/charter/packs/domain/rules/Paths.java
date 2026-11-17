package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.packs.domain.PackRelativePaths;
import java.util.Optional;

/**
 * The pack's file names the rules key on, where a finding comes from, and the faults of a pack-relative path that
 * leaves its root, in the one wording every surface shares.
 */
public final class Paths {

    /** The pack's descriptor. */
    public static final String PACK_YAML = "pack.yaml";

    /** The org fragment of a pack. */
    public static final String FRAGMENT = "drg/fragment.yaml";

    private Paths() {}

    /**
     * The fault of a fragment node whose {@code body_path} leaves the pack root as written ({@link PackRelativePaths#leavesRoot},
     * #254): a graph fault of the fragment. Validate mode and the load both refuse the whole fragment with it, in the
     * same words, as a graph document that cannot be read.
     *
     * @param id the node's id
     * @param bodyPath its {@code body_path}, when written
     * @return {@code node <id> body_path '<path>' escapes the pack root}, or empty for a path that stays inside
     */
    public static Optional<String> bodyPathEscape(String id, Optional<String> bodyPath) {
        return bodyPath.filter(PackRelativePaths::leavesRoot)
                .map(path -> "node " + id + " body_path '" + path + "' escapes the pack root");
    }

    /**
     * The reason an asset path leaves {@code assets/}, in the one wording validate mode and the load share.
     *
     * @param path the path as the sidecar wrote it
     * @return {@code asset path '<path>' escapes the pack's assets/ root}
     */
    public static String assetPathEscape(String path) {
        return "asset path '" + path + "' escapes the pack's assets/ root";
    }

    /**
     * Whether a pack-relative path is an org fragment, at the root or nested.
     *
     * @param file the pack-relative path
     * @return whether it is {@value #FRAGMENT}, at the root or under a directory
     */
    public static boolean isFragment(String file) {
        return file.equals(FRAGMENT) || file.endsWith("/" + FRAGMENT);
    }

    /**
     * Where a finding comes from, as its text names it: {@code pack.yaml of <pack>} for the descriptor, else the
     * pack-relative file, never prefixed with the pack's name (#554, R2, squad finding C-11).
     *
     * @param packName the pack's configured name
     * @param file the pack-relative path
     * @return the origin
     */
    public static String origin(String packName, String file) {
        return PACK_YAML.equals(file) ? "pack.yaml of " + packName : file;
    }
}
