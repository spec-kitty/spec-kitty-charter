package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Every file of a pack with its gate kind: the universe the whole-pack gate accounts for, read from the one read's
 * {@link TreeCensus} (#554, WP13, operator ruling 2026-11-17 "Switch it in WP13"). The census is the one owner of "held"
 * and of how each file stands ({@link CensusEntries}); this class only names each entry the way the gate counts it, and
 * walks and classifies nothing itself.
 *
 * <p>A file that {@code contracts/packs/file-kinds.yaml} maps to a schema takes the schema's name ({@code directive},
 * {@code graph-shard}, {@code pack-descriptor}, {@code agent-profile}, …). Any other file takes
 * {@code other:<top-level directory>}, or {@code other:<file name>} at the pack root, so files without a schema are
 * grouped where they live ({@code other:missions}, {@code other:notes.txt}); the census does not hold such a file, so
 * the gate leaves it out and never fails it (operator ruling R34, #631). A file the table excludes (an
 * {@code exclusions} rule, or the {@code ignored} fallback for Markdown nothing models) takes
 * {@code excluded:<rule's glob>}: it is not pack content, so it is never {@code other:} and never red (#451).
 *
 * <p>A companion file (ADR-0039, #194) takes its owning artifact's gate kind, so it is modelled with that artifact: the
 * gate pairs it with the owner's written companion at the same relative path and compares it in the companion's declared
 * form ({@link #companionsOf}, {@link #formsOf}), never as YAML.
 */
final class WholePackCensus {

    /** The gate kind of a symbolic link ({@link PackRoundTripResult#SYMLINK}). */
    static final String SYMLINK = PackRoundTripResult.SYMLINK;

    /** The prefix of a gate kind for a file no schema covers ({@link PackRoundTripResult#OTHER}). */
    static final String OTHER = PackRoundTripResult.OTHER;

    /**
     * The prefix of a retired file's gate kind ({@link PackRoundTripResult#RETIRED}): a bucket of its own, never folded
     * into {@code other:}, so a stale file is red.
     */
    static final String RETIRED = PackRoundTripResult.RETIRED;

    /**
     * The prefix of an excluded file's gate kind ({@link PackRoundTripResult#EXCLUDED}): the verdict checks that the
     * store did not write it, and it needs no entry in a baseline list.
     */
    static final String EXCLUDED = PackRoundTripResult.EXCLUDED;

    private WholePackCensus() {}

    /**
     * A companion file of the pack and the artifact that names it.
     *
     * @param owner the owning artifact's file, pack relative
     * @param ownerKind the owner's gate kind, its binding's schema name
     * @param declared the companion path exactly as the owner declares it, relative to the owner's file
     * @param form the companion's declared content form
     */
    record CompanionFile(String owner, String ownerKind, String declared, ContentForm form) {
        /**
         * Checks the fields.
         *
         * @param owner the owner's file
         * @param ownerKind the owner's gate kind
         * @param declared the declared path
         * @param form the declared form
         */
        CompanionFile {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(ownerKind, "ownerKind");
            Objects.requireNonNull(declared, "declared");
            Objects.requireNonNull(form, "form");
        }
    }

    /**
     * The census of one pack directory: the one read's, under the pack directory's own name.
     *
     * @param pack the pack directory
     * @return the census
     */
    static TreeCensus census(Path pack) {
        return census(pack, new FileSystemPackInspector());
    }

    /**
     * The census of one pack directory under the reader's bindings and companion rules: the seam a test uses for
     * bindings of its own.
     *
     * @param pack the pack directory
     * @param bindings the bindings whose companions the census lists
     * @param rules the table that classifies companion paths
     * @return the census
     */
    static TreeCensus census(Path pack, List<KindBinding<?, ?>> bindings, Companions.Rules rules) {
        return census(
                pack, new FileSystemPackInspector(rules.table(), new YamlSerializationStrategy(), bindings, rules));
    }

    private static TreeCensus census(Path pack, FileSystemPackInspector reader) {
        Path directory = pack.toAbsolutePath().normalize();
        return reader.readTree(
                        new PackRoot(String.valueOf(directory.getFileName()), Optional.empty(), directory.toString()))
                .census();
    }

    /**
     * Every regular file under {@code pack} with its gate kind, and every symbolic link as kind {@link #SYMLINK}, never
     * followed.
     *
     * @param pack the pack directory
     * @return pack-relative path ({@code /}-separated) to gate kind, sorted by path
     */
    static SortedMap<String, String> of(Path pack) {
        return kinds(census(pack));
    }

    /**
     * Every entry of a census with its gate kind.
     *
     * @param census the census
     * @return pack-relative path to gate kind, sorted by path
     */
    static SortedMap<String, String> kinds(TreeCensus census) {
        SortedMap<String, String> kinds = new TreeMap<>();
        census.entries().forEach(entry -> kinds.put(entry.path(), kindOf(entry)));
        return kinds;
    }

    /**
     * The paths a census does not hold ({@link TreeCensus#notHeld}): what the round trip leaves out (#631).
     *
     * @param census the census
     * @return the pack-relative paths, sorted
     */
    static SortedSet<String> notHeld(TreeCensus census) {
        SortedSet<String> paths = new TreeSet<>();
        census.notHeld().forEach(file -> paths.add(file.path()));
        return paths;
    }

    /**
     * The gate kind of one census entry: the schema name of a mapped file, the owner's for a companion,
     * {@link #RETIRED} and the rule's glob for a retired file, {@link #EXCLUDED} and the rule's glob for an excluded
     * one, {@link #OTHER} and the first path segment for any other file, {@link #SYMLINK} for a link.
     *
     * @param entry the entry
     * @return the gate kind
     */
    static String kindOf(TreeCensus.Entry entry) {
        return switch (entry.standing()) {
            case LINK -> SYMLINK;
            case MAPPED, COMPANION -> entry.name();
            case RETIRED -> RETIRED + entry.name();
            case EXCLUDED -> EXCLUDED + entry.name();
            case UNCOVERED -> OTHER + entry.name();
        };
    }

    /**
     * The gate kind of one file under the bundled table, as the census classifies a file no artifact names as its
     * companion.
     *
     * @param packName the pack directory's name ({@code file-kinds.yaml} globs start at the directory holding packs)
     * @param path the pack-relative path
     * @return the gate kind
     */
    static String kindOf(String packName, String path) {
        return kindOf(packName, path, PackFileKind.bundled());
    }

    /**
     * The gate kind of one file under {@code table}, as the census classifies a file no artifact names as its
     * companion: the seam a test uses before the bundled table has a retired rule.
     *
     * @param packName the pack directory's name
     * @param path the pack-relative path
     * @param table the file-kinds table
     * @return the gate kind
     */
    static String kindOf(String packName, String path, PackFileKind table) {
        return kindOf(CensusEntries.entry(packName, path, table, Map.of()));
    }

    /**
     * The companion files the readable artifacts of the pack name, as the census lists them.
     *
     * @param pack the pack directory
     * @return pack-relative companion path to its owner, sorted by path
     */
    static SortedMap<String, CompanionFile> companionsOf(Path pack) {
        return companionsOf(census(pack));
    }

    /**
     * The companion files a census lists.
     *
     * @param census the census
     * @return pack-relative companion path to its owner, sorted by path
     */
    static SortedMap<String, CompanionFile> companionsOf(TreeCensus census) {
        SortedMap<String, CompanionFile> out = new TreeMap<>();
        for (TreeCensus.Companion companion : census.companions()) {
            out.put(
                    companion.path(),
                    new CompanionFile(
                            companion.owner(),
                            companion.ownerKind(),
                            companion.declared(),
                            ContentForm.fromToken(companion.form())));
        }
        return out;
    }

    /**
     * How the gate compares each file of a pack: a companion in its declared form, any other file as
     * {@link #contentFormOf} gives it.
     *
     * @param pack the pack directory
     * @return the form of a pack-relative path
     */
    static Function<String, ContentForm> formsOf(Path pack) {
        return formsOf(census(pack));
    }

    /**
     * How the gate compares each file a census lists: a companion in its declared form, a mapped file in its rule's form,
     * any other file as YAML.
     *
     * @param census the census
     * @return the form of a pack-relative path
     */
    static Function<String, ContentForm> formsOf(TreeCensus census) {
        Map<String, String> companions = new HashMap<>();
        census.companions().forEach(companion -> companions.put(companion.path(), companion.form()));
        Map<String, TreeCensus.Entry> mapped = new HashMap<>();
        for (TreeCensus.Entry entry : census.entries()) {
            if (entry.standing() == TreeCensus.Standing.MAPPED) {
                mapped.put(entry.path(), entry);
            }
        }
        return path -> {
            String declared = companions.get(path);
            if (declared != null) {
                return ContentForm.fromToken(declared);
            }
            TreeCensus.Entry entry = mapped.get(path);
            return entry == null ? ContentForm.YAML : formOf(entry);
        };
    }

    /**
     * How the gate reads and compares one file (#193): the content form its {@code file-kinds.yaml} rule declares, so a
     * {@link ContentForm#BYTES} file is compared byte for byte and never read as text, and a
     * {@link ContentForm#MARKDOWN} file is compared by its Markdown model and never read as YAML (#194). A file no rule
     * maps keeps today's treatment, {@link ContentForm#YAML}.
     *
     * @param packName the pack directory's name
     * @param path the pack-relative path
     * @return the file's content form
     * @throws IllegalStateException for a form the gate does not compare yet ({@link ContentForm#TEXT}), which the table
     *     refuses anyway: the dispatch is exhaustive, so a newly built form must be decided here
     */
    static ContentForm contentFormOf(String packName, String path) {
        TreeCensus.Entry entry = CensusEntries.entry(packName, path, PackFileKind.bundled(), Map.of());
        return entry.standing() == TreeCensus.Standing.MAPPED ? formOf(entry) : ContentForm.YAML;
    }

    /** The form of a mapped entry, refusing a form the gate does not compare. */
    private static ContentForm formOf(TreeCensus.Entry entry) {
        ContentForm form = ContentForm.fromToken(entry.form().orElseThrow());
        return switch (form) {
            case YAML, MARKDOWN, BYTES -> form;
            case TEXT ->
                throw new IllegalStateException(entry.path() + ": content form '" + ContentForm.TEXT.token()
                        + "' is not built; the gate has no comparison for it");
        };
    }
}
