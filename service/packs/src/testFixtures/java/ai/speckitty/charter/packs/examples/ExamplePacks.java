package ai.speckitty.charter.packs.examples;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The charter-owned example packs every pack-reading test reads (ADR-0070): {@code built-in}; the org packs
 * {@code org-base} and {@code org-alt} that depend on it, siblings that contest one styleguide; the project pack
 * {@code project} that depends on both and settles that contest as their distribution pack (#358); the org pack
 * {@code org-mandate}, which depends on {@code built-in} and on which no pack depends (ADR-0080, #108); and the org
 * pack {@code tensions}, which depends on {@code built-in} and holds one case of each tension (ADR-0045, #602). They
 * carry only
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

    /**
     * The tensions example pack, at org; it depends on {@link #BUILT_IN}, holds one case of each tension between two
     * guidance artifacts and leaves its directives and tactics open (ADR-0045, #602).
     */
    public static final String TENSIONS = "tensions";

    /** The project example pack; it depends on {@link #ORG} and {@link #ORG_ALT}, and is their distribution pack. */
    public static final String PROJECT = "project";

    /**
     * The third org example pack; it depends on {@link #BUILT_IN}, requires its routing catalog by a governing preset,
     * and is in no other example pack's closure (operator ruling of 2026-11-18 on #108, ADR-0080).
     */
    public static final String ORG_MANDATE = "org-mandate";

    private static final String RESOURCE = "/example-packs";
    private static final Map<String, PackLevel> LEVELS = Map.of(
            BUILT_IN, PackLevel.BUILT_IN,
            ORG, PackLevel.ORG,
            ORG_ALT, PackLevel.ORG,
            PROJECT, PackLevel.PROJECT,
            ORG_MANDATE, PackLevel.ORG,
            TENSIONS, PackLevel.ORG);

    private static volatile Path copied;

    private ExamplePacks() {}

    /**
     * The directory that holds the five packs, one sub-directory each.
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
     * @param name {@link #BUILT_IN}, {@link #ORG}, {@link #ORG_ALT}, {@link #PROJECT}, {@link #ORG_MANDATE} or
     *     {@link #TENSIONS}
     * @return its directory
     */
    public static Path pack(String name) {
        if (!LEVELS.containsKey(name)) {
            throw new IllegalArgumentException("no example pack named " + name);
        }
        return packsDirectory().resolve(name);
    }

    /**
     * The six pack names in the compose's stable order (by level, then by name), built-in first, as the store
     * configures them.
     *
     * @return {@link #BUILT_IN}, {@link #ORG_ALT}, {@link #ORG}, {@link #ORG_MANDATE}, {@link #TENSIONS},
     *     {@link #PROJECT}
     */
    public static List<String> names() {
        return List.of(BUILT_IN, ORG_ALT, ORG, ORG_MANDATE, TENSIONS, PROJECT);
    }

    /**
     * The level one example pack is configured at.
     *
     * @param name {@link #BUILT_IN}, {@link #ORG}, {@link #ORG_ALT}, {@link #PROJECT}, {@link #ORG_MANDATE} or
     *     {@link #TENSIONS}
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
