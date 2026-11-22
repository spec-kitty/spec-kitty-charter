package ai.speckitty.charter.packs.domain;

/**
 * Which names under a pack domain ({@code <config-root>/packs/}) can be packs: the one rule the service's listing and
 * pack-doctor's {@code --config-root} share (#478, WP04 review MEDIUM-2), so the CLI checks the entries the service would
 * serve. A hidden entry ({@code .git}, a commit's {@code .pack-commit-*} staging directory) is never a pack, and nor is
 * a {@code <name>.pack-old-*} entry, the aside copy a commit leaves beside or in place of its live directory.
 */
public final class PackEntryNames {

    /** The marker of an aside copy's name: {@code <name>.pack-old-<unique>}. */
    public static final String ASIDE_MARKER = ".pack-old-";

    private PackEntryNames() {}

    /**
     * Whether an entry of this name can be a pack: it is not hidden and not an aside copy.
     *
     * @param name the entry's name under the pack domain
     * @return {@code true} when the name can be a pack's
     */
    public static boolean canBeAPack(String name) {
        return !name.startsWith(".") && !name.contains(ASIDE_MARKER);
    }
}
