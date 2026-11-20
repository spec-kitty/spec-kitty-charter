package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * How each entry of one walk of a pack tree stands (#554, WP13, operator ruling 2026-11-17 "Switch it in WP13"): the
 * entries and the companions of the {@link TreeCensus}, the one owner of "held". The one reader builds them from its one
 * walk ({@link FileSystemPackInspector#readTree}), whether or not the pack's descriptor can be read, and the whole-pack
 * gate reads them from the census ({@link WholePackCensus}); nothing else classifies a pack tree for the gate.
 *
 * <p>A file a {@code files} rule maps stands with its schema; a companion a readable artifact names stands with its
 * owner's schema, whatever rule maps it, so the gate models it with its owner (ADR-0039, #194); a retired file with its
 * rule's glob; an excluded one (an {@code exclusions} rule, or the {@code ignored} fallback for Markdown nothing models,
 * #451) with its rule's glob; any other file with its first path segment, or its name at the pack root; and a link,
 * never followed, as a link.
 */
final class CensusEntries {

    private CensusEntries() {}

    /**
     * Every entry of a walk, path-sorted: each regular file that is not itself a link, and each link.
     *
     * @param packName the pack directory's name ({@code file-kinds.yaml} globs start at the directory holding packs)
     * @param walk the walk
     * @param table the file-kinds table
     * @param companions the companions the pack's readable artifacts name, by path
     * @return the entries
     */
    static List<TreeCensus.Entry> of(
            String packName, PackFileSet.Walk walk, PackFileKind table, Map<String, TreeCensus.Companion> companions) {
        SortedMap<String, TreeCensus.Entry> entries = new TreeMap<>();
        for (String file : walk.regular()) {
            entries.put(file, entry(packName, file, table, companions));
        }
        for (String link : walk.links()) {
            entries.put(link, new TreeCensus.Entry(link, TreeCensus.Standing.LINK, "", Optional.empty()));
        }
        return List.copyOf(entries.values());
    }

    /**
     * How one regular file stands.
     *
     * @param packName the pack directory's name
     * @param path the pack-relative path
     * @param table the file-kinds table
     * @param companions the companions the pack's readable artifacts name, by path
     * @return the entry
     */
    static TreeCensus.Entry entry(
            String packName, String path, PackFileKind table, Map<String, TreeCensus.Companion> companions) {
        TreeCensus.Companion companion = companions.get(path);
        if (companion != null) {
            return new TreeCensus.Entry(
                    path, TreeCensus.Standing.COMPANION, companion.ownerKind(), Optional.of(companion.form()));
        }
        return switch (table.classify(packName + "/" + path)) {
            case PackFileKind.Mapped mapped ->
                new TreeCensus.Entry(
                        path,
                        TreeCensus.Standing.MAPPED,
                        mapped.kind(),
                        Optional.of(mapped.contentForm().token()));
            // A case variant is refused by name under its root rule, so it stands in the retired bucket (ADR-0073 d3).
            case PackFileKind.RefusedByName refused ->
                new TreeCensus.Entry(path, TreeCensus.Standing.RETIRED, refused.match(), Optional.empty());
            case PackFileKind.Excluded excluded ->
                new TreeCensus.Entry(path, TreeCensus.Standing.EXCLUDED, excluded.exclusionClass(), Optional.empty());
            case PackFileKind.Unclassified unclassified -> {
                int slash = path.indexOf('/');
                yield new TreeCensus.Entry(
                        path,
                        TreeCensus.Standing.UNCOVERED,
                        slash < 0 ? path : path.substring(0, slash),
                        Optional.empty());
            }
        };
    }

    /**
     * The companion files the readable artifacts of a pack name inside it, as the companion rule resolves them, whether
     * the file exists or not (whether it does is the round trip's to report). Each artifact file of a bound kind with
     * companions is read through its binding; a companion itself is never read here. A companion two artifacts name
     * stands with the first, in binding then path order.
     *
     * @param directory the pack directory, absolute and normalized
     * @param listing the pack's listing, from the one walk
     * @param bindings the bindings whose companions to list
     * @param rules the table that classifies companion paths
     * @param strategy the format the artifacts are read in
     * @return the companions, by pack-relative path, path-sorted
     */
    static SortedMap<String, TreeCensus.Companion> companions(
            Path directory,
            PackFileSet.Listing listing,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules rules,
            SerializationStrategy strategy) {
        SortedMap<String, TreeCensus.Companion> out = new TreeMap<>();
        for (KindBinding<?, ?> binding : bindings) {
            if (binding.hasCompanions()) {
                for (String file : listing.files(binding.kind())) {
                    companions(directory, file, binding, rules, strategy, out);
                }
            }
        }
        return out;
    }

    private static <D> void companions(
            Path directory,
            String file,
            KindBinding<D, ?> binding,
            Companions.Rules rules,
            SerializationStrategy strategy,
            SortedMap<String, TreeCensus.Companion> out) {
        Optional<String> text = FilesystemPackStore.readText(directory.resolve(file));
        if (text.isEmpty() || !(strategy.read(binding, text.get(), file) instanceof FileRead.Read<D> read)) {
            return;
        }
        for (KindBinding.Companion companion : binding.companions(read.value())) {
            if (Companions.resolve(rules, directory, file, companion) instanceof Companions.Inside at) {
                out.putIfAbsent(
                        at.packRelative(),
                        new TreeCensus.Companion(
                                at.packRelative(),
                                file,
                                binding.schemaName(),
                                companion.path(),
                                companion.form().token()));
            }
        }
    }
}
