package ai.speckitty.charter.packs.examples;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The charter-owned example packs every pack-reading test reads (ADR-0070): {@code built-in}; the org packs
 * {@code org-base} and {@code org-alt} that depend on it, siblings that contest one styleguide; and the project pack
 * {@code project} that depends on both and settles that contest as their distribution pack (#358). They carry only
 * canonical shapes; an invalid shape belongs to an error-handling fixture, never here.
 *
 * <p>The packs ship as classpath resources, inside a jar when another module reads them. On first use the tree is
 * copied once per JVM to a temporary directory, deleted when the JVM exits, so every caller gets real paths it can walk; a test that changes a
 * file copies the pack again into its own temporary directory.
 */
public final class ExamplePacks {

    /** The built-in example pack. */
    public static final String BUILT_IN = "built-in";

    /** The org example pack; it depends on {@link #BUILT_IN}. */
    public static final String ORG = "org-base";

    /**
     * The second org example pack; it depends on {@link #BUILT_IN} and contests {@code styleguide:reasons-canvas-writing}
     * with {@link #ORG}, a contest {@link #PROJECT} settles (#358).
     */
    public static final String ORG_ALT = "org-alt";

    /** The project example pack; it depends on {@link #ORG} and {@link #ORG_ALT}, and is their distribution pack. */
    public static final String PROJECT = "project";

    private static final String RESOURCE = "/example-packs";
    private static final Map<String, PackLevel> LEVELS = Map.of(
            BUILT_IN, PackLevel.BUILT_IN, ORG, PackLevel.ORG, ORG_ALT, PackLevel.ORG, PROJECT, PackLevel.PROJECT);

    private static volatile Path copied;

    private ExamplePacks() {}

    /**
     * The directory that holds the four packs, one sub-directory each.
     *
     * @return a real directory
     */
    public static Path packsDirectory() {
        Path local = copied;
        if (local == null) {
            synchronized (ExamplePacks.class) {
                local = copied;
                if (local == null) {
                    local = copyOut();
                    copied = local;
                }
            }
        }
        return local;
    }

    /**
     * One example pack's directory.
     *
     * @param name {@link #BUILT_IN}, {@link #ORG}, {@link #ORG_ALT} or {@link #PROJECT}
     * @return its directory
     */
    public static Path pack(String name) {
        if (!LEVELS.containsKey(name)) {
            throw new IllegalArgumentException("no example pack named " + name);
        }
        return packsDirectory().resolve(name);
    }

    /**
     * The four pack names in the compose's stable order, built-in first, as the store configures them.
     *
     * @return {@link #BUILT_IN}, {@link #ORG_ALT}, {@link #ORG}, {@link #PROJECT}
     */
    public static List<String> names() {
        return List.of(BUILT_IN, ORG_ALT, ORG, PROJECT);
    }

    /**
     * The level one example pack is configured at.
     *
     * @param name {@link #BUILT_IN}, {@link #ORG}, {@link #ORG_ALT} or {@link #PROJECT}
     * @return its level
     */
    public static PackLevel level(String name) {
        PackLevel level = LEVELS.get(name);
        if (level == null) {
            throw new IllegalArgumentException("no example pack named " + name);
        }
        return level;
    }

    /**
     * A fresh copy of one pack under {@code target}, for a test that changes files.
     *
     * @param name the pack
     * @param target the directory to copy into; the pack lands at {@code target/<name>}
     * @return the copy
     */
    public static Path copyOf(String name, Path target) {
        Path destination = target.resolve(name);
        copyTree(pack(name), destination);
        return destination;
    }

    private static Path copyOut() {
        return ClasspathTree.copyOut(ExamplePacks.class, RESOURCE, "example-packs");
    }

    private static void copyTree(Path source, Path target) {
        ClasspathTree.copyTree(source, target);
    }
}
